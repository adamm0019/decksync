package dev.decksync.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parsed view of one game in the Ludusavi manifest. The manifest keys games by display name, so
 * {@link #name} preserves that while {@link #steamAppId} carries the Steam AppID when the manifest
 * provides one.
 *
 * @param name canonical game name as it appears in the manifest
 * @param steamAppId Steam AppID if the manifest lists one, else empty
 * @param installDirs directory names the manifest claims this game installs under (any of them)
 * @param savePaths zero-or-more save-path rules with their platform/store filters
 */
public record ManifestEntry(
    String name, Optional<Long> steamAppId, Set<String> installDirs, List<SavePathRule> savePaths) {

  public ManifestEntry {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(steamAppId, "steamAppId");
    Objects.requireNonNull(installDirs, "installDirs");
    Objects.requireNonNull(savePaths, "savePaths");
    if (name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    installDirs = Set.copyOf(installDirs);
    savePaths = List.copyOf(savePaths);
  }
}
