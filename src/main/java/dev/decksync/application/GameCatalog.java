package dev.decksync.application;

import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import java.util.Map;

/**
 * Resolves the absolute on-disk save directory for every currently-installed game the daemon can
 * locate. The value side of the map is always an absolute path on the running host. Games that are
 * installed via Steam but have no matching manifest entry, no usable save-path rule, or a Proton
 * prefix that doesn't exist yet are silently dropped from the result.
 */
public interface GameCatalog {

  Map<GameId, AbsolutePath> resolveInstalled();
}
