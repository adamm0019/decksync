package dev.decksync.application;

import dev.decksync.domain.Sha256;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * A cache of previously-computed file hashes keyed by {@code (absolute path, size, mtime)} — the
 * same tuple a filesystem reuses when a file hasn't been touched. Rehashing large saves dominates
 * scanner runtime, so skipping even a few multi-hundred-MB files is worth a persistent cache.
 * Implementations may be in-memory or file-backed; use {@link NoopHashCache#INSTANCE} for tests
 * that don't care.
 */
public interface HashCache {

  Optional<Sha256> lookup(Path path, long size, Instant mtime);

  void store(Path path, long size, Instant mtime, Sha256 hash);

  /**
   * Flush any in-memory state to durable storage. Called by callers (CLI, sync engine) at natural
   * checkpoints — typically the end of a scan. A no-op by default so purely in-memory
   * implementations can ignore it.
   */
  default void persist() {
    // no-op
  }
}
