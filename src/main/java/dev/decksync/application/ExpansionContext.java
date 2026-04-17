package dev.decksync.application;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-game inputs to placeholder expansion. Carries the information that depends on the specific
 * game being resolved — install location, store-specific user id, canonical name — so that {@code
 * PlaceholderResolver} can stay a pure function of ({@link Environment}, {@code ExpansionContext},
 * raw placeholder).
 *
 * @param gameName canonical game name as it appears in the Ludusavi manifest
 * @param storeUserId numeric Steam user id (or equivalent) if known, else empty
 * @param installRoot absolute path of the library root that contains the game (e.g. the Steam
 *     library root)
 * @param installBase absolute path of the game's own installation directory
 */
public record ExpansionContext(
    String gameName, Optional<String> storeUserId, Path installRoot, Path installBase) {

  public ExpansionContext {
    Objects.requireNonNull(gameName, "gameName");
    Objects.requireNonNull(storeUserId, "storeUserId");
    Objects.requireNonNull(installRoot, "installRoot");
    Objects.requireNonNull(installBase, "installBase");
    if (gameName.isBlank()) {
      throw new IllegalArgumentException("gameName must not be blank");
    }
    if (!installRoot.isAbsolute()) {
      throw new IllegalArgumentException("installRoot must be absolute — got: " + installRoot);
    }
    if (!installBase.isAbsolute()) {
      throw new IllegalArgumentException("installBase must be absolute — got: " + installBase);
    }
  }
}
