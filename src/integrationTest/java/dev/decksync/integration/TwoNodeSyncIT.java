package dev.decksync.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.application.BackupService;
import dev.decksync.application.DefaultFileScanner;
import dev.decksync.application.FileApplier;
import dev.decksync.application.FileScanner;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.NoopHashCache;
import dev.decksync.application.PeerClient;
import dev.decksync.application.SyncOutcome;
import dev.decksync.application.SyncPlanner;
import dev.decksync.application.SyncService;
import dev.decksync.config.JacksonConfiguration;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.infrastructure.net.HttpPeerClient;
import dev.decksync.web.FileDownloadController;
import dev.decksync.web.GamesController;
import dev.decksync.web.ManifestController;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * End-to-end sync across two separately-booted Spring contexts. Peer A hosts a real Tomcat on a
 * random port and serves a save directory populated by the test; peer B runs as a sync client and
 * pulls from peer A. Asserts the newer file lands on peer B AND that peer B's previous contents are
 * preserved under {@code history/}.
 *
 * <p>Both contexts boot from the same bespoke {@link IntegTestApp} rather than {@code
 * DeckSyncApplication} so we can swap in a stub {@link GameCatalog} that points at a configured
 * temp directory — keeping the real Ludusavi manifest + Steam library resolution out of the test
 * path entirely.
 */
class TwoNodeSyncIT {

  private static final GameId GAME = new GameId.SteamAppId(1245620L);
  private static final Instant LATER = Instant.parse("2026-04-17T20:00:00Z");
  private static final Instant EARLIER = Instant.parse("2026-04-17T19:00:00Z");

  @Test
  void peerBPullsNewerFileFromPeerAAndBacksUpExistingLocalCopy(@TempDir Path tmp) throws Exception {
    Path saveRootA = tmp.resolve("peerA/saves");
    Path saveRootB = tmp.resolve("peerB/saves");
    Path historyB = tmp.resolve("peerB/history");
    Files.createDirectories(saveRootA);
    Files.createDirectories(saveRootB);

    byte[] oldBytes = "OLD-CONTENTS".getBytes(StandardCharsets.UTF_8);
    byte[] newBytes = "NEW-CONTENTS-FROM-A".getBytes(StandardCharsets.UTF_8);
    Path fileA = saveRootA.resolve("slot_0.sav");
    Path fileB = saveRootB.resolve("slot_0.sav");
    Files.write(fileA, newBytes);
    Files.setLastModifiedTime(fileA, FileTime.from(LATER));
    Files.write(fileB, oldBytes);
    Files.setLastModifiedTime(fileB, FileTime.from(EARLIER));

    try (ConfigurableApplicationContext peerA = startPeer(saveRootA, historyB, 0, null);
        ConfigurableApplicationContext peerB = startPeer(saveRootB, historyB, 0, peerUrl(peerA))) {

      SyncService sync = peerB.getBean(SyncService.class);
      SyncOutcome outcome = sync.syncGame(GAME, false);

      assertThat(outcome.appliedPaths()).containsExactly("slot_0.sav");
      assertThat(Files.readAllBytes(fileB)).isEqualTo(newBytes);
      assertThat(Files.getLastModifiedTime(fileB).toInstant()).isEqualTo(LATER);

      Path backup = historyB.resolve("steam-1245620");
      try (var stream = Files.list(backup)) {
        Path snapshotDir = stream.findFirst().orElseThrow();
        assertThat(snapshotDir.resolve("slot_0.sav")).exists().hasBinaryContent(oldBytes);
      }
    }
  }

  private static String peerUrl(ConfigurableApplicationContext ctx) {
    int port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
    return "http://localhost:" + port;
  }

  private static ConfigurableApplicationContext startPeer(
      Path saveRoot, Path historyRoot, int port, String peerUrl) {
    SpringApplication app = new SpringApplication(IntegTestApp.class);
    String[] args = {
      "--server.port=" + port,
      "--integ.save-root=" + saveRoot.toAbsolutePath(),
      "--integ.history-root=" + historyRoot.toAbsolutePath(),
      "--integ.peer-url=" + (peerUrl == null ? "http://localhost:0" : peerUrl),
      "--spring.main.banner-mode=off"
    };
    return app.run(args);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    JacksonConfiguration.class,
    GamesController.class,
    ManifestController.class,
    FileDownloadController.class
  })
  static class IntegTestApp {

    @Bean
    Clock clock() {
      return Clock.systemUTC();
    }

    @Bean
    FileScanner fileScanner(Clock clock) {
      return new DefaultFileScanner(clock, Duration.ZERO, NoopHashCache.INSTANCE);
    }

    @Bean
    GameCatalog gameCatalog(@Value("${integ.save-root}") String saveRoot) {
      AbsolutePath root = new AbsolutePath(Path.of(saveRoot).toAbsolutePath());
      Map<GameId, AbsolutePath> map = Map.of(GAME, root);
      return () -> map;
    }

    @Bean
    SyncPlanner syncPlanner() {
      return new SyncPlanner();
    }

    @Bean
    FileApplier fileApplier() {
      return new FileApplier();
    }

    @Bean
    BackupService backupService(@Value("${integ.history-root}") String historyRoot, Clock clock) {
      return new BackupService(Path.of(historyRoot).toAbsolutePath(), clock);
    }

    @Bean
    PeerClient peerClient(
        org.springframework.web.client.RestClient.Builder builder,
        @Value("${integ.peer-url}") URI peerUrl) {
      return HttpPeerClient.create(builder, peerUrl);
    }

    @Bean
    SyncService syncService(
        PeerClient peer,
        FileScanner scanner,
        SyncPlanner planner,
        BackupService backupService,
        FileApplier fileApplier,
        GameCatalog catalog) {
      return new SyncService(peer, scanner, planner, backupService, fileApplier, catalog, 20);
    }
  }
}
