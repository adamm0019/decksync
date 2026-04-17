package dev.decksync.domain;

import java.time.Instant;

/**
 * A single file inside a game save directory, identified by its game-relative {@link LogicalPath}.
 * The {@link #size} and {@link #mtime} together with {@link #hash} drive sync decisions: hashes
 * decide redundancy, mtime decides who wins a last-writer-wins conflict, size is a cheap pre-check.
 */
public record FileEntry(LogicalPath path, long size, Instant mtime, Sha256 hash) {

  public FileEntry {
    if (path == null) {
      throw new IllegalArgumentException("FileEntry path must not be null");
    }
    if (size < 0) {
      throw new IllegalArgumentException("FileEntry size must not be negative — got: " + size);
    }
    if (mtime == null) {
      throw new IllegalArgumentException("FileEntry mtime must not be null");
    }
    if (hash == null) {
      throw new IllegalArgumentException("FileEntry hash must not be null");
    }
  }
}
