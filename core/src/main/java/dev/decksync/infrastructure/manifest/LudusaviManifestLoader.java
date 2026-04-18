package dev.decksync.infrastructure.manifest;

import dev.decksync.application.ManifestEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Loads the pinned Ludusavi manifest YAML from the classpath and parses it. The manifest is
 * assembled at build time from an upstream commit SHA, so the same SHA always yields the same
 * parsed list — useful for reproducible cross-peer resolution.
 *
 * <p>A constructor overload accepts an arbitrary {@link InputStream} supplier so tests can feed
 * synthetic YAML without touching the classpath copy.
 */
public final class LudusaviManifestLoader {

  /** Classpath location populated by the {@code downloadLudusaviManifest} Gradle task. */
  public static final String CLASSPATH_RESOURCE = "/ludusavi/manifest.yaml";

  private final LudusaviManifestParser parser;
  private final Supplier<InputStream> source;

  public LudusaviManifestLoader(LudusaviManifestParser parser) {
    this(parser, LudusaviManifestLoader::classpathStream);
  }

  LudusaviManifestLoader(LudusaviManifestParser parser, Supplier<InputStream> source) {
    this.parser = Objects.requireNonNull(parser, "parser");
    this.source = Objects.requireNonNull(source, "source");
  }

  /** Loads and parses the manifest. Each call reads the source afresh. */
  public List<ManifestEntry> load() {
    try (InputStream in = source.get()) {
      if (in == null) {
        throw new IllegalStateException(
            "Manifest source returned null — classpath resource "
                + CLASSPATH_RESOURCE
                + " missing?");
      }
      return parser.parse(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read Ludusavi manifest", e);
    }
  }

  private static InputStream classpathStream() {
    InputStream in = LudusaviManifestLoader.class.getResourceAsStream(CLASSPATH_RESOURCE);
    if (in == null) {
      throw new IllegalStateException(
          "Classpath resource "
              + CLASSPATH_RESOURCE
              + " not found — did the downloadLudusaviManifest task run?");
    }
    return in;
  }
}
