package dev.decksync.infrastructure.discovery;

import dev.decksync.application.DiscoveryService;
import dev.decksync.application.PeerIdentity;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jmDNS-backed {@link DiscoveryService} advertising the local peer as {@value #SERVICE_TYPE}. The
 * TXT record carries {@link PeerIdentity} fields verbatim so a subscriber can rebuild the record
 * without a second HTTP round-trip.
 *
 * <p>The adapter owns one {@link JmDNS} instance for its lifetime. Creating it early (at Spring
 * context refresh) is cheap, but registering the service triggers a multicast probe that needs the
 * network interface to be up — doing this inside {@link #start()} rather than the constructor means
 * {@link DiscoveryConfiguration} can delay registration until the HTTP server is listening.
 *
 * <p>{@link JmDNS#create(InetAddress)} is given a specific bind address rather than letting jmDNS
 * pick one; on Windows hosts with multiple interfaces (common on Steam Deck docks) the default
 * picks wrong and we silently end up advertising to the loopback segment. The wiring layer resolves
 * the bind address from {@code server.address} and the first non-loopback IPv4 interface.
 */
public final class MdnsDiscoveryService implements DiscoveryService {

  static final String SERVICE_TYPE = "_decksync._tcp.local.";
  private static final String KEY_PEER_ID = "peerId";
  private static final String KEY_APP_VERSION = "appVersion";
  private static final String KEY_PROTOCOL_VERSION = "protocolVersion";

  private static final Logger log = LoggerFactory.getLogger(MdnsDiscoveryService.class);

  private final PeerIdentity identity;
  private final InetAddress bindAddress;
  private final int port;
  private final JmDnsFactory jmDnsFactory;

  private JmDNS jmdns;
  private boolean started;
  private boolean closed;

  public MdnsDiscoveryService(PeerIdentity identity, InetAddress bindAddress, int port) {
    this(identity, bindAddress, port, JmDNS::create);
  }

  MdnsDiscoveryService(
      PeerIdentity identity, InetAddress bindAddress, int port, JmDnsFactory jmDnsFactory) {
    if (identity == null) {
      throw new IllegalArgumentException("identity must not be null");
    }
    if (bindAddress == null) {
      throw new IllegalArgumentException("bindAddress must not be null");
    }
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("port must be between 1 and 65535 — got: " + port);
    }
    if (jmDnsFactory == null) {
      throw new IllegalArgumentException("jmDnsFactory must not be null");
    }
    this.identity = identity;
    this.bindAddress = bindAddress;
    this.port = port;
    this.jmDnsFactory = jmDnsFactory;
  }

  @Override
  public synchronized void start() {
    if (started) {
      throw new IllegalStateException("MdnsDiscoveryService already started");
    }
    if (closed) {
      throw new IllegalStateException("MdnsDiscoveryService already closed");
    }
    try {
      jmdns = jmDnsFactory.create(bindAddress);
      Map<String, String> props = new HashMap<>();
      props.put(KEY_PEER_ID, identity.peerId());
      props.put(KEY_APP_VERSION, identity.appVersion());
      props.put(KEY_PROTOCOL_VERSION, identity.protocolVersion());
      ServiceInfo service =
          ServiceInfo.create(SERVICE_TYPE, identity.peerName(), port, 0, 0, true, props);
      jmdns.registerService(service);
      started = true;
      log.info(
          "mDNS advertising {} as '{}' on {}:{} (peerId={}, protocolVersion={})",
          SERVICE_TYPE,
          identity.peerName(),
          bindAddress.getHostAddress(),
          port,
          identity.peerId(),
          identity.protocolVersion());
    } catch (IOException e) {
      closeQuietly();
      throw new IllegalStateException("Failed to start mDNS advertisement", e);
    }
  }

  @Override
  public PeerIdentity identity() {
    return identity;
  }

  @Override
  public synchronized void close() {
    if (closed) {
      return;
    }
    closed = true;
    closeQuietly();
  }

  private void closeQuietly() {
    JmDNS handle = jmdns;
    jmdns = null;
    if (handle == null) {
      return;
    }
    try {
      handle.unregisterAllServices();
    } catch (RuntimeException e) {
      log.debug("mDNS unregisterAllServices raised {}", e.toString());
    }
    try {
      handle.close();
    } catch (IOException e) {
      log.debug("mDNS close raised {}", e.toString());
    }
  }

  /** Seam for tests that want to swap the real {@link JmDNS} for an in-memory fake. */
  @FunctionalInterface
  interface JmDnsFactory {
    JmDNS create(InetAddress bindAddress) throws IOException;
  }

  /** Convenience: resolves the first non-loopback IPv4 as a fallback bind address. */
  public static InetAddress defaultBindAddress() {
    try {
      return InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      throw new IllegalStateException("Could not resolve localhost for mDNS bind", e);
    }
  }
}
