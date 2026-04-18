package dev.decksync.infrastructure.config;

import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.GameIdParser;
import dev.decksync.domain.GameId;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Loads {@code ~/.decksync/config.yml} into a {@link DeckSyncConfig}. Every field is optional — a
 * missing file yields {@link DeckSyncConfig#defaults()} — but any field that is present must parse
 * cleanly. Malformed YAML or invalid values throw {@link ConfigParseException} so the daemon fails
 * loud at startup rather than drifting to defaults after a config typo.
 *
 * <p>Schema:
 *
 * <pre>
 * peer:
 *   url: http://192.168.1.5:47824
 * games:
 *   - 1245620           # Steam appid (bare number)
 *   - stardew-valley    # slug
 * port: 47824
 * retention: 20
 * </pre>
 */
public final class DeckSyncConfigLoader {

  private static final int CODE_POINT_LIMIT = 1 * 1024 * 1024;

  public DeckSyncConfig load(Path file) {
    Objects.requireNonNull(file, "file");
    if (!Files.isRegularFile(file)) {
      return DeckSyncConfig.defaults();
    }
    try (InputStream in = Files.newInputStream(file)) {
      return parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read config: " + file, e);
    }
  }

  public DeckSyncConfig parse(Reader reader) {
    Objects.requireNonNull(reader, "reader");
    return parseObject(newYaml().load(reader));
  }

  public DeckSyncConfig parse(InputStream input) {
    Objects.requireNonNull(input, "input");
    return parseObject(newYaml().load(input));
  }

  public DeckSyncConfig parse(String text) {
    Objects.requireNonNull(text, "text");
    return parseObject(newYaml().load(text));
  }

  private static Yaml newYaml() {
    LoaderOptions options = new LoaderOptions();
    options.setCodePointLimit(CODE_POINT_LIMIT);
    return new Yaml(new SafeConstructor(options));
  }

  @SuppressWarnings("unchecked")
  private static DeckSyncConfig parseObject(Object loaded) {
    if (loaded == null) {
      return DeckSyncConfig.defaults();
    }
    if (!(loaded instanceof Map)) {
      throw new ConfigParseException("config.yml must be a mapping at the top level");
    }
    Map<String, Object> root = (Map<String, Object>) loaded;

    URI peerUrl = parsePeerUrl(root.get("peer"));
    List<GameId> games = parseGames(root.get("games"));
    int port = parseInt(root.get("port"), DeckSyncConfig.DEFAULT_PORT, "port");
    int retention = parseInt(root.get("retention"), DeckSyncConfig.DEFAULT_RETENTION, "retention");

    try {
      return new DeckSyncConfig(peerUrl, games, port, retention);
    } catch (IllegalArgumentException e) {
      throw new ConfigParseException(e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private static URI parsePeerUrl(Object raw) {
    if (raw == null) {
      return DeckSyncConfig.DEFAULT_PEER_URL;
    }
    if (!(raw instanceof Map)) {
      throw new ConfigParseException("peer must be a mapping with a 'url' key");
    }
    Object url = ((Map<String, Object>) raw).get("url");
    if (url == null) {
      return DeckSyncConfig.DEFAULT_PEER_URL;
    }
    if (!(url instanceof String s) || s.isBlank()) {
      throw new ConfigParseException("peer.url must be a non-blank string");
    }
    try {
      return new URI(s);
    } catch (URISyntaxException e) {
      throw new ConfigParseException("peer.url is not a valid URI: " + s);
    }
  }

  private static List<GameId> parseGames(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      throw new ConfigParseException("games must be a list");
    }
    List<GameId> out = new ArrayList<>(list.size());
    for (Object item : list) {
      if (item == null) {
        throw new ConfigParseException("games entries must not be null");
      }
      String text = String.valueOf(item);
      try {
        out.add(GameIdParser.parse(text));
      } catch (IllegalArgumentException e) {
        throw new ConfigParseException("invalid game id '" + text + "': " + e.getMessage());
      }
    }
    return out;
  }

  private static int parseInt(Object raw, int fallback, String field) {
    if (raw == null) {
      return fallback;
    }
    if (raw instanceof Integer i) {
      return i;
    }
    if (raw instanceof Long l) {
      return Math.toIntExact(l);
    }
    if (raw instanceof String s) {
      try {
        return Integer.parseInt(s.trim());
      } catch (NumberFormatException e) {
        throw new ConfigParseException(field + " must be an integer — got: " + s);
      }
    }
    throw new ConfigParseException(field + " must be an integer — got: " + raw);
  }

  public static final class ConfigParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConfigParseException(String message) {
      super(message);
    }
  }
}
