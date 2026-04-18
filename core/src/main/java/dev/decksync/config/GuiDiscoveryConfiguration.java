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
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires a listen-only mDNS discovery service for the GUI process ({@code decksync gui}). The
 * first-run wizard's "Find peer" step reads from {@link DiscoveredPeers} to show the user which
 * other DeckSync peers are advertising on the LAN.
 *
 * <p>We do <em>not</em> advertise from the GUI — the GUI isn't serving HTTP, so announcing our
 * presence would invite other peers to try to connect and fail with connection refused. The serve
 * daemon, running alongside (or on the other machine), is the one that advertises.
 *
 * <p>Mirrors most of {@link DiscoveryConfiguration} deliberately: both configs wire the same {@link
 * PeerIdStore}/{@link PeerIdentity}/{@link DiscoveredPeers} beans, but they're gated by
 * mutually-exclusive conditions ({@code @Profile("gui")} here versus
 * {@code @ConditionalOnWebApplication(SERVLET)} there), so at most one of them contributes beans
 * per Spring context.
 */
@Configuration
@Profile("gui")
public class GuiDiscoveryConfiguration {

  private static final Logger log = LoggerFactory.getLogger(GuiDiscoveryConfiguration.class);

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
      PeerIdentity peerIdentity, DeckSyncConfig deckSyncConfig, DiscoveredPeers discoveredPeers) {
    return new MdnsDiscoveryService(
        peerIdentity,
        MdnsDiscoveryService.defaultBindAddress(),
        deckSyncConfig.port(),
        discoveredPeers,
        // advertise=false — see class Javadoc.
        false);
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
}
