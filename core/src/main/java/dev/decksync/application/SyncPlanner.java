package dev.decksync.application;

import dev.decksync.domain.FileEntry;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import dev.decksync.domain.SyncAction;
import dev.decksync.domain.SyncPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure-domain diff between a local and remote {@link Manifest} for a single game. No IO, no Spring
 * — inputs and outputs are records so the planner is trivially unit-testable and its behavior is
 * the contract peers rely on for deterministic last-writer-wins.
 *
 * <p>The rules, enumerated once so they're reviewable:
 *
 * <ul>
 *   <li>Hashes equal → {@link SyncAction.Skip.Reason#ALREADY_IN_SYNC}.
 *   <li>Remote mtime strictly greater AND hashes differ → {@link SyncAction.Pull}.
 *   <li>Local mtime strictly greater AND hashes differ → {@link
 *       SyncAction.Skip.Reason#LOCAL_NEWER}.
 *   <li>Mtimes equal AND hashes differ → {@link SyncAction.Conflict} (refuses to pick a winner).
 *   <li>Present only remotely → {@link SyncAction.Pull}.
 *   <li>Present only locally → {@link SyncAction.Skip.Reason#LOCAL_ONLY} (Phase 1 never deletes).
 * </ul>
 */
public final class SyncPlanner {

  public SyncPlan plan(Manifest local, Manifest remote) {
    if (local == null) {
      throw new IllegalArgumentException("local manifest must not be null");
    }
    if (remote == null) {
      throw new IllegalArgumentException("remote manifest must not be null");
    }
    Map<LogicalPath, FileEntry> localByPath = index(local);
    Map<LogicalPath, FileEntry> remoteByPath = index(remote);

    Set<LogicalPath> all = new HashSet<>();
    all.addAll(localByPath.keySet());
    all.addAll(remoteByPath.keySet());

    List<SyncAction> actions = new ArrayList<>(all.size());
    for (LogicalPath path : all) {
      FileEntry l = localByPath.get(path);
      FileEntry r = remoteByPath.get(path);
      actions.add(decide(path, l, r));
    }
    return new SyncPlan(local.game(), actions);
  }

  private static SyncAction decide(LogicalPath path, FileEntry local, FileEntry remote) {
    if (local == null) {
      return new SyncAction.Pull(path, remote);
    }
    if (remote == null) {
      return new SyncAction.Skip(path, SyncAction.Skip.Reason.LOCAL_ONLY);
    }
    if (local.hash().equals(remote.hash())) {
      return new SyncAction.Skip(path, SyncAction.Skip.Reason.ALREADY_IN_SYNC);
    }
    int cmp = remote.mtime().compareTo(local.mtime());
    if (cmp > 0) {
      return new SyncAction.Pull(path, remote);
    }
    if (cmp < 0) {
      return new SyncAction.Skip(path, SyncAction.Skip.Reason.LOCAL_NEWER);
    }
    return new SyncAction.Conflict(path, local, remote);
  }

  private static Map<LogicalPath, FileEntry> index(Manifest manifest) {
    Map<LogicalPath, FileEntry> byPath = new HashMap<>(manifest.files().size());
    for (FileEntry entry : manifest.files()) {
      byPath.put(entry.path(), entry);
    }
    return byPath;
  }
}
