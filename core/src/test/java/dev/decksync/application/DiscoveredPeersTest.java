package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DiscoveredPeersTest {

  private static final Instant T0 = Instant.parse("2026-04-18T12:00:00Z");

  @Test
  void recordSeen_addsNewPeer() {
    DiscoveredPeers peers = new DiscoveredPeers();
    peers.recordSeen(peer("p1", "deck", T0));

    assertThat(peers.size()).isEqualTo(1);
    assertThat(peers.findByPeerId("p1")).isPresent();
  }

  @Test
  void recordSeen_preservesFirstSeenOnUpdate() {
    DiscoveredPeers peers = new DiscoveredPeers();
    peers.recordSeen(peer("p1", "deck", T0));

    Instant later = T0.plusSeconds(30);
    peers.recordSeen(peer("p1", "deck", later));

    DiscoveredPeer updated = peers.findByPeerId("p1").orElseThrow();
    assertThat(updated.firstSeen()).isEqualTo(T0);
    assertThat(updated.lastSeen()).isEqualTo(later);
  }

  @Test
  void evictOlderThan_removesStaleEntries() {
    DiscoveredPeers peers = new DiscoveredPeers();
    peers.recordSeen(peer("old", "old-peer", T0));
    peers.recordSeen(peer("fresh", "fresh-peer", T0.plusSeconds(90)));

    int removed = peers.evictOlderThan(T0.plusSeconds(60));

    assertThat(removed).isEqualTo(1);
    assertThat(peers.findByPeerId("old")).isEmpty();
    assertThat(peers.findByPeerId("fresh")).isPresent();
  }

  @Test
  void remove_byUnknownIdIsNoOp() {
    DiscoveredPeers peers = new DiscoveredPeers();
    peers.recordSeen(peer("p1", "deck", T0));

    peers.remove("ghost");

    assertThat(peers.size()).isEqualTo(1);
  }

  @Test
  void snapshot_isOrderedByPeerName() {
    DiscoveredPeers peers = new DiscoveredPeers();
    peers.recordSeen(peer("p1", "charlie", T0));
    peers.recordSeen(peer("p2", "alice", T0));
    peers.recordSeen(peer("p3", "bob", T0));

    assertThat(peers.snapshot())
        .extracting(p -> p.identity().peerName())
        .containsExactly("alice", "bob", "charlie");
  }

  @Test
  void recordSeen_rejectsNull() {
    DiscoveredPeers peers = new DiscoveredPeers();
    assertThatThrownBy(() -> peers.recordSeen(null)).isInstanceOf(IllegalArgumentException.class);
  }

  private static DiscoveredPeer peer(String id, String name, Instant seen) {
    return new DiscoveredPeer(
        new PeerIdentity(name, id, "0.1.0", "1"),
        new InetSocketAddress("192.168.1.50", 47824),
        seen,
        seen);
  }
}
