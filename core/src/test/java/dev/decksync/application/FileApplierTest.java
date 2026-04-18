package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileApplierTest {

  private final FileApplier applier = new FileApplier();

  @Test
  void writesBytesAtomicallyAndPreservesSourceMtime(@TempDir Path tmp) throws Exception {
    Path target = tmp.resolve("saves/slot_0.sav");
    byte[] payload = "DeckSync payload".getBytes(StandardCharsets.UTF_8);
    Instant mtime = Instant.parse("2026-04-17T20:00:00Z");

    applier.apply(target, payload, mtime);

    assertThat(target).exists().hasBinaryContent(payload);
    assertThat(Files.getLastModifiedTime(target).toInstant()).isEqualTo(mtime);
  }

  @Test
  void leavesNoTmpFileBehindOnSuccess(@TempDir Path tmp) throws Exception {
    Path target = tmp.resolve("save.sav");
    applier.apply(
        target, "bytes".getBytes(StandardCharsets.UTF_8), Instant.parse("2026-04-17T20:00:00Z"));

    try (Stream<Path> listing = Files.list(tmp)) {
      assertThat(listing.map(Path::getFileName).map(Path::toString))
          .containsExactlyInAnyOrder("save.sav");
    }
  }

  @Test
  void overwritesExistingTargetAtomically(@TempDir Path tmp) throws Exception {
    Path target = tmp.resolve("save.sav");
    Files.writeString(target, "old");
    byte[] fresh = "new".getBytes(StandardCharsets.UTF_8);

    applier.apply(target, fresh, Instant.parse("2026-04-17T20:00:00Z"));

    assertThat(target).hasBinaryContent(fresh);
  }

  @Test
  void createsParentDirectoriesIfMissing(@TempDir Path tmp) throws IOException {
    Path target = tmp.resolve("deep/nested/path/save.sav");

    applier.apply(
        target, "x".getBytes(StandardCharsets.UTF_8), Instant.parse("2026-04-17T20:00:00Z"));

    assertThat(target).exists();
  }
}
