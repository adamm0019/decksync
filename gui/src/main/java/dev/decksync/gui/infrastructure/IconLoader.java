package dev.decksync.gui.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javafx.scene.image.Image;

/**
 * Loads the DeckSync window icons from the classpath in every size JavaFX's {@code Stage.getIcons}
 * may hand to the OS. Windows and the GNOME/KDE shells each pick a different size depending on
 * where the icon is rendered (taskbar, Alt-Tab, title bar, Start menu), so supplying the full
 * spread is both simpler and faster than guessing.
 *
 * <p>PNGs are rasterised at build time by the {@code :generateIcons} Gradle task and committed
 * under {@code gui/src/main/resources/icons/}, so a fresh checkout does not need Batik on the
 * classpath to produce a working GUI.
 */
public final class IconLoader {

  // Mirrors pngSizes in the root build.gradle.kts generateIcons registration.
  // Listed largest-first so Stage.getIcons() has the hi-res option up front when
  // the OS doesn't express a strong preference.
  private static final List<Integer> SIZES = List.of(1024, 512, 256, 128, 64, 48, 32, 24, 16);

  private IconLoader() {}

  /**
   * Returns every DeckSync app icon baked into the classpath, ordered largest to smallest. Callers
   * typically pass the result straight to {@code stage.getIcons().addAll(...)}.
   *
   * @throws IllegalStateException if any expected icon resource is missing — this indicates a
   *     broken build (the {@code :generateIcons} task did not run).
   */
  public static List<Image> loadAll() {
    return SIZES.stream().map(IconLoader::load).toList();
  }

  private static Image load(int size) {
    String resource = resourcePath(size);
    try (InputStream in = IconLoader.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath icon: " + resource);
      }
      return new Image(in);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read icon: " + resource, e);
    }
  }

  /**
   * Package-private so the test can assert every size resolves without pulling in the JavaFX
   * toolkit.
   */
  static List<String> resourcePaths() {
    return SIZES.stream().map(IconLoader::resourcePath).toList();
  }

  private static String resourcePath(int size) {
    return "/icons/icon-" + size + ".png";
  }
}
