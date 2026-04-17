package dev.decksync.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestTest {

  private static final Instant NOW = Instant.parse("2026-04-17T20:00:00Z");
  private static final Sha256 ZERO = Sha256.ofHex("00".repeat(32));

  @Test
  void sortsFilesByLogicalPath() {
    FileEntry b = new FileEntry(new LogicalPath("b.sav"), 1, NOW, ZERO);
    FileEntry a = new FileEntry(new LogicalPath("a.sav"), 1, NOW, ZERO);
    FileEntry nested = new FileEntry(new LogicalPath("sub/c.sav"), 1, NOW, ZERO);

    Manifest manifest = new Manifest(new GameId.Slug("game"), List.of(b, nested, a), NOW);

    assertThat(manifest.files()).containsExactly(a, b, nested);
  }

  @Test
  void rejectsDuplicatePaths() {
    FileEntry a = new FileEntry(new LogicalPath("a.sav"), 1, NOW, ZERO);
    FileEntry aDup = new FileEntry(new LogicalPath("a.sav"), 2, NOW, ZERO);

    assertThatThrownBy(() -> new Manifest(new GameId.Slug("game"), List.of(a, aDup), NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate");
  }

  @Test
  void emptyFilesIsAllowed() {
    Manifest manifest = new Manifest(new GameId.Slug("game"), List.of(), NOW);
    assertThat(manifest.files()).isEmpty();
  }

  @Test
  void filesListIsImmutable() {
    FileEntry a = new FileEntry(new LogicalPath("a.sav"), 1, NOW, ZERO);
    Manifest manifest = new Manifest(new GameId.Slug("game"), List.of(a), NOW);
    assertThatThrownBy(() -> manifest.files().add(a))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
