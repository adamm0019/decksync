package dev.decksync.application;

import dev.decksync.domain.GameId;
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Parsed contents of {@code ~/.decksync/config.yml}. All fields are resolved — missing file values
 * have already been replaced with defaults by the loader, so the sync engine and web server can
 * read from a single source without re-doing absent-vs-default bookkeeping.
 *
 * <p>{@code watchedGames} semantics: empty list means "sync every game both peers agree is
 * installed"; a non-empty list means "sync only these, skipping others even if installed on both
 * sides". The CLI's {@code --game} flag always wins regardless of this setting.
 */
public record DeckSyncConfig(URI peerUrl, List<GameId> watchedGames, int port, int retention) {

  public static final URI DEFAULT_PEER_URL = URI.create("http://localhost:47824");
  public static final int DEFAULT_PORT = 47824;
  public static final int DEFAULT_RETENTION = 20;

  public DeckSyncConfig {
    if (peerUrl == null) {
      throw new IllegalArgumentException("peerUrl must not be null");
    }
    if (peerUrl.getHost() == null || peerUrl.getHost().isBlank()) {
      throw new IllegalArgumentException("peer.url must include a host — got: " + peerUrl);
    }
    String scheme = peerUrl.getScheme();
    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new IllegalArgumentException("peer.url scheme must be http or https — got: " + scheme);
    }
    if (watchedGames == null) {
      throw new IllegalArgumentException("watchedGames must not be null");
    }
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("port must be between 1 and 65535 — got: " + port);
    }
    if (retention < 1) {
      throw new IllegalArgumentException("retention must be at least 1 — got: " + retention);
    }
    watchedGames = List.copyOf(watchedGames);
  }

  public static DeckSyncConfig defaults() {
    return new DeckSyncConfig(DEFAULT_PEER_URL, List.of(), DEFAULT_PORT, DEFAULT_RETENTION);
  }

  public Optional<List<GameId>> watchedGamesIfAny() {
    return watchedGames.isEmpty() ? Optional.empty() : Optional.of(watchedGames);
  }
}
