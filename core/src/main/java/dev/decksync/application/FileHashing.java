package dev.decksync.application;

import dev.decksync.domain.Sha256;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Streaming SHA-256 helper shared by the scanner and the peer file-download endpoint. Separated out
 * of {@link DefaultFileScanner} so the web layer can reuse the same hashing path without depending
 * on the scanner's lock-probe logic — file downloads don't need exclusive access, and stale bytes
 * are acceptable because the peer's retry loop reconciles against the manifest.
 */
public final class FileHashing {

  private static final int BUFFER_SIZE = 64 * 1024;

  private FileHashing() {}

  public static Sha256 hash(Path path) throws IOException {
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
      MessageDigest digest = newSha256();
      ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
      while (channel.read(buffer) != -1) {
        buffer.flip();
        digest.update(buffer);
        buffer.clear();
      }
      return new Sha256(digest.digest());
    }
  }

  private static MessageDigest newSha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
