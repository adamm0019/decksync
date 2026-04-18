package dev.decksync.infrastructure.discovery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Mints and persists a stable per-install peer id. The value is a random 48-bit nonce written to
 * {@code ~/.decksync/peer-id} on first access — hex-encoded so operators can eyeball it in logs
 * without a decoder step. It stays identical across process restarts so subscribers on the other
 * side of the LAN can recognise the same peer reappearing after a reboot.
 *
 * <p>This is a <em>placeholder until M8</em>. When TLS + pairing lands, the peer id becomes a SHA
 * of the peer's self-signed cert rather than a random nonce, so the six-word BIP-39 fingerprint
 * users verify at pairing time anchors to real key material. Storage location and file shape stay
 * the same; only the provenance of the bytes changes.
 */
public final class PeerIdStore {

  private static final int ID_BYTES = 6;

  private final Path file;
  private final SecureRandom random;

  public PeerIdStore(Path file) {
    this(file, new SecureRandom());
  }

  PeerIdStore(Path file, SecureRandom random) {
    if (file == null) {
      throw new IllegalArgumentException("file must not be null");
    }
    if (random == null) {
      throw new IllegalArgumentException("random must not be null");
    }
    this.file = file;
    this.random = random;
  }

  /** Returns the stored peer id, minting and persisting one on first call. */
  public String loadOrCreate() {
    try {
      if (Files.exists(file)) {
        String existing = Files.readString(file).trim();
        if (isValidHex(existing)) {
          return existing;
        }
      }
      return mintAndWrite();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read or write peer id at " + file, e);
    }
  }

  private String mintAndWrite() throws IOException {
    byte[] bytes = new byte[ID_BYTES];
    random.nextBytes(bytes);
    String id = HexFormat.of().formatHex(bytes);
    Files.createDirectories(file.getParent());
    Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
    Files.writeString(tmp, id + System.lineSeparator());
    try {
      Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException atomicFailed) {
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
    return id;
  }

  private static boolean isValidHex(String value) {
    if (value == null || value.length() != ID_BYTES * 2) {
      return false;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      boolean digit = c >= '0' && c <= '9';
      boolean lower = c >= 'a' && c <= 'f';
      boolean upper = c >= 'A' && c <= 'F';
      if (!digit && !lower && !upper) {
        return false;
      }
    }
    return true;
  }
}
