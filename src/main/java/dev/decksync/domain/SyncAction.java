package dev.decksync.domain;

/**
 * A per-file decision emitted by the planner for a single {@link LogicalPath}. {@link Pull} means
 * fetch the remote entry's bytes and overwrite the local file (backing up whatever was there
 * first). {@link Skip} means no transfer — either nothing changed, or the local side is considered
 * authoritative. {@link Conflict} means both sides modified the file with identical mtimes but
 * different hashes; last-writer-wins can't pick a side automatically and Phase 1 surfaces it to the
 * operator rather than picking blindly.
 *
 * <p>{@code Delete} is deliberately absent — Phase 1 never removes files from either peer.
 */
public sealed interface SyncAction {

  LogicalPath path();

  record Pull(LogicalPath path, FileEntry remote) implements SyncAction {
    public Pull {
      if (path == null) {
        throw new IllegalArgumentException("Pull path must not be null");
      }
      if (remote == null) {
        throw new IllegalArgumentException("Pull remote must not be null");
      }
    }
  }

  record Skip(LogicalPath path, Reason reason) implements SyncAction {
    public enum Reason {
      /** Remote and local agree on the bytes — nothing to do. */
      ALREADY_IN_SYNC,
      /** Both sides have the file with different contents, but local mtime wins. */
      LOCAL_NEWER,
      /** File exists only locally; Phase 1 never deletes on the remote's behalf. */
      LOCAL_ONLY
    }

    public Skip {
      if (path == null) {
        throw new IllegalArgumentException("Skip path must not be null");
      }
      if (reason == null) {
        throw new IllegalArgumentException("Skip reason must not be null");
      }
    }
  }

  record Conflict(LogicalPath path, FileEntry local, FileEntry remote) implements SyncAction {
    public Conflict {
      if (path == null) {
        throw new IllegalArgumentException("Conflict path must not be null");
      }
      if (local == null) {
        throw new IllegalArgumentException("Conflict local must not be null");
      }
      if (remote == null) {
        throw new IllegalArgumentException("Conflict remote must not be null");
      }
    }
  }
}
