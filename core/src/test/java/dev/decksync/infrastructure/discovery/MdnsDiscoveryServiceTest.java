package dev.decksync.infrastructure.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.decksync.application.DiscoveredPeer;
import dev.decksync.application.DiscoveredPeers;
import dev.decksync.application.PeerIdentity;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledExecutorService;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MdnsDiscoveryServiceTest {

  private static final PeerIdentity IDENTITY =
      new PeerIdentity("adam-laptop", "a1b2c3d4e5f6", "0.1.0", "1");

  @Test
  void start_registersServiceWithPopulatedTxtRecord() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    DiscoveredPeers peers = new DiscoveredPeers();
    MdnsDiscoveryService service = serviceWithJmdns(jmdns, peers);

    service.start();

    ArgumentCaptor<ServiceInfo> captor = ArgumentCaptor.forClass(ServiceInfo.class);
    verify(jmdns).registerService(captor.capture());
    ServiceInfo info = captor.getValue();
    assertThat(info.getType()).isEqualTo(MdnsDiscoveryService.SERVICE_TYPE);
    assertThat(info.getName()).isEqualTo("adam-laptop");
    assertThat(info.getPort()).isEqualTo(47824);
    assertThat(info.getPropertyString("peerId")).isEqualTo("a1b2c3d4e5f6");
    assertThat(info.getPropertyString("appVersion")).isEqualTo("0.1.0");
    assertThat(info.getPropertyString("protocolVersion")).isEqualTo("1");
  }

  @Test
  void start_addsServiceListener() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service = serviceWithJmdns(jmdns, new DiscoveredPeers());

    service.start();

    verify(jmdns).addServiceListener(eq(MdnsDiscoveryService.SERVICE_TYPE), any());
  }

  @Test
  void start_whenAdvertiseDisabled_skipsRegisterServiceButStillListens() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service =
        serviceWithSweeper(
            jmdns,
            new DiscoveredPeers(),
            Clock.systemUTC(),
            mock(ScheduledExecutorService.class),
            false);

    service.start();

    verify(jmdns, times(0)).registerService(any());
    verify(jmdns).addServiceListener(eq(MdnsDiscoveryService.SERVICE_TYPE), any());
  }

  @Test
  void serviceResolved_registersRemotePeer() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    DiscoveredPeers peers = new DiscoveredPeers();
    Clock clock = Clock.fixed(Instant.parse("2026-04-18T12:00:00Z"), ZoneOffset.UTC);
    MdnsDiscoveryService service = serviceWithClock(jmdns, peers, clock);

    service.start();
    ServiceListener listener = captureListener(jmdns);

    Inet4Address remoteAddr = (Inet4Address) InetAddress.getByName("192.168.1.50");
    ServiceInfo info =
        fakeServiceInfo("steam-deck", "deadbeef0001", "0.1.0", "1", remoteAddr, 47824);
    listener.serviceResolved(eventFor(info));

    assertThat(peers.size()).isEqualTo(1);
    DiscoveredPeer registered = peers.snapshot().get(0);
    assertThat(registered.identity().peerId()).isEqualTo("deadbeef0001");
    assertThat(registered.identity().peerName()).isEqualTo("steam-deck");
    assertThat(registered.endpoint().getHostString()).isEqualTo("192.168.1.50");
    assertThat(registered.endpoint().getPort()).isEqualTo(47824);
    assertThat(registered.lastSeen()).isEqualTo(clock.instant());
  }

  @Test
  void serviceResolved_ignoresSelf() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    DiscoveredPeers peers = new DiscoveredPeers();
    MdnsDiscoveryService service = serviceWithJmdns(jmdns, peers);

    service.start();
    ServiceListener listener = captureListener(jmdns);

    Inet4Address loopback = (Inet4Address) InetAddress.getLoopbackAddress();
    ServiceInfo info =
        fakeServiceInfo(IDENTITY.peerName(), IDENTITY.peerId(), "0.1.0", "1", loopback, 47824);
    listener.serviceResolved(eventFor(info));

    assertThat(peers.size()).isZero();
  }

  @Test
  void serviceResolved_withoutPeerIdTxtIsIgnored() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    DiscoveredPeers peers = new DiscoveredPeers();
    MdnsDiscoveryService service = serviceWithJmdns(jmdns, peers);

    service.start();
    ServiceListener listener = captureListener(jmdns);

    Inet4Address addr = (Inet4Address) InetAddress.getByName("192.168.1.77");
    ServiceInfo info = fakeServiceInfo("mystery", null, "0.1.0", "1", addr, 47824);
    listener.serviceResolved(eventFor(info));

    assertThat(peers.size()).isZero();
  }

  @Test
  void serviceRemoved_dropsPeerFromRegistry() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    DiscoveredPeers peers = new DiscoveredPeers();
    MdnsDiscoveryService service = serviceWithJmdns(jmdns, peers);

    service.start();
    ServiceListener listener = captureListener(jmdns);

    Inet4Address addr = (Inet4Address) InetAddress.getByName("192.168.1.50");
    ServiceInfo added = fakeServiceInfo("steam-deck", "peer-42", "0.1.0", "1", addr, 47824);
    listener.serviceResolved(eventFor(added));
    assertThat(peers.size()).isEqualTo(1);

    listener.serviceRemoved(eventFor(added));
    assertThat(peers.size()).isZero();
  }

  @Test
  void sweepOnce_evictsEntriesOlderThanTtl() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    DiscoveredPeers peers = new DiscoveredPeers();
    Instant start = Instant.parse("2026-04-18T12:00:00Z");
    MutableClock clock = new MutableClock(start);
    MdnsDiscoveryService service = serviceWithClock(jmdns, peers, clock);

    service.start();
    ServiceListener listener = captureListener(jmdns);

    Inet4Address addr = (Inet4Address) InetAddress.getByName("192.168.1.50");
    ServiceInfo info = fakeServiceInfo("steam-deck", "peer-42", "0.1.0", "1", addr, 47824);
    listener.serviceResolved(eventFor(info));
    assertThat(peers.size()).isEqualTo(1);

    clock.advance(Duration.ofSeconds(90));
    service.sweepOnce();

    assertThat(peers.size()).isZero();
  }

  @Test
  void sweepOnce_keepsFreshEntries() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    DiscoveredPeers peers = new DiscoveredPeers();
    Instant start = Instant.parse("2026-04-18T12:00:00Z");
    MutableClock clock = new MutableClock(start);
    MdnsDiscoveryService service = serviceWithClock(jmdns, peers, clock);

    service.start();
    ServiceListener listener = captureListener(jmdns);

    Inet4Address addr = (Inet4Address) InetAddress.getByName("192.168.1.50");
    ServiceInfo info = fakeServiceInfo("steam-deck", "peer-42", "0.1.0", "1", addr, 47824);
    listener.serviceResolved(eventFor(info));

    clock.advance(Duration.ofSeconds(30));
    service.sweepOnce();

    assertThat(peers.size()).isEqualTo(1);
  }

  @Test
  void start_twiceThrows() {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service = serviceWithJmdns(jmdns, new DiscoveredPeers());
    service.start();

    assertThatThrownBy(service::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already started");
  }

  @Test
  void start_afterCloseThrows() {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service = serviceWithJmdns(jmdns, new DiscoveredPeers());
    service.close();

    assertThatThrownBy(service::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already closed");
  }

  @Test
  void start_whenFactoryFailsWrapsException() {
    ScheduledExecutorService sweeper = mock(ScheduledExecutorService.class);
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(
            IDENTITY,
            InetAddress.getLoopbackAddress(),
            47824,
            new DiscoveredPeers(),
            true,
            MdnsDiscoveryService.DEFAULT_PEER_TTL,
            MdnsDiscoveryService.SWEEP_INTERVAL,
            Clock.systemUTC(),
            bind -> {
              throw new IOException("boom");
            },
            () -> sweeper);

    assertThatThrownBy(service::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to start mDNS discovery");
  }

  @Test
  void close_unregistersAndClosesJmdns() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    ScheduledExecutorService sweeper = mock(ScheduledExecutorService.class);
    doNothing().when(jmdns).unregisterAllServices();
    MdnsDiscoveryService service = serviceWithSweeper(jmdns, new DiscoveredPeers(), sweeper);
    service.start();

    service.close();

    verify(jmdns).removeServiceListener(eq(MdnsDiscoveryService.SERVICE_TYPE), any());
    verify(jmdns).unregisterAllServices();
    verify(jmdns).close();
    verify(sweeper).shutdownNow();
  }

  @Test
  void close_isIdempotent() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service = serviceWithJmdns(jmdns, new DiscoveredPeers());
    service.start();

    service.close();
    service.close();

    verify(jmdns, times(1)).close();
  }

  @Test
  void start_propagatesRegisterServiceIOException() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    when(jmdns.getName()).thenReturn("test");
    doNothing().when(jmdns).unregisterAllServices();
    doThrow(new IOException("nope")).when(jmdns).registerService(any());
    MdnsDiscoveryService service = serviceWithJmdns(jmdns, new DiscoveredPeers());

    assertThatThrownBy(service::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to start mDNS discovery");

    verify(jmdns).close();
  }

  @Test
  void constructor_rejectsInvalidPort() {
    assertThatThrownBy(
            () ->
                new MdnsDiscoveryService(
                    IDENTITY, InetAddress.getLoopbackAddress(), 0, new DiscoveredPeers()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("port");
  }

  @Test
  void constructor_rejectsNullDiscoveredPeers() {
    assertThatThrownBy(
            () -> new MdnsDiscoveryService(IDENTITY, InetAddress.getLoopbackAddress(), 47824, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("discoveredPeers");
  }

  // --- test helpers -------------------------------------------------------

  private MdnsDiscoveryService serviceWithJmdns(JmDNS jmdns, DiscoveredPeers peers) {
    return serviceWithClock(jmdns, peers, Clock.systemUTC());
  }

  private MdnsDiscoveryService serviceWithClock(JmDNS jmdns, DiscoveredPeers peers, Clock clock) {
    return serviceWithSweeper(jmdns, peers, clock, mock(ScheduledExecutorService.class));
  }

  private MdnsDiscoveryService serviceWithSweeper(
      JmDNS jmdns, DiscoveredPeers peers, ScheduledExecutorService sweeper) {
    return serviceWithSweeper(jmdns, peers, Clock.systemUTC(), sweeper);
  }

  private MdnsDiscoveryService serviceWithSweeper(
      JmDNS jmdns, DiscoveredPeers peers, Clock clock, ScheduledExecutorService sweeper) {
    return serviceWithSweeper(jmdns, peers, clock, sweeper, true);
  }

  private MdnsDiscoveryService serviceWithSweeper(
      JmDNS jmdns,
      DiscoveredPeers peers,
      Clock clock,
      ScheduledExecutorService sweeper,
      boolean advertise) {
    return new MdnsDiscoveryService(
        IDENTITY,
        InetAddress.getLoopbackAddress(),
        47824,
        peers,
        advertise,
        MdnsDiscoveryService.DEFAULT_PEER_TTL,
        MdnsDiscoveryService.SWEEP_INTERVAL,
        clock,
        bind -> jmdns,
        () -> sweeper);
  }

  private static ServiceListener captureListener(JmDNS jmdns) {
    ArgumentCaptor<ServiceListener> captor = ArgumentCaptor.forClass(ServiceListener.class);
    verify(jmdns).addServiceListener(eq(MdnsDiscoveryService.SERVICE_TYPE), captor.capture());
    return captor.getValue();
  }

  private static ServiceEvent eventFor(ServiceInfo info) {
    // Pre-compute getters so Mockito doesn't see nested mock interactions
    // inside when(...) — it treats those as unfinished stubbings.
    String type = info.getType();
    String name = info.getName();
    ServiceEvent event = mock(ServiceEvent.class);
    when(event.getInfo()).thenReturn(info);
    when(event.getType()).thenReturn(type);
    when(event.getName()).thenReturn(name);
    return event;
  }

  private static ServiceInfo fakeServiceInfo(
      String name,
      String peerId,
      String appVersion,
      String protocolVersion,
      Inet4Address addr,
      int port) {
    ServiceInfo info = mock(ServiceInfo.class);
    when(info.getType()).thenReturn(MdnsDiscoveryService.SERVICE_TYPE);
    when(info.getName()).thenReturn(name);
    when(info.getPort()).thenReturn(port);
    when(info.getPropertyString("peerId")).thenReturn(peerId);
    when(info.getPropertyString("appVersion")).thenReturn(appVersion);
    when(info.getPropertyString("protocolVersion")).thenReturn(protocolVersion);
    when(info.getInet4Addresses()).thenReturn(new Inet4Address[] {addr});
    when(info.getInetAddresses()).thenReturn(new InetAddress[] {addr});
    when(info.getHostAddresses()).thenReturn(new String[] {addr.getHostAddress()});
    return info;
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
