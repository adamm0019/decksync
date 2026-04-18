package dev.decksync.application;

/**
 * Port that registers this peer on the local network. M7c implements advertise only; M7d will
 * extend this port with a subscribe side backed by a {@code DiscoveredPeers} registry.
 *
 * <p>Lifecycle is explicit rather than tied to Spring annotations so the adapter stays framework
 * free — {@link #start()} is invoked by the wiring layer once the HTTP server is up, and {@link
 * AutoCloseable#close()} releases the mDNS socket on shutdown. Calling {@code start()} twice is a
 * programming error; adapters should throw in that case.
 */
public interface DiscoveryService extends AutoCloseable {

  /** Begins advertising the local peer. Must be called exactly once before close. */
  void start();

  /** Identity being advertised. Stable for the lifetime of the service. */
  PeerIdentity identity();

  /** Stops advertising and releases underlying resources. Idempotent. */
  @Override
  void close();
}
