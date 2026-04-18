package dev.decksync.application;

import dev.decksync.domain.Sha256;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * A no-op {@link HashCache} that never returns a hit. Used by tests and by the initial scanner
 * construction before a real cache is wired.
 */
public enum NoopHashCache implements HashCache {
  INSTANCE;

  @Override
  public Optional<Sha256> lookup(Path path, long size, Instant mtime) {
    return Optional.empty();
  }

  @Override
  public void store(Path path, long size, Instant mtime, Sha256 hash) {
    // intentional no-op
  }
}
