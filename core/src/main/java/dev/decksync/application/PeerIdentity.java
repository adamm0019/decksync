package dev.decksync.application;

/**
 * Broadcast identity for a DeckSync peer — exactly what the mDNS TXT record carries. Kept framework
 * free and value-typed so both the advertise side ({@link DiscoveryService}) and the M7d subscribe
 * side can share one shape.
 *
 * <p>{@code peerId} is a stable opaque token. In M7 it's a random 48-bit nonce minted once and
 * persisted to {@code ~/.decksync/peer-id}; M8 replaces it with a SHA-256 of the peer's TLS
 * certificate so the six-word fingerprint shown at pairing time anchors to real key material. The
 * on-the-wire shape does not change between M7 and M8 — only the provenance.
 *
 * <p>{@code protocolVersion} is the wire protocol version, not the app version. Two peers with
 * different {@code appVersion}s can still sync as long as their {@code protocolVersion}s match;
 * bumping it signals a breaking change (e.g. M10 tombstones).
 */
public record PeerIdentity(
    String peerName, String peerId, String appVersion, String protocolVersion) {

  public static final String CURRENT_PROTOCOL_VERSION = "1";

  public PeerIdentity {
    if (peerName == null || peerName.isBlank()) {
      throw new IllegalArgumentException("peerName must not be blank");
    }
    if (peerId == null || peerId.isBlank()) {
      throw new IllegalArgumentException("peerId must not be blank");
    }
    if (appVersion == null || appVersion.isBlank()) {
      throw new IllegalArgumentException("appVersion must not be blank");
    }
    if (protocolVersion == null || protocolVersion.isBlank()) {
      throw new IllegalArgumentException("protocolVersion must not be blank");
    }
  }
}
