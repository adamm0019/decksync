package dev.decksync.infrastructure.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.decksync.application.ManifestEntry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class LudusaviManifestLoaderTest {

  @Test
  void loadsFromSuppliedStream() {
    var parser = new LudusaviManifestParser();
    String yaml =
        """
        Elden Ring:
          steam:
            id: 1245620
        """;
    var loader =
        new LudusaviManifestLoader(
            parser, () -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

    List<ManifestEntry> entries = loader.load();

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).steamAppId()).contains(1245620L);
  }

  @Test
  void loadsFromClasspathByDefault() {
    // The downloadLudusaviManifest Gradle task populates this resource; the real manifest
    // lists several thousand games, so a lower-bound of 100 is plenty to confirm wiring.
    var loader = new LudusaviManifestLoader(new LudusaviManifestParser());

    List<ManifestEntry> entries = loader.load();

    assertThat(entries).hasSizeGreaterThan(100);
    assertThat(entries).anyMatch(e -> e.steamAppId().equals(Optional.of(1245620L)));
  }

  @Test
  void closesSuppliedStream() {
    var parser = new LudusaviManifestParser();
    var closedFlag = new boolean[] {false};
    Supplier<InputStream> source =
        () ->
            new ByteArrayInputStream("Game: {}".getBytes(StandardCharsets.UTF_8)) {
              @Override
              public void close() throws IOException {
                closedFlag[0] = true;
                super.close();
              }
            };
    var loader = new LudusaviManifestLoader(parser, source);

    loader.load();

    assertThat(closedFlag[0]).isTrue();
  }

  @Test
  void throwsWhenSupplierReturnsNull() {
    var loader = new LudusaviManifestLoader(new LudusaviManifestParser(), () -> null);

    assertThatIllegalStateException().isThrownBy(loader::load).withMessageContaining("null");
  }

  @Test
  void wrapsIoExceptionOnClose() {
    Supplier<InputStream> source =
        () ->
            new ByteArrayInputStream("Game: {}".getBytes(StandardCharsets.UTF_8)) {
              @Override
              public void close() throws IOException {
                throw new IOException("boom");
              }
            };
    var loader = new LudusaviManifestLoader(new LudusaviManifestParser(), source);

    assertThatThrownBy(loader::load)
        .isInstanceOf(java.io.UncheckedIOException.class)
        .hasMessageContaining("manifest");
  }

  @Test
  void rejectsNullArgs() {
    assertThatNullPointerException().isThrownBy(() -> new LudusaviManifestLoader(null));
    assertThatNullPointerException().isThrownBy(() -> new LudusaviManifestLoader(null, () -> null));
    assertThatNullPointerException()
        .isThrownBy(() -> new LudusaviManifestLoader(new LudusaviManifestParser(), null));
  }
}
