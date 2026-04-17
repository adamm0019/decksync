package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SteamLibraryTest {

  @Test
  void acceptsAbsolutePathAndAppIds(@TempDir Path tempDir) {
    SteamLibrary lib = new SteamLibrary(tempDir, Set.of(1245620L, 730L));

    assertThat(lib.path()).isEqualTo(tempDir);
    assertThat(lib.appIds()).containsExactlyInAnyOrder(1245620L, 730L);
  }

  @Test
  void acceptsEmptyAppIds(@TempDir Path tempDir) {
    SteamLibrary lib = new SteamLibrary(tempDir, Set.of());

    assertThat(lib.appIds()).isEmpty();
  }

  @Test
  void appIdsAreDefensivelyCopied(@TempDir Path tempDir) {
    var mutable = new HashSet<>(Set.of(1L, 2L));

    SteamLibrary lib = new SteamLibrary(tempDir, mutable);
    mutable.add(3L);

    assertThat(lib.appIds()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void rejectsRelativePath() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SteamLibrary(Path.of("steamapps/common"), Set.of()))
        .withMessageContaining("absolute");
  }

  @Test
  void rejectsNullArgs(@TempDir Path tempDir) {
    assertThatNullPointerException().isThrownBy(() -> new SteamLibrary(null, Set.of()));
    assertThatNullPointerException().isThrownBy(() -> new SteamLibrary(tempDir, null));
  }
}
