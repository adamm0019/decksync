package dev.decksync.application;

import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.FileEntry;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import dev.decksync.domain.SyncAction;
import dev.decksync.domain.SyncPlan;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives a single game's sync top-to-bottom: scan the local save directory, fetch the peer's
 * manifest, hand both to the {@link SyncPlanner}, and then walk the resulting {@link SyncPlan}
 * applying each {@link SyncAction.Pull} via {@link BackupService} + {@link FileApplier}. Conflicts
 * and skips are logged but don't short-circuit the rest of the plan — one file's conflict shouldn't
 * block an unrelated file from converging.
 *
 * <p>Dry-run is a first-class mode rather than a wrapper: the planner always runs, but the executor
 * loop is skipped so the returned {@link SyncOutcome} still carries the plan for the CLI to print.
 */
public final class SyncService {

  private static final Logger log = LoggerFactory.getLogger(SyncService.class);

  private final PeerClient peer;
  private final FileScanner scanner;
  private final SyncPlanner planner;
  private final BackupService backupService;
  private final FileApplier fileApplier;
  private final GameCatalog catalog;
  private final int retentionKeep;

  public SyncService(
      PeerClient peer,
      FileScanner scanner,
      SyncPlanner planner,
      BackupService backupService,
      FileApplier fileApplier,
      GameCatalog catalog,
      int retentionKeep) {
    if (retentionKeep <= 0) {
      throw new IllegalArgumentException("retentionKeep must be positive — got: " + retentionKeep);
    }
    this.peer = peer;
    this.scanner = scanner;
    this.planner = planner;
    this.backupService = backupService;
    this.fileApplier = fileApplier;
    this.catalog = catalog;
    this.retentionKeep = retentionKeep;
  }

  public SyncOutcome syncGame(GameId game, boolean dryRun) {
    Map<GameId, AbsolutePath> installed = catalog.resolveInstalled();
    AbsolutePath root = installed.get(game);
    if (root == null) {
      throw new IllegalStateException("Game not installed locally: " + game);
    }
    Manifest local = scanner.scan(game, root);
    Manifest remote = peer.fetchManifest(game);
    SyncPlan plan = planner.plan(local, remote);

    if (dryRun) {
      return new SyncOutcome(plan, List.of(), true);
    }

    List<String> applied = new ArrayList<>();
    for (SyncAction action : plan.actions()) {
      switch (action) {
        case SyncAction.Pull pull -> {
          applyPull(game, root, pull);
          applied.add(pull.path().path());
        }
        case SyncAction.Skip skip ->
            log.debug("sync skip {} {}: {}", game, skip.path().path(), skip.reason());
        case SyncAction.Conflict conflict ->
            log.warn(
                "sync conflict {} {}: local mtime={} hash={} vs remote mtime={} hash={}",
                game,
                conflict.path().path(),
                conflict.local().mtime(),
                conflict.local().hash(),
                conflict.remote().mtime(),
                conflict.remote().hash());
      }
    }

    try {
      backupService.prune(game, retentionKeep);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to prune backup history for " + game, e);
    }

    return new SyncOutcome(plan, applied, false);
  }

  private void applyPull(GameId game, AbsolutePath root, SyncAction.Pull pull) {
    LogicalPath logical = pull.path();
    Path target = root.path().resolve(logical.path());
    FileEntry remoteEntry = pull.remote();
    try {
      backupService.backupIfExists(game, logical, target);
      byte[] bytes = peer.downloadFile(game, logical);
      fileApplier.apply(target, bytes, remoteEntry.mtime());
      log.info(
          "sync pull {} {} ({} bytes, mtime={})",
          game,
          logical.path(),
          remoteEntry.size(),
          remoteEntry.mtime());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to apply pull for " + logical.path(), e);
    }
  }
}
