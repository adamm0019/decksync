package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.FileEntry;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import dev.decksync.domain.Sha256;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultFileScannerTest {

  private static final Instant NOW = Instant.parse("2026-04-17T20:00:00Z");
  private static final GameId GAME = new GameId.Slug("elden-ring");

  @Test
  void scansRegularFilesAndProducesStableHashes(@TempDir Path dir) throws IOException {
    writeOldFile(dir.resolve("a.sav"), new byte[] {1, 2, 3});
    writeOldFile(dir.resolve("sub/b.sav"), new byte[] {4, 5});

    Manifest manifest = newScanner().scan(GAME, new AbsolutePath(dir));

    assertThat(manifest.game()).isEqualTo(GAME);
    assertThat(manifest.files())
        .extracting(f -> f.path().path())
        .containsExactly("a.sav", "sub/b.sav");
    FileEntry a = manifest.files().getFirst();
    assertThat(a.size()).isEqualTo(3);
    assertThat(a.hash()).isEqualTo(expectedHash(new byte[] {1, 2, 3}));
  }

  @Test
  void normalisesBackslashesInLogicalPath(@TempDir Path dir) throws IOException {
    writeOldFile(dir.resolve("nested/sub/c.sav"), new byte[] {9});
    Manifest manifest = newScanner().scan(GAME, new AbsolutePath(dir));
    assertThat(manifest.files().getFirst().path()).isEqualTo(new LogicalPath("nested/sub/c.sav"));
  }

  @Test
  void skipsFilesModifiedWithinStabilityWindow(@TempDir Path dir) throws IOException {
    Path recent = dir.resolve("recent.sav");
    Files.writeString(recent, "fresh");
    Files.setLastModifiedTime(recent, FileTime.from(NOW.minusSeconds(1)));

    Path old = dir.resolve("old.sav");
    writeOldFile(old, new byte[] {7});

    Manifest manifest = newScanner().scan(GAME, new AbsolutePath(dir));

    assertThat(manifest.files()).hasSize(1);
    assertThat(manifest.files().getFirst().path().path()).isEqualTo("old.sav");
  }

  @Test
  void skipsLockedFile(@TempDir Path dir) throws IOException {
    Path locked = dir.resolve("locked.sav");
    Files.writeString(locked, "locked");
    Files.setLastModifiedTime(locked, FileTime.from(NOW.minusSeconds(60)));

    Path free = dir.resolve("free.sav");
    writeOldFile(free, new byte[] {1});

    try (FileChannel channel = FileChannel.open(locked, StandardOpenOption.WRITE)) {
      FileLock heldLock = channel.lock();
      try {
        Manifest manifest = newScanner().scan(GAME, new AbsolutePath(dir));
        assertThat(manifest.files()).hasSize(1);
        assertThat(manifest.files().getFirst().path().path()).isEqualTo("free.sav");
      } finally {
        heldLock.release();
      }
    }
  }

  @Test
  void returnsEmptyManifestWhenRootMissing(@TempDir Path dir) {
    Manifest manifest = newScanner().scan(GAME, new AbsolutePath(dir.resolve("does-not-exist")));
    assertThat(manifest.files()).isEmpty();
    assertThat(manifest.game()).isEqualTo(GAME);
  }

  @Test
  void generatedAtReflectsClock(@TempDir Path dir) throws IOException {
    writeOldFile(dir.resolve("a.sav"), new byte[] {1});
    Manifest manifest = newScanner().scan(GAME, new AbsolutePath(dir));
    assertThat(manifest.generatedAt()).isEqualTo(NOW);
  }

  private static void writeOldFile(Path path, byte[] bytes) throws IOException {
    Files.createDirectories(path.getParent());
    Files.write(path, bytes);
    Files.setLastModifiedTime(path, FileTime.from(NOW.minusSeconds(60)));
  }

  private static Sha256 expectedHash(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return new Sha256(md.digest(bytes));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private static DefaultFileScanner newScanner() {
    return new DefaultFileScanner(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(3));
  }
}
