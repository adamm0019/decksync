package dev.decksync.domain;

import java.nio.file.Path;

/**
 * An absolute filesystem path. Value-equal to another instance with the same {@link Path}. Used for
 * locally-resolved save directories — {@link GameCatalog#resolveInstalled()} returns a {@code
 * Map<GameId, AbsolutePath>}. Never transmitted over the wire — the protocol carries only {@link
 * LogicalPath}.
 */
public record AbsolutePath(Path path) {

  public AbsolutePath {
    if (path == null) {
      throw new IllegalArgumentException("AbsolutePath must not be null");
    }
    if (!path.isAbsolute()) {
      throw new IllegalArgumentException("AbsolutePath requires an absolute path — got: " + path);
    }
  }
}
