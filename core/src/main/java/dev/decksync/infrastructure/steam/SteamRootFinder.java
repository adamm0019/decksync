package dev.decksync.infrastructure.steam;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the root directory of the local Steam installation. Implementations are OS-specific
 * (Windows reads the registry; Linux probes known filesystem locations). Returns empty when Steam
 * isn't detected so callers can decide whether to try fallbacks or treat "not installed" as "no
 * libraries".
 */
public interface SteamRootFinder {

  Optional<Path> find();
}
