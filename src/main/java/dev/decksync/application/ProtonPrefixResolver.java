package dev.decksync.application;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the Wine prefix that Proton creates for a given Steam app on Linux. Returning {@link
 * Optional#empty()} means the prefix doesn't exist yet — typically because the game has been
 * installed but never launched, which is a valid state callers must treat as "not yet resolvable"
 * rather than an error. On Windows this port has no implementation; platform-specific wiring
 * decides whether to construct an instance at all.
 */
public interface ProtonPrefixResolver {

  Optional<Path> resolve(long steamAppId);
}
