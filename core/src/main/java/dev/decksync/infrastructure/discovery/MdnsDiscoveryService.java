package dev.decksync.infrastructure.discovery;

import dev.decksync.application.DiscoveredPeer;
import dev.decksync.application.DiscoveredPeers;
import dev.decksync.application.DiscoveryService;
import dev.decksync.application.PeerIdentity;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * jmDNS-backed {@link DiscoveryService} advertising the local peer as {@value #SERVICE_TYPE} and
 * subscribing to the same service type to populate a {@link DiscoveredPeers} registry. The TXT
 * record carries {@link PeerIdentity} fields verbatim so a subscriber can rebuild the record
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
 *
 * <p>Liveness: jmDNS emits {@code serviceRemoved} when a peer's mDNS record expires naturally, but
 * a peer that disappears ungracefully (laptop lid slam, Deck sleep) may never deregister. A
 * scheduled sweeper runs every {@link #SWEEP_INTERVAL} and drops entries older than {@link
 * #DEFAULT_PEER_TTL} — 60s matches the Phase 2 plan's "drop after 60s without a response".
 */
public final class MdnsDiscoveryService implements DiscoveryService {

  static final String SERVICE_TYPE = "_decksync._tcp.local.";
  static final Duration DEFAULT_PEER_TTL = Duration.ofSeconds(60);
  static final Duration SWEEP_INTERVAL = Duration.ofSeconds(15);

  private static final String KEY_PEER_ID = "peerId";
  private static final String KEY_APP_VERSION = "appVersion";
  private static final String KEY_PROTOCOL_VERSION = "protocolVersion";

  private static final Logger log = LoggerFactory.getLogger(MdnsDiscoveryService.class);

  private final PeerIdentity identity;
  private final InetAddress bindAddress;
  private final int port;
  private final DiscoveredPeers discoveredPeers;
  private final boolean advertise;
  private final Duration peerTtl;
  private final Duration sweepInterval;
  private final Clock clock;
  private final JmDnsFactory jmDnsFactory;
  private final SweeperFactory sweeperFactory;

  private JmDNS jmdns;
  private ServiceListener listener;
  private ScheduledExecutorService sweeper;
  private boolean started;
  private boolean closed;

  /** Convenience for the serve daemon: advertises <em>and</em> listens. */
  public MdnsDiscoveryService(
      PeerIdentity identity, InetAddress bindAddress, int port, DiscoveredPeers discoveredPeers) {
    this(identity, bindAddress, port, discoveredPeers, true);
  }

  /**
   * @param advertise {@code true} for the serve daemon (we both announce ourselves and listen for
   *     other peers); {@code false} for the GUI wizard (we only listen, since the wizard process
   *     isn't serving anything HTTP-shaped that peers would want to connect to).
   */
  public MdnsDiscoveryService(
      PeerIdentity identity,
      InetAddress bindAddress,
      int port,
      DiscoveredPeers discoveredPeers,
      boolean advertise) {
    this(
        identity,
        bindAddress,
        port,
        discoveredPeers,
        advertise,
        DEFAULT_PEER_TTL,
        SWEEP_INTERVAL,
        Clock.systemUTC(),
        JmDNS::create,
        MdnsDiscoveryService::defaultSweeper);
  }

  MdnsDiscoveryService(
      PeerIdentity identity,
      InetAddress bindAddress,
      int port,
      DiscoveredPeers discoveredPeers,
      boolean advertise,
      Duration peerTtl,
      Duration sweepInterval,
      Clock clock,
      JmDnsFactory jmDnsFactory,
      SweeperFactory sweeperFactory) {
    if (identity == null) {
      throw new IllegalArgumentException("identity must not be null");
    }
    if (bindAddress == null) {
      throw new IllegalArgumentException("bindAddress must not be null");
    }
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("port must be between 1 and 65535 — got: " + port);
    }
    if (discoveredPeers == null) {
      throw new IllegalArgumentException("discoveredPeers must not be null");
    }
    if (peerTtl == null || peerTtl.isZero() || peerTtl.isNegative()) {
      throw new IllegalArgumentException("peerTtl must be positive — got: " + peerTtl);
    }
    if (sweepInterval == null || sweepInterval.isZero() || sweepInterval.isNegative()) {
      throw new IllegalArgumentException("sweepInterval must be positive — got: " + sweepInterval);
    }
    if (clock == null) {
      throw new IllegalArgumentException("clock must not be null");
    }
    if (jmDnsFactory == null) {
      throw new IllegalArgumentException("jmDnsFactory must not be null");
    }
    if (sweeperFactory == null) {
      throw new IllegalArgumentException("sweeperFactory must not be null");
    }
    this.identity = identity;
    this.bindAddress = bindAddress;
    this.port = port;
    this.discoveredPeers = discoveredPeers;
    this.advertise = advertise;
    this.peerTtl = peerTtl;
    this.sweepInterval = sweepInterval;
    this.clock = clock;
    this.jmDnsFactory = jmDnsFactory;
    this.sweeperFactory = sweeperFactory;
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
      if (advertise) {
        registerSelf();
      }
      subscribe();
      startSweeper();
      started = true;
      log.info(
          "mDNS {} {} as '{}' on {}:{} (peerId={}, protocolVersion={})",
          advertise ? "advertising" : "listening for",
          SERVICE_TYPE,
          identity.peerName(),
          bindAddress.getHostAddress(),
          port,
          identity.peerId(),
          identity.protocolVersion());
    } catch (IOException e) {
      closeQuietly();
      throw new IllegalStateException("Failed to start mDNS discovery", e);
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

  /**
   * Runs a single TTL sweep. Exposed for tests — production code schedules this via the internal
   * executor and shouldn't call it directly.
   */
  void sweepOnce() {
    Instant cutoff = clock.instant().minus(peerTtl);
    int removed = discoveredPeers.evictOlderThan(cutoff);
    if (removed > 0) {
      log.info("mDNS eviction: dropped {} peer(s) idle for > {}", removed, peerTtl);
    }
  }

  private void registerSelf() throws IOException {
    Map<String, String> props = new HashMap<>();
    props.put(KEY_PEER_ID, identity.peerId());
    props.put(KEY_APP_VERSION, identity.appVersion());
    props.put(KEY_PROTOCOL_VERSION, identity.protocolVersion());
    ServiceInfo service =
        ServiceInfo.create(SERVICE_TYPE, identity.peerName(), port, 0, 0, true, props);
    jmdns.registerService(service);
  }

  private void subscribe() {
    listener =
        new ServiceListener() {
          @Override
          public void serviceAdded(ServiceEvent event) {
            // jmDNS will fire serviceResolved once it completes the lookup;
            // requestServiceInfo nudges it along for peers that don't broadcast
            // their TXT proactively.
            jmdns.requestServiceInfo(event.getType(), event.getName(), true);
          }

          @Override
          public void serviceRemoved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();
            if (info == null) {
              return;
            }
            String peerId = info.getPropertyString(KEY_PEER_ID);
            if (peerId != null && !peerId.isBlank()) {
              discoveredPeers.remove(peerId);
              log.info("mDNS peer removed: {} (peerId={})", info.getName(), peerId);
            }
          }

          @Override
          public void serviceResolved(ServiceEvent event) {
            handleResolved(event.getInfo());
          }
        };
    jmdns.addServiceListener(SERVICE_TYPE, listener);
  }

  private void handleResolved(ServiceInfo info) {
    if (info == null) {
      return;
    }
    String peerId = info.getPropertyString(KEY_PEER_ID);
    if (peerId == null || peerId.isBlank()) {
      log.debug("Ignoring mDNS service '{}' without peerId TXT", info.getName());
      return;
    }
    if (peerId.equals(identity.peerId())) {
      // Our own advertisement echoing back — skip.
      return;
    }
    String appVersion = firstNonBlank(info.getPropertyString(KEY_APP_VERSION), "unknown");
    String protocolVersion =
        firstNonBlank(
            info.getPropertyString(KEY_PROTOCOL_VERSION), PeerIdentity.CURRENT_PROTOCOL_VERSION);
    PeerIdentity remote = new PeerIdentity(info.getName(), peerId, appVersion, protocolVersion);
    InetSocketAddress endpoint = pickEndpoint(info);
    if (endpoint == null) {
      log.debug("mDNS service '{}' resolved without a usable address", info.getName());
      return;
    }
    Instant now = clock.instant();
    DiscoveredPeer peer = new DiscoveredPeer(remote, endpoint, now, now);
    discoveredPeers.recordSeen(peer);
    log.info(
        "mDNS peer discovered: {} at {}:{} (peerId={}, appVersion={}, protocolVersion={})",
        remote.peerName(),
        endpoint.getHostString(),
        endpoint.getPort(),
        peerId,
        appVersion,
        protocolVersion);
  }

  private static InetSocketAddress pickEndpoint(ServiceInfo info) {
    Inet4Address[] ipv4 = info.getInet4Addresses();
    if (ipv4 != null) {
      for (Inet4Address addr : ipv4) {
        if (!addr.isLinkLocalAddress() && !addr.isAnyLocalAddress()) {
          return new InetSocketAddress(addr, info.getPort());
        }
      }
      if (ipv4.length > 0) {
        return new InetSocketAddress(ipv4[0], info.getPort());
      }
    }
    InetAddress[] all = info.getInetAddresses();
    if (all != null && all.length > 0) {
      return new InetSocketAddress(all[0], info.getPort());
    }
    String[] urls = info.getHostAddresses();
    if (urls != null && urls.length > 0) {
      return InetSocketAddress.createUnresolved(urls[0], info.getPort());
    }
    return null;
  }

  private static String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private void startSweeper() {
    sweeper = sweeperFactory.create();
    long intervalMs = sweepInterval.toMillis();
    // ErrorProne requires the scheduled future to be captured; we don't need
    // to observe completion because scheduleAtFixedRate loops until the
    // executor is shut down in closeQuietly().
    @SuppressWarnings("FutureReturnValueIgnored")
    var ignored =
        sweeper.scheduleAtFixedRate(
            this::sweepOnceSafely, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
  }

  private void sweepOnceSafely() {
    try {
      sweepOnce();
    } catch (RuntimeException e) {
      // Scheduled tasks that throw silently abort the recurrence. Log and
      // swallow so a single transient failure doesn't disable TTL eviction
      // for the rest of the daemon's lifetime.
      log.warn("mDNS sweep failed: {}", e.toString());
    }
  }

  private void closeQuietly() {
    ScheduledExecutorService currentSweeper = sweeper;
    sweeper = null;
    if (currentSweeper != null) {
      currentSweeper.shutdownNow();
    }
    ServiceListener currentListener = listener;
    listener = null;
    JmDNS handle = jmdns;
    jmdns = null;
    if (handle != null) {
      if (currentListener != null) {
        try {
          handle.removeServiceListener(SERVICE_TYPE, currentListener);
        } catch (RuntimeException e) {
          log.debug("mDNS removeServiceListener raised {}", e.toString());
        }
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
  }

  /** Seam for tests that want to swap the real {@link JmDNS} for an in-memory fake. */
  @FunctionalInterface
  interface JmDnsFactory {
    JmDNS create(InetAddress bindAddress) throws IOException;
  }

  /** Seam for tests that want to run the sweeper synchronously instead of on a real clock. */
  @FunctionalInterface
  interface SweeperFactory {
    ScheduledExecutorService create();
  }

  private static ScheduledExecutorService defaultSweeper() {
    AtomicInteger counter = new AtomicInteger();
    ThreadFactory factory =
        r -> {
          Thread t = new Thread(r, "decksync-mdns-sweeper-" + counter.incrementAndGet());
          t.setDaemon(true);
          return t;
        };
    return Executors.newSingleThreadScheduledExecutor(factory);
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
