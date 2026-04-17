package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupServiceTest {

  private static final GameId GAME = new GameId.SteamAppId(1245620L);
  private static final LogicalPath LOGICAL = new LogicalPath("saves/slot_0.sav");

  @Test
  void copiesExistingFileIntoHistoryBeforeOverwrite(@TempDir Path tmp) throws Exception {
    Path historyRoot = tmp.resolve("history");
    Path gameRoot = tmp.resolve("game");
    Path target = gameRoot.resolve("saves/slot_0.sav");
    Files.createDirectories(target.getParent());
    byte[] originalBytes = "ORIGINAL".getBytes(StandardCharsets.UTF_8);
    Files.write(target, originalBytes);

    BackupService service = new BackupService(historyRoot, fixedClock("2026-04-17T20:00:00Z"));
    service.backupIfExists(GAME, LOGICAL, target);

    Path snapshot =
        historyRoot
            .resolve("steam-1245620")
            .resolve("2026-04-17T20-00-00Z")
            .resolve("saves/slot_0.sav");
    assertThat(snapshot).exists().hasBinaryContent(originalBytes);
  }

  @Test
  void skipsBackupWhenTargetDoesNotExist(@TempDir Path tmp) throws Exception {
    Path historyRoot = tmp.resolve("history");
    Path target = tmp.resolve("game/saves/slot_0.sav");

    BackupService service = new BackupService(historyRoot, fixedClock("2026-04-17T20:00:00Z"));
    service.backupIfExists(GAME, LOGICAL, target);

    assertThat(historyRoot).doesNotExist();
  }

  @Test
  void pruneDeletesOldestSnapshotsKeepingMostRecent(@TempDir Path tmp) throws Exception {
    Path historyRoot = tmp.resolve("history");
    Path gameHistory = historyRoot.resolve("steam-1245620");
    Files.createDirectories(gameHistory);
    List<String> timestamps =
        List.of(
            "2026-04-10T20-00-00Z",
            "2026-04-11T20-00-00Z",
            "2026-04-12T20-00-00Z",
            "2026-04-13T20-00-00Z",
            "2026-04-14T20-00-00Z");
    for (String ts : timestamps) {
      Path snap = gameHistory.resolve(ts);
      Files.createDirectories(snap);
      Files.writeString(snap.resolve("marker"), ts);
    }

    BackupService service = new BackupService(historyRoot, fixedClock("2026-04-15T20:00:00Z"));
    service.prune(GAME, 3);

    try (Stream<Path> remaining = Files.list(gameHistory)) {
      assertThat(remaining.map(p -> p.getFileName().toString()))
          .containsExactlyInAnyOrder(
              "2026-04-12T20-00-00Z", "2026-04-13T20-00-00Z", "2026-04-14T20-00-00Z");
    }
  }

  @Test
  void pruneIsNoopWhenHistoryDoesNotExist(@TempDir Path tmp) throws IOException {
    BackupService service =
        new BackupService(tmp.resolve("history"), fixedClock("2026-04-17T20:00:00Z"));
    service.prune(GAME, 20);
  }

  @Test
  void usesSlugValueVerbatimForGameWithSlugId(@TempDir Path tmp) throws Exception {
    Path historyRoot = tmp.resolve("history");
    Path target = tmp.resolve("game/save.sav");
    Files.createDirectories(target.getParent());
    Files.writeString(target, "x");

    BackupService service = new BackupService(historyRoot, fixedClock("2026-04-17T20:00:00Z"));
    service.backupIfExists(new GameId.Slug("stardew-valley"), new LogicalPath("save.sav"), target);

    assertThat(historyRoot.resolve("stardew-valley/2026-04-17T20-00-00Z/save.sav")).exists();
  }

  private static Clock fixedClock(String isoInstant) {
    return Clock.fixed(Instant.parse(isoInstant), ZoneOffset.UTC);
  }
}
