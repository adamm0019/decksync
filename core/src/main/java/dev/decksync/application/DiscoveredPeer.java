package dev.decksync.application;

import java.net.InetSocketAddress;
import java.time.Instant;

/**
 * A peer seen on the LAN via mDNS. Wraps {@link PeerIdentity} with the transport coordinates jmDNS
 * resolved (host + port) and liveness timestamps so {@link DiscoveredPeers} can age stale entries
 * out without pinging them directly.
 *
 * <p>{@code endpoint} is what the HTTP client will dial. jmDNS may return multiple addresses for a
 * single service; the adapter picks the first IPv4 that isn't link-local, falling back to whatever
 * came first if none qualify.
 */
public record DiscoveredPeer(
    PeerIdentity identity, InetSocketAddress endpoint, Instant firstSeen, Instant lastSeen) {

  public DiscoveredPeer {
    if (identity == null) {
      throw new IllegalArgumentException("identity must not be null");
    }
    if (endpoint == null) {
      throw new IllegalArgumentException("endpoint must not be null");
    }
    if (firstSeen == null) {
      throw new IllegalArgumentException("firstSeen must not be null");
    }
    if (lastSeen == null) {
      throw new IllegalArgumentException("lastSeen must not be null");
    }
    if (lastSeen.isBefore(firstSeen)) {
      throw new IllegalArgumentException(
          "lastSeen must not precede firstSeen — firstSeen=" + firstSeen + " lastSeen=" + lastSeen);
    }
  }

  public DiscoveredPeer withLastSeen(Instant updated) {
    return new DiscoveredPeer(identity, endpoint, firstSeen, updated);
  }
}
