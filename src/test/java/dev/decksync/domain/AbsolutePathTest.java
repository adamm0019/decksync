package dev.decksync.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbsolutePathTest {

  @Test
  void acceptsAbsolutePath(@TempDir Path tempDir) {
    AbsolutePath p = new AbsolutePath(tempDir);

    assertThat(p.path()).isEqualTo(tempDir);
  }

  @Test
  void rejectsRelativePath() {
    Path relative = Path.of("saves/slot_0.sav");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AbsolutePath(relative))
        .withMessageContaining("absolute");
  }

  @Test
  void rejectsNull() {
    assertThatIllegalArgumentException().isThrownBy(() -> new AbsolutePath(null));
  }

  @Test
  void sameAbsolutePathIsEqual(@TempDir Path tempDir) {
    assertThat(new AbsolutePath(tempDir)).isEqualTo(new AbsolutePath(tempDir));
  }
}
