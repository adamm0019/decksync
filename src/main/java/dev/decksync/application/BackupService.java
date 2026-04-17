package dev.decksync.application;

import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Keeps versioned, timestamped copies of save files so a bad sync can be rolled back by hand.
 * Before overwriting a file, {@link #backupIfExists} snapshots the current bytes under {@code
 * <historyRoot>/<gameFsName>/<timestamp>/<logical>}. {@link #prune} trims each game's history to
 * the most recent {@code keep} snapshots.
 *
 * <p>Directory names avoid colons (both for the game id and the ISO timestamp) so paths are
 * portable to Windows, which rejects {@code :} in filenames. ISO-8601 order is preserved: the
 * dash-separated timestamp still sorts lexicographically like the canonical form.
 */
public final class BackupService {

  /** {@code 2026-04-17T20-00-00Z} — sortable and Windows-safe. */
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(java.time.ZoneOffset.UTC);

  private final Path historyRoot;
  private final Clock clock;

  public BackupService(Path historyRoot, Clock clock) {
    this.historyRoot = historyRoot;
    this.clock = clock;
  }

  public void backupIfExists(GameId game, LogicalPath logical, Path target) throws IOException {
    if (!Files.exists(target)) {
      return;
    }
    Path snapshot =
        gameHistory(game).resolve(TIMESTAMP.format(clock.instant())).resolve(logical.path());
    Files.createDirectories(snapshot.getParent());
    Files.copy(target, snapshot, StandardCopyOption.COPY_ATTRIBUTES);
  }

  public void prune(GameId game, int keep) throws IOException {
    Path gameHistory = gameHistory(game);
    if (!Files.isDirectory(gameHistory)) {
      return;
    }
    List<Path> snapshots;
    try (Stream<Path> entries = Files.list(gameHistory)) {
      snapshots =
          entries
              .filter(Files::isDirectory)
              .sorted(Comparator.comparing(p -> p.getFileName().toString()))
              .toList();
    }
    int toDelete = snapshots.size() - keep;
    for (int i = 0; i < toDelete; i++) {
      deleteRecursively(snapshots.get(i));
    }
  }

  private Path gameHistory(GameId game) {
    return historyRoot.resolve(gameFsName(game));
  }

  private static String gameFsName(GameId game) {
    return switch (game) {
      case GameId.SteamAppId s -> "steam-" + s.appid();
      case GameId.Slug s -> s.value();
    };
  }

  private static void deleteRecursively(Path root) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
      for (Path p : paths) {
        Files.delete(p);
      }
    }
  }
}
