package dev.decksync.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Sha256Test {

  private static final String ZERO_HEX =
      "0000000000000000000000000000000000000000000000000000000000000000";

  @Test
  void acceptsThirtyTwoByteArray() {
    byte[] bytes = new byte[32];
    Sha256 hash = new Sha256(bytes);
    assertThat(hash.hex()).isEqualTo(ZERO_HEX);
  }

  @Test
  void rejectsWrongLengthByteArray() {
    assertThatThrownBy(() -> new Sha256(new byte[31]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32");
  }

  @Test
  void rejectsNullByteArray() {
    assertThatThrownBy(() -> new Sha256((byte[]) null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void defensivelyCopiesInputArray() {
    byte[] bytes = new byte[32];
    bytes[0] = 0x11;
    Sha256 hash = new Sha256(bytes);
    bytes[0] = 0x22;
    assertThat(hash.hex()).startsWith("11");
  }

  @Test
  void parsesLowerHex() {
    Sha256 hash = Sha256.ofHex("deadbeef" + "00".repeat(28));
    assertThat(hash.hex()).isEqualTo("deadbeef" + "00".repeat(28));
  }

  @Test
  void parsesUpperHex() {
    Sha256 hash = Sha256.ofHex("DEADBEEF" + "00".repeat(28));
    assertThat(hash.hex()).isEqualTo("deadbeef" + "00".repeat(28));
  }

  @Test
  void rejectsShortHex() {
    assertThatThrownBy(() -> Sha256.ofHex("deadbeef")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNonHex() {
    assertThatThrownBy(() -> Sha256.ofHex("xy" + "00".repeat(31)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void equalsByContent() {
    assertThat(Sha256.ofHex(ZERO_HEX)).isEqualTo(Sha256.ofHex(ZERO_HEX));
    assertThat(Sha256.ofHex(ZERO_HEX)).isNotEqualTo(Sha256.ofHex("01" + "00".repeat(31)));
  }
}
