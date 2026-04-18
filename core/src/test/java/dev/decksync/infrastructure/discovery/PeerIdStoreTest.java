package dev.decksync.infrastructure.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PeerIdStoreTest {

  @Test
  void loadOrCreate_whenFileMissing_mintsAndPersists(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("peer-id");
    PeerIdStore store = new PeerIdStore(file, deterministicRandom());

    String id = store.loadOrCreate();

    assertThat(id).hasSize(12).matches("[0-9a-f]+");
    assertThat(Files.readString(file).trim()).isEqualTo(id);
  }

  @Test
  void loadOrCreate_whenFileExists_returnsStoredValue(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("peer-id");
    Files.writeString(file, "a1b2c3d4e5f6\n");
    PeerIdStore store = new PeerIdStore(file, deterministicRandom());

    assertThat(store.loadOrCreate()).isEqualTo("a1b2c3d4e5f6");
  }

  @Test
  void loadOrCreate_whenFileCorrupt_remints(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("peer-id");
    Files.writeString(file, "not-hex!!");
    PeerIdStore store = new PeerIdStore(file, deterministicRandom());

    String id = store.loadOrCreate();

    assertThat(id).hasSize(12).matches("[0-9a-f]+");
    assertThat(Files.readString(file).trim()).isEqualTo(id);
  }

  @Test
  void loadOrCreate_createsParentDirectoryIfMissing(@TempDir Path dir) {
    Path file = dir.resolve("nested/dirs/peer-id");
    PeerIdStore store = new PeerIdStore(file, deterministicRandom());

    String id = store.loadOrCreate();

    assertThat(id).hasSize(12);
    assertThat(Files.exists(file)).isTrue();
  }

  @Test
  void constructor_rejectsNullFile() {
    assertThatThrownBy(() -> new PeerIdStore(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("file");
  }

  private static SecureRandom deterministicRandom() {
    SecureRandom random = new SecureRandom();
    random.setSeed(42L);
    return random;
  }
}
