package dev.decksync.gui.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the build-time contract between {@code :generateIcons} (which writes the PNGs) and {@link
 * IconLoader} (which reads them at runtime). We can't exercise {@link IconLoader#loadAll()}
 * directly here — JavaFX won't instantiate {@code Image} without a toolkit, and we deliberately
 * don't pull TestFX in just for one headless probe — so we assert the classpath contract instead:
 * every size IconLoader will ask for has a corresponding resource on the test classpath.
 */
class IconLoaderTest {

  private static final List<Integer> EXPECTED_SIZES =
      List.of(16, 24, 32, 48, 64, 128, 256, 512, 1024);

  @Test
  void everyExpectedIconSizeIsOnTheClasspath() {
    for (int size : EXPECTED_SIZES) {
      String resource = "/icons/icon-" + size + ".png";
      try (InputStream in = IconLoaderTest.class.getResourceAsStream(resource)) {
        assertThat(in).as("classpath resource %s", resource).isNotNull();
      } catch (Exception e) {
        throw new AssertionError("Failed to probe " + resource, e);
      }
    }
  }

  @Test
  void resourcePathsCoverEverySizeWeExpect() {
    assertThat(IconLoader.resourcePaths())
        .containsExactlyInAnyOrderElementsOf(
            EXPECTED_SIZES.stream().map(s -> "/icons/icon-" + s + ".png").toList());
  }
}
