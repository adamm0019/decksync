package dev.decksync.infrastructure.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.decksync.application.PeerIdentity;
import java.io.IOException;
import java.net.InetAddress;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MdnsDiscoveryServiceTest {

  private static final PeerIdentity IDENTITY =
      new PeerIdentity("adam-laptop", "a1b2c3d4e5f6", "0.1.0", "1");

  @Test
  void start_registersServiceWithPopulatedTxtRecord() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(IDENTITY, InetAddress.getLoopbackAddress(), 47824, bind -> jmdns);

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
  void start_passesBindAddressToFactory() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    InetAddress bind = InetAddress.getByName("192.168.1.42");
    InetAddress[] seen = new InetAddress[1];
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(
            IDENTITY,
            bind,
            47824,
            actual -> {
              seen[0] = actual;
              return jmdns;
            });

    service.start();

    assertThat(seen[0]).isEqualTo(bind);
  }

  @Test
  void start_twiceThrows() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(IDENTITY, InetAddress.getLoopbackAddress(), 47824, bind -> jmdns);
    service.start();

    assertThatThrownBy(service::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already started");
  }

  @Test
  void start_afterCloseThrows() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(IDENTITY, InetAddress.getLoopbackAddress(), 47824, bind -> jmdns);
    service.close();

    assertThatThrownBy(service::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already closed");
  }

  @Test
  void start_whenFactoryFailsWrapsException() {
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(
            IDENTITY,
            InetAddress.getLoopbackAddress(),
            47824,
            bind -> {
              throw new IOException("boom");
            });

    assertThatThrownBy(service::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to start mDNS advertisement");
  }

  @Test
  void close_unregistersAndClosesJmdns() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    doNothing().when(jmdns).unregisterAllServices();
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(IDENTITY, InetAddress.getLoopbackAddress(), 47824, bind -> jmdns);
    service.start();

    service.close();

    verify(jmdns).unregisterAllServices();
    verify(jmdns).close();
  }

  @Test
  void close_isIdempotent() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(IDENTITY, InetAddress.getLoopbackAddress(), 47824, bind -> jmdns);
    service.start();

    service.close();
    service.close();

    verify(jmdns, times(1)).close();
  }

  @Test
  void close_withoutStart_doesNothing() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(IDENTITY, InetAddress.getLoopbackAddress(), 47824, bind -> jmdns);

    service.close();

    verify(jmdns, times(0)).close();
  }

  @Test
  void identity_returnsConstructorValue() {
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(
            IDENTITY, InetAddress.getLoopbackAddress(), 47824, bind -> mock(JmDNS.class));

    assertThat(service.identity()).isEqualTo(IDENTITY);
  }

  @Test
  void constructor_rejectsInvalidPort() {
    assertThatThrownBy(
            () ->
                new MdnsDiscoveryService(
                    IDENTITY, InetAddress.getLoopbackAddress(), 0, bind -> mock(JmDNS.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("port");
  }

  @Test
  void start_propagatesRegisterServiceIOException() throws Exception {
    JmDNS jmdns = mock(JmDNS.class);
    when(jmdns.getName()).thenReturn("test");
    doNothing().when(jmdns).unregisterAllServices();
    org.mockito.Mockito.doThrow(new IOException("nope")).when(jmdns).registerService(any());
    MdnsDiscoveryService service =
        new MdnsDiscoveryService(IDENTITY, InetAddress.getLoopbackAddress(), 47824, bind -> jmdns);

    assertThatThrownBy(service::start)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to start mDNS advertisement");

    verify(jmdns).close();
  }
}
