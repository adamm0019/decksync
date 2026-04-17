package dev.decksync.infrastructure.config;

import dev.decksync.application.Overrides;
import dev.decksync.application.PlatformOverride;
import dev.decksync.domain.GameId;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads {@code ~/.decksync/overrides.yml} into an {@link Overrides} object. The file is optional —
 * a missing file yields {@link Overrides#EMPTY}, which is the expected state for a user who hasn't
 * customised any save paths yet. Malformed YAML throws {@link OverridesParseException} rather than
 * silently degrading; we'd rather the daemon fail loud at startup than sync to the wrong folder
 * because a typo dropped the user's override.
 *
 * <p>Keys that look like positive integers map to {@link GameId.SteamAppId}; everything else is
 * attempted as a {@link GameId.Slug}. Entries whose key parses as neither are rejected.
 */
public final class OverridesLoader {

  private static final int CODE_POINT_LIMIT = 1 * 1024 * 1024;

  public Overrides load(Path file) {
    Objects.requireNonNull(file, "file");
    if (!Files.isRegularFile(file)) {
      return Overrides.EMPTY;
    }
    try (InputStream in = Files.newInputStream(file)) {
      return parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read overrides: " + file, e);
    }
  }

  public Overrides parse(Reader reader) {
    Objects.requireNonNull(reader, "reader");
    return parseObject(newYaml().load(reader));
  }

  public Overrides parse(InputStream input) {
    Objects.requireNonNull(input, "input");
    return parseObject(newYaml().load(input));
  }

  public Overrides parse(String text) {
    Objects.requireNonNull(text, "text");
    return parseObject(newYaml().load(text));
  }

  private static Yaml newYaml() {
    LoaderOptions options = new LoaderOptions();
    options.setCodePointLimit(CODE_POINT_LIMIT);
    return new Yaml(new SafeConstructor(options));
  }

  private static Overrides parseObject(Object loaded) {
    if (loaded == null) {
      return Overrides.EMPTY;
    }
    Map<Object, Object> root = asRawMap(loaded);
    if (root == null) {
      throw new OverridesParseException("overrides.yml must be a mapping at the top level");
    }
    Map<GameId, PlatformOverride> out = new LinkedHashMap<>(root.size());
    for (Map.Entry<Object, Object> entry : root.entrySet()) {
      String key = String.valueOf(entry.getKey());
      GameId id = parseGameId(key);
      PlatformOverride override = parseOverride(key, entry.getValue());
      out.put(id, override);
    }
    return new Overrides(out);
  }

  private static GameId parseGameId(String key) {
    if (key == null || key.isBlank()) {
      throw new OverridesParseException("game id key must be a non-blank string");
    }
    try {
      long appid = Long.parseLong(key);
      return new GameId.SteamAppId(appid);
    } catch (NumberFormatException ignored) {
      // fall through to slug
    }
    try {
      return new GameId.Slug(key);
    } catch (IllegalArgumentException e) {
      throw new OverridesParseException("invalid game id '" + key + "': " + e.getMessage());
    }
  }

  private static PlatformOverride parseOverride(String key, Object value) {
    Map<String, Object> entry = asMap(value);
    if (entry == null) {
      throw new OverridesParseException(
          "entry '" + key + "' must be a mapping with windows/linux keys");
    }
    Optional<String> windows = extractPath(key, entry.get("windows"), "windows");
    Optional<String> linux = extractPath(key, entry.get("linux"), "linux");
    if (windows.isEmpty() && linux.isEmpty()) {
      throw new OverridesParseException(
          "entry '" + key + "' must set at least one of windows or linux");
    }
    return new PlatformOverride(windows, linux);
  }

  private static Optional<String> extractPath(String gameKey, Object raw, String platformKey) {
    if (raw == null) {
      return Optional.empty();
    }
    if (!(raw instanceof String s) || s.isBlank()) {
      throw new OverridesParseException(
          "entry '" + gameKey + "." + platformKey + "' must be a non-blank string");
    }
    return Optional.of(s);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> asRawMap(Object value) {
    return value instanceof Map ? (Map<Object, Object>) value : null;
  }

  public static final class OverridesParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OverridesParseException(String message) {
      super(message);
    }
  }
}
