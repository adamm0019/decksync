package dev.decksync.config;

import dev.decksync.application.BackupService;
import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.Environment;
import dev.decksync.application.FileApplier;
import dev.decksync.application.FileScanner;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.PeerClient;
import dev.decksync.application.SyncPlanner;
import dev.decksync.application.SyncService;
import dev.decksync.infrastructure.net.HttpPeerClient;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the sync engine: the pure {@link SyncPlanner}, the filesystem adapters ({@link
 * BackupService}, {@link FileApplier}), the HTTP {@link PeerClient}, and the {@link SyncService}
 * that glues them together. Peer URL and retention come from the {@link DeckSyncConfig} bean —
 * config.yml is the single source of truth, defaults only apply when the file is absent.
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
  public PeerClient peerClient(RestClient.Builder builder, DeckSyncConfig config) {
    return HttpPeerClient.create(builder, config.peerUrl());
  }

  @Bean
  public SyncService syncService(
      PeerClient peer,
      FileScanner scanner,
      SyncPlanner planner,
      BackupService backupService,
      FileApplier fileApplier,
      GameCatalog catalog,
      DeckSyncConfig config) {
    return new SyncService(
        peer, scanner, planner, backupService, fileApplier, catalog, config.retention());
  }
}
