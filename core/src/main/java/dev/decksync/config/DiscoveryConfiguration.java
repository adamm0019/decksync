package dev.decksync.config;

import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.DiscoveredPeers;
import dev.decksync.application.DiscoveryService;
import dev.decksync.application.Environment;
import dev.decksync.application.PeerIdentity;
import dev.decksync.infrastructure.discovery.MdnsDiscoveryService;
import dev.decksync.infrastructure.discovery.PeerIdStore;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires mDNS advertisement for the {@code serve} subcommand. Guarded by {@link
 * ConditionalOnWebApplication} so one-shot CLI commands ({@code scan}, {@code list-games}, {@code
 * sync}) never open a multicast socket — they boot a non-web Spring context that skips this
 * configuration entirely.
 *
 * <p>The {@link DiscoveryService} bean is registered with {@code initMethod = "start"} so the
 * announce happens as the context finishes refreshing, after Tomcat is listening. Shutdown flows
 * through {@link AutoCloseable#close()} automatically — Spring picks it up without a {@code
 * destroyMethod} hint because the bean type implements {@code AutoCloseable}.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DiscoveryConfiguration {

  private static final Logger log = LoggerFactory.getLogger(DiscoveryConfiguration.class);

  private static final String PEER_ID_RELATIVE = ".decksync/peer-id";

  @Bean
  public PeerIdStore peerIdStore(Environment env) {
    return new PeerIdStore(env.home().resolve(PEER_ID_RELATIVE));
  }

  @Bean
  public PeerIdentity peerIdentity(
      PeerIdStore peerIdStore, ObjectProvider<BuildProperties> buildProperties) {
    String peerName = resolvePeerName();
    String peerId = peerIdStore.loadOrCreate();
    String appVersion =
        buildProperties.getIfAvailable() != null
            ? buildProperties.getIfAvailable().getVersion()
            : "dev";
    return new PeerIdentity(peerName, peerId, appVersion, PeerIdentity.CURRENT_PROTOCOL_VERSION);
  }

  @Bean
  public DiscoveredPeers discoveredPeers() {
    return new DiscoveredPeers();
  }

  @Bean(initMethod = "start")
  public DiscoveryService discoveryService(
      PeerIdentity peerIdentity,
      DeckSyncConfig deckSyncConfig,
      DiscoveredPeers discoveredPeers,
      @Value("${server.address:0.0.0.0}") String bindAddressProperty) {
    InetAddress bindAddress = resolveBindAddress(bindAddressProperty);
    return new MdnsDiscoveryService(
        peerIdentity, bindAddress, deckSyncConfig.port(), discoveredPeers);
  }

  private static String resolvePeerName() {
    try {
      String host = InetAddress.getLocalHost().getHostName();
      if (host != null && !host.isBlank()) {
        return host;
      }
    } catch (UnknownHostException e) {
      log.debug("InetAddress.getLocalHost() failed: {}", e.toString());
    }
    String user = System.getProperty("user.name");
    return user != null && !user.isBlank() ? user + "-decksync" : "decksync-peer";
  }

  private static InetAddress resolveBindAddress(String configured) {
    // server.address=0.0.0.0 means "bind all" at the HTTP layer, but jmDNS
    // needs a concrete interface to probe from — falling back to
    // InetAddress.getLocalHost() picks the OS's default outbound address,
    // which matches what peers on the LAN will actually see.
    if (configured == null || configured.isBlank() || "0.0.0.0".equals(configured)) {
      return MdnsDiscoveryService.defaultBindAddress();
    }
    try {
      return InetAddress.getByName(configured);
    } catch (UnknownHostException e) {
      log.warn(
          "Could not resolve server.address='{}' for mDNS bind; falling back to localhost ({})",
          configured,
          e.toString());
      return MdnsDiscoveryService.defaultBindAddress();
    }
  }
}
