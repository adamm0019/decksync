package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExpansionContextTest {

  @Test
  void acceptsValidInputs(@TempDir Path tempDir) {
    Path root = tempDir.resolve("library");
    Path base = root.resolve("game");

    ExpansionContext ctx =
        new ExpansionContext("Elden Ring", Optional.of("76561198000000001"), root, base);

    assertThat(ctx.gameName()).isEqualTo("Elden Ring");
    assertThat(ctx.storeUserId()).contains("76561198000000001");
    assertThat(ctx.installRoot()).isEqualTo(root);
    assertThat(ctx.installBase()).isEqualTo(base);
  }

  @Test
  void allowsEmptyStoreUserId(@TempDir Path tempDir) {
    ExpansionContext ctx =
        new ExpansionContext("Stardew Valley", Optional.empty(), tempDir, tempDir);

    assertThat(ctx.storeUserId()).isEmpty();
  }

  @Test
  void rejectsNullGameName(@TempDir Path tempDir) {
    assertThatNullPointerException()
        .isThrownBy(() -> new ExpansionContext(null, Optional.empty(), tempDir, tempDir));
  }

  @Test
  void rejectsBlankGameName(@TempDir Path tempDir) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ExpansionContext("   ", Optional.empty(), tempDir, tempDir))
        .withMessageContaining("gameName");
  }

  @Test
  void rejectsNullStoreUserId(@TempDir Path tempDir) {
    assertThatNullPointerException()
        .isThrownBy(() -> new ExpansionContext("Game", null, tempDir, tempDir));
  }

  @Test
  void rejectsRelativeInstallRoot(@TempDir Path tempDir) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ExpansionContext("Game", Optional.empty(), Path.of("relative/root"), tempDir))
        .withMessageContaining("installRoot");
  }

  @Test
  void rejectsRelativeInstallBase(@TempDir Path tempDir) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new ExpansionContext("Game", Optional.empty(), tempDir, Path.of("relative/base")))
        .withMessageContaining("installBase");
  }
}
