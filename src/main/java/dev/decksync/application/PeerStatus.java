package dev.decksync.application;

import java.time.Duration;

/**
 * Result of a {@link PeerReachability#probe} call. Sealed: the peer answered quickly enough ({@link
 * Reachable}) or it didn't, with a reason string usable in a status table ({@link Unreachable}).
 * Modelled as a sealed type so the status command can exhaustively pattern-match without null
 * checks.
 */
public sealed interface PeerStatus {

  record Reachable(Duration rtt) implements PeerStatus {
    public Reachable {
      if (rtt == null) {
        throw new IllegalArgumentException("rtt must not be null");
      }
    }
  }

  record Unreachable(String reason) implements PeerStatus {
    public Unreachable {
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("reason must not be blank");
      }
    }
  }
}
