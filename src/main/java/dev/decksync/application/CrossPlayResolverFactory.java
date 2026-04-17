package dev.decksync.application;

import java.nio.file.Path;

/**
 * Builds a Windows-semantics {@link PlaceholderResolver} rooted at a Proton Wine prefix. Injected
 * only on Linux: the catalog's cross-play branch uses it to translate Windows save-path rules
 * against the per-game prefix when a native Linux rule isn't available. Kept in the application
 * layer (not infrastructure) so {@code GameCatalog} doesn't depend on Proton-specific adapters
 * directly.
 */
@FunctionalInterface
public interface CrossPlayResolverFactory {

  PlaceholderResolver forProtonPrefix(Path prefixUserDir);
}
