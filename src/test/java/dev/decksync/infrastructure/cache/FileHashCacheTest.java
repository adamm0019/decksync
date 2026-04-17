package dev.decksync.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.domain.Sha256;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileHashCacheTest {

  private static final Instant MTIME = Instant.parse("2026-04-17T12:00:00Z");
  private static final Sha256 HASH_A = Sha256.ofHex("aa".repeat(32));
  private static final Sha256 HASH_B = Sha256.ofHex("bb".repeat(32));

  @Test
  void missWhenPathNotCached(@TempDir Path tmp) {
    FileHashCache cache = new FileHashCache(tmp.resolve("hashes.tsv"));
    assertThat(cache.lookup(tmp.resolve("a.sav"), 10, MTIME)).isEmpty();
  }

  @Test
  void hitWhenPathSizeAndMtimeMatch(@TempDir Path tmp) {
    FileHashCache cache = new FileHashCache(tmp.resolve("hashes.tsv"));
    Path file = tmp.resolve("a.sav");
    cache.store(file, 10, MTIME, HASH_A);
    assertThat(cache.lookup(file, 10, MTIME)).contains(HASH_A);
  }

  @Test
  void missWhenSizeDiffers(@TempDir Path tmp) {
    FileHashCache cache = new FileHashCache(tmp.resolve("hashes.tsv"));
    Path file = tmp.resolve("a.sav");
    cache.store(file, 10, MTIME, HASH_A);
    assertThat(cache.lookup(file, 11, MTIME)).isEmpty();
  }

  @Test
  void missWhenMtimeDiffers(@TempDir Path tmp) {
    FileHashCache cache = new FileHashCache(tmp.resolve("hashes.tsv"));
    Path file = tmp.resolve("a.sav");
    cache.store(file, 10, MTIME, HASH_A);
    assertThat(cache.lookup(file, 10, MTIME.plusSeconds(1))).isEmpty();
  }

  @Test
  void persistRoundTripsEntries(@TempDir Path tmp) {
    Path cacheFile = tmp.resolve("hashes.tsv");
    FileHashCache writer = new FileHashCache(cacheFile);
    Path fileA = tmp.resolve("a.sav");
    Path fileB = tmp.resolve("sub/b.sav");
    writer.store(fileA, 100, MTIME, HASH_A);
    writer.store(fileB, 200, MTIME.plusSeconds(10), HASH_B);
    writer.persist();

    FileHashCache reader = new FileHashCache(cacheFile);
    assertThat(reader.lookup(fileA, 100, MTIME)).contains(HASH_A);
    assertThat(reader.lookup(fileB, 200, MTIME.plusSeconds(10))).contains(HASH_B);
    assertThat(reader.sizeForTest()).isEqualTo(2);
  }

  @Test
  void persistSkipsWhenNotDirty(@TempDir Path tmp) {
    Path cacheFile = tmp.resolve("hashes.tsv");
    FileHashCache cache = new FileHashCache(cacheFile);
    cache.persist();
    assertThat(Files.exists(cacheFile)).isFalse();
  }

  @Test
  void persistCreatesParentDirectory(@TempDir Path tmp) {
    Path cacheFile = tmp.resolve("nested/cache/hashes.tsv");
    FileHashCache cache = new FileHashCache(cacheFile);
    cache.store(tmp.resolve("a.sav"), 1, MTIME, HASH_A);
    cache.persist();
    assertThat(Files.isRegularFile(cacheFile)).isTrue();
  }

  @Test
  void loadIgnoresMalformedLines(@TempDir Path tmp) throws IOException {
    Path cacheFile = tmp.resolve("hashes.tsv");
    Files.writeString(
        cacheFile,
        "this is not a valid line\n"
            + HASH_A.hex()
            + "\t10\t"
            + MTIME.toEpochMilli()
            + "\t"
            + tmp.resolve("good.sav")
            + "\n"
            + "aa\t10\t1\tshort-hex\n",
        StandardCharsets.UTF_8);
    FileHashCache cache = new FileHashCache(cacheFile);
    assertThat(cache.sizeForTest()).isEqualTo(1);
    assertThat(cache.lookup(tmp.resolve("good.sav"), 10, MTIME)).contains(HASH_A);
  }

  @Test
  void overwritingEntryUpdatesHash(@TempDir Path tmp) {
    Path cacheFile = tmp.resolve("hashes.tsv");
    FileHashCache cache = new FileHashCache(cacheFile);
    Path file = tmp.resolve("a.sav");
    cache.store(file, 1, MTIME, HASH_A);
    cache.store(file, 2, MTIME.plusSeconds(1), HASH_B);
    Optional<Sha256> looked = cache.lookup(file, 2, MTIME.plusSeconds(1));
    assertThat(looked).contains(HASH_B);
    assertThat(cache.lookup(file, 1, MTIME)).isEmpty();
  }
}
