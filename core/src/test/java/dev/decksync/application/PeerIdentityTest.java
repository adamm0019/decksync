package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PeerIdentityTest {

  @Test
  void constructor_rejectsBlankPeerName() {
    assertThatThrownBy(() -> new PeerIdentity("", "id", "0.1.0", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("peerName");
  }

  @Test
  void constructor_rejectsBlankPeerId() {
    assertThatThrownBy(() -> new PeerIdentity("laptop", " ", "0.1.0", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("peerId");
  }

  @Test
  void constructor_rejectsBlankAppVersion() {
    assertThatThrownBy(() -> new PeerIdentity("laptop", "id", "", "1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("appVersion");
  }

  @Test
  void constructor_rejectsBlankProtocolVersion() {
    assertThatThrownBy(() -> new PeerIdentity("laptop", "id", "0.1.0", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("protocolVersion");
  }

  @Test
  void currentProtocolVersion_isOne() {
    assertThat(PeerIdentity.CURRENT_PROTOCOL_VERSION).isEqualTo("1");
  }
}
