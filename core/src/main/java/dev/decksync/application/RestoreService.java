package dev.decksync.application;

import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rolls a game's save directory back to a specific historical snapshot. Each file in the snapshot
 * is first backed up (so the user can undo the undo), then copied over the live save using the same
 * atomic-write path {@link FileApplier} uses for a normal pull. Files that exist in the live save
 * but not in the snapshot are left untouched — Phase 1 never deletes, restore included.
 */
public final class RestoreService {

  private static final Logger log = LoggerFactory.getLogger(RestoreService.class);

  private final BackupService backupService;
  private final FileApplier fileApplier;
  private final GameCatalog catalog;

  public RestoreService(BackupService backupService, FileApplier fileApplier, GameCatalog catalog) {
    this.backupService = backupService;
    this.fileApplier = fileApplier;
    this.catalog = catalog;
  }

  /**
   * Restore {@code game}'s live save directory to the snapshot taken at {@code snapshot}. Returns
   * the logical paths that were written. Throws if the game isn't locally installed or the snapshot
   * directory is missing.
   */
  public List<LogicalPath> restore(GameId game, Instant snapshot) {
    Map<GameId, AbsolutePath> installed = catalog.resolveInstalled();
    AbsolutePath root = installed.get(game);
    if (root == null) {
      throw new IllegalStateException("Game not installed locally: " + game);
    }
    Path snapshotDir = backupService.snapshotDir(game, snapshot);
    if (!Files.isDirectory(snapshotDir)) {
      throw new IllegalStateException("Snapshot not found: " + snapshotDir);
    }

    List<LogicalPath> restored = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(snapshotDir)) {
      List<Path> files = walk.filter(Files::isRegularFile).toList();
      for (Path file : files) {
        String relative = snapshotDir.relativize(file).toString().replace('\\', '/');
        LogicalPath logical = new LogicalPath(relative);
        Path target = root.path().resolve(logical.path());
        backupService.backupIfExists(game, logical, target);
        byte[] bytes = Files.readAllBytes(file);
        Instant mtime = Files.getLastModifiedTime(file).toInstant();
        fileApplier.apply(target, bytes, mtime);
        log.info("restore {} {} ← {}", game, logical.path(), snapshot);
        restored.add(logical);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to restore " + game + " from " + snapshot, e);
    }
    return restored;
  }
}
