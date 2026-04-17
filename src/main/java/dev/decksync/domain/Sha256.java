package dev.decksync.domain;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * A 32-byte SHA-256 digest. Always wrapped rather than passed around as a raw {@code byte[]} so
 * equality is value-based and length violations fail at construction, not at hash-compare time.
 * Transmitted in hex form over the wire; {@link #hex()} and {@link #ofHex(String)} are the
 * serialization pair.
 */
public record Sha256(byte[] bytes) {

  private static final HexFormat HEX = HexFormat.of();

  public Sha256 {
    if (bytes == null) {
      throw new IllegalArgumentException("Sha256 bytes must not be null");
    }
    if (bytes.length != 32) {
      throw new IllegalArgumentException("Sha256 bytes must be exactly 32 — got: " + bytes.length);
    }
    bytes = bytes.clone();
  }

  public static Sha256 ofHex(String hex) {
    if (hex == null) {
      throw new IllegalArgumentException("Sha256 hex must not be null");
    }
    if (hex.length() != 64) {
      throw new IllegalArgumentException("Sha256 hex must be 64 chars — got: " + hex.length());
    }
    try {
      return new Sha256(HEX.parseHex(hex));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Sha256 hex must be valid hexadecimal — got: " + hex, e);
    }
  }

  public String hex() {
    return HEX.formatHex(bytes);
  }

  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Sha256 other && Arrays.equals(bytes, other.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "Sha256[" + hex() + "]";
  }
}
