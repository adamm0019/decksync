package dev.decksync.config;

import dev.decksync.application.BackupService;
import dev.decksync.application.Environment;
import dev.decksync.application.FileApplier;
import dev.decksync.application.FileScanner;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.PeerClient;
import dev.decksync.application.SyncPlanner;
import dev.decksync.application.SyncService;
import dev.decksync.infrastructure.net.HttpPeerClient;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the sync engine: the pure {@link SyncPlanner}, the filesystem adapters ({@link
 * BackupService}, {@link FileApplier}), the HTTP {@link PeerClient}, and the {@link SyncService}
 * that glues them together. Retention and peer URL come from Spring config so operators can tweak
 * them per-host without recompiling.
 */
@Configuration
public class SyncConfiguration {

  private static final String HISTORY_RELATIVE = ".decksync/history";

  @Bean
  public SyncPlanner syncPlanner() {
    return new SyncPlanner();
  }

  @Bean
  public FileApplier fileApplier() {
    return new FileApplier();
  }

  @Bean
  public BackupService backupService(Environment env, Clock clock) {
    Path historyRoot = env.home().resolve(HISTORY_RELATIVE);
    return new BackupService(historyRoot, clock);
  }

  @Bean
  public PeerClient peerClient(
      RestClient.Builder builder, @Value("${decksync.peer.url:http://localhost:47824}") URI peer) {
    return HttpPeerClient.create(builder, peer);
  }

  @Bean
  public SyncService syncService(
      PeerClient peer,
      FileScanner scanner,
      SyncPlanner planner,
      BackupService backupService,
      FileApplier fileApplier,
      GameCatalog catalog,
      @Value("${decksync.sync.retention:20}") int retention) {
    return new SyncService(peer, scanner, planner, backupService, fileApplier, catalog, retention);
  }
}
