package dev.decksync.infrastructure.manifest;

import dev.decksync.application.ManifestEntry;
import dev.decksync.application.SavePathRule;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * SnakeYAML-based parser for the Ludusavi manifest. Emits one {@link ManifestEntry} per game,
 * carrying only the fields DeckSync acts on today — name, Steam AppID, install dir names, and save
 * path rules. Other manifest fields (registry, launch, gog, cloud, ...) are ignored until a
 * downstream feature needs them.
 */
public final class LudusaviManifestParser {

  /** Upper bound on parsed bytes. The upstream manifest is around 10 MB and grows slowly. */
  private static final int CODE_POINT_LIMIT = 64 * 1024 * 1024;

  /** Parses a manifest document into a list of entries in document order. */
  public List<ManifestEntry> parse(InputStream input) {
    Objects.requireNonNull(input, "input");
    LoaderOptions options = new LoaderOptions();
    options.setCodePointLimit(CODE_POINT_LIMIT);
    Yaml yaml = new Yaml(new SafeConstructor(options));
    Map<String, Object> root = yaml.load(input);
    if (root == null) {
      return List.of();
    }
    List<ManifestEntry> entries = new ArrayList<>(root.size());
    for (Map.Entry<String, Object> e : root.entrySet()) {
      entries.add(parseEntry(e.getKey(), asMap(e.getValue())));
    }
    return Collections.unmodifiableList(entries);
  }

  private static ManifestEntry parseEntry(String name, Map<String, Object> game) {
    Optional<Long> steamAppId = parseSteamAppId(game);
    Set<String> installDirs = parseInstallDirs(game);
    List<SavePathRule> savePaths = parseSavePaths(game);
    return new ManifestEntry(name, steamAppId, installDirs, savePaths);
  }

  private static Optional<Long> parseSteamAppId(Map<String, Object> game) {
    Map<String, Object> steam = asMap(game.get("steam"));
    if (steam == null) {
      return Optional.empty();
    }
    Object id = steam.get("id");
    if (id instanceof Number n) {
      return Optional.of(n.longValue());
    }
    return Optional.empty();
  }

  private static Set<String> parseInstallDirs(Map<String, Object> game) {
    Map<String, Object> installDir = asMap(game.get("installDir"));
    if (installDir == null) {
      return Set.of();
    }
    return new LinkedHashSet<>(installDir.keySet());
  }

  private static List<SavePathRule> parseSavePaths(Map<String, Object> game) {
    Map<String, Object> files = asMap(game.get("files"));
    if (files == null) {
      return List.of();
    }
    List<SavePathRule> rules = new ArrayList<>(files.size());
    for (Map.Entry<String, Object> file : files.entrySet()) {
      rules.add(parseSavePathRule(file.getKey(), asMap(file.getValue())));
    }
    return rules;
  }

  private static SavePathRule parseSavePathRule(String template, Map<String, Object> meta) {
    Set<String> tags = parseTags(meta);
    List<SavePathRule.WhenCondition> when = parseWhen(meta);
    return new SavePathRule(template, tags, when);
  }

  private static Set<String> parseTags(Map<String, Object> meta) {
    if (meta == null) {
      return Set.of();
    }
    List<Object> tags = asList(meta.get("tags"));
    if (tags == null) {
      return Set.of();
    }
    Set<String> out = new LinkedHashSet<>(tags.size());
    for (Object t : tags) {
      if (t instanceof String s) {
        out.add(s);
      }
    }
    return out;
  }

  private static List<SavePathRule.WhenCondition> parseWhen(Map<String, Object> meta) {
    if (meta == null) {
      return List.of();
    }
    List<Object> whenList = asList(meta.get("when"));
    if (whenList == null) {
      return List.of();
    }
    List<SavePathRule.WhenCondition> out = new ArrayList<>(whenList.size());
    for (Object w : whenList) {
      Map<String, Object> clause = asMap(w);
      if (clause == null) {
        continue;
      }
      Optional<String> os = asOptionalString(clause.get("os"));
      Optional<String> store = asOptionalString(clause.get("store"));
      out.add(new SavePathRule.WhenCondition(os, store));
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : null;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asList(Object value) {
    return value instanceof List ? (List<Object>) value : null;
  }

  private static Optional<String> asOptionalString(Object value) {
    return value instanceof String s ? Optional.of(s) : Optional.empty();
  }
}
