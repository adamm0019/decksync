package dev.decksync.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

/**
 * Writes fetched bytes to their target location using the same atomic write pattern the scanner
 * relies on: stage to a sibling {@code .decksync.tmp}, then {@link Files#move} with {@link
 * StandardCopyOption#ATOMIC_MOVE ATOMIC_MOVE}. Preserves the source peer's mtime so subsequent
 * scans see bit-identical metadata on both sides.
 */
public final class FileApplier {

  private static final String TMP_SUFFIX = ".decksync.tmp";

  public void apply(Path target, byte[] bytes, Instant sourceMtime) throws IOException {
    Path parent = target.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path tmp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
    Files.write(tmp, bytes);
    try {
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      Files.deleteIfExists(tmp);
      throw e;
    }
    Files.setLastModifiedTime(target, FileTime.from(sourceMtime));
  }
}
