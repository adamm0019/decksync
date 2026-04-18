package dev.decksync.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of peers the mDNS listener has heard from. Writes happen on jmDNS listener
 * threads; reads happen on the GUI event loop and the occasional CLI call, so the map has to
 * tolerate concurrent observers without locking the writer. {@link ConcurrentHashMap} keyed by
 * {@link PeerIdentity#peerId()} gives us that for free.
 *
 * <p>Eviction is explicit: {@link #evictOlderThan(Instant)} is driven by the adapter's scheduled
 * sweeper rather than happening lazily on read. That keeps {@link #snapshot()} cheap and
 * deterministic, and it makes the TTL behaviour exercisable from tests with a fake clock instead of
 * a real delay.
 */
public final class DiscoveredPeers {

  private final ConcurrentHashMap<String, DiscoveredPeer> byPeerId = new ConcurrentHashMap<>();

  /** Upserts a peer, preserving {@code firstSeen} when already present. */
  public void recordSeen(DiscoveredPeer seen) {
    if (seen == null) {
      throw new IllegalArgumentException("seen must not be null");
    }
    byPeerId.merge(
        seen.identity().peerId(),
        seen,
        (existing, incoming) ->
            new DiscoveredPeer(
                incoming.identity(),
                incoming.endpoint(),
                existing.firstSeen(),
                incoming.lastSeen()));
  }

  /** Removes a peer by id. No-op if unknown. */
  public void remove(String peerId) {
    if (peerId == null) {
      return;
    }
    byPeerId.remove(peerId);
  }

  /** Drops every peer whose {@code lastSeen} is strictly before {@code cutoff}. */
  public int evictOlderThan(Instant cutoff) {
    if (cutoff == null) {
      throw new IllegalArgumentException("cutoff must not be null");
    }
    int[] removed = {0};
    byPeerId
        .entrySet()
        .removeIf(
            entry -> {
              boolean expired = entry.getValue().lastSeen().isBefore(cutoff);
              if (expired) {
                removed[0]++;
              }
              return expired;
            });
    return removed[0];
  }

  /** Snapshot ordered by peerName for stable GUI rendering. */
  public List<DiscoveredPeer> snapshot() {
    return byPeerId.values().stream()
        .sorted(Comparator.comparing(p -> p.identity().peerName()))
        .toList();
  }

  public Optional<DiscoveredPeer> findByPeerId(String peerId) {
    if (peerId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(byPeerId.get(peerId));
  }

  public int size() {
    return byPeerId.size();
  }
}
