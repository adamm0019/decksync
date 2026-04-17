package dev.decksync.application;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * A single Steam library — a directory that holds installed games alongside Steam's own content.
 * Each Steam install has one or more, and every installed game belongs to exactly one.
 *
 * @param path absolute path to the library root (the directory that contains {@code steamapps/})
 * @param appIds Steam AppIDs of games installed in this library
 */
public record SteamLibrary(Path path, Set<Long> appIds) {

  public SteamLibrary {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(appIds, "appIds");
    if (!path.isAbsolute()) {
      throw new IllegalArgumentException("Steam library path must be absolute — got: " + path);
    }
    appIds = Set.copyOf(appIds);
  }
}
