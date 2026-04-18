package dev.decksync.infrastructure.steam;

import dev.decksync.application.ProtonPrefixResolver;
import dev.decksync.application.SteamLibrary;
import dev.decksync.application.SteamLibraryLocator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Linux adapter for {@link ProtonPrefixResolver}. Walks the libraries reported by {@link
 * SteamLibraryLocator}, picks the one that owns the requested appid, and probes {@code
 * <library>/steamapps/compatdata/<appid>/pfx/drive_c/users/steamuser}. Proton stores the prefix in
 * the same library the game is installed to — not always the primary Steam install — so the
 * library-by-appid lookup matters on multi-drive setups.
 *
 * <p>Returns empty when the appid isn't in any known library, or when the prefix directory doesn't
 * exist yet (never-launched games). The latter is the common case the rest of the pipeline must
 * tolerate: a user can add a game to their sync config before they've launched it once on the Deck.
 */
public final class LinuxProtonPrefixResolver implements ProtonPrefixResolver {

  private final SteamLibraryLocator locator;

  public LinuxProtonPrefixResolver(SteamLibraryLocator locator) {
    this.locator = Objects.requireNonNull(locator, "locator");
  }

  @Override
  public Optional<Path> resolve(long steamAppId) {
    for (SteamLibrary library : locator.locate()) {
      if (!library.appIds().contains(steamAppId)) {
        continue;
      }
      Path prefixUserDir =
          library
              .path()
              .resolve("steamapps")
              .resolve("compatdata")
              .resolve(Long.toString(steamAppId))
              .resolve("pfx")
              .resolve("drive_c")
              .resolve("users")
              .resolve("steamuser");
      if (Files.isDirectory(prefixUserDir)) {
        return Optional.of(prefixUserDir);
      }
    }
    return Optional.empty();
  }
}
