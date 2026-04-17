package dev.decksync.infrastructure.steam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.decksync.application.SteamLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsSteamLibraryLocatorTest {

  @Test
  void usesRegistryRootWhenVdfPresent(@TempDir Path tempDir) throws IOException {
    Path steamRoot = tempDir.resolve("steam");
    Path libRoot = tempDir.resolve("lib");
    writeLibraryFolders(steamRoot, libRoot);

    WindowsSteamLibraryLocator locator =
        new WindowsSteamLibraryLocator(
            () -> Optional.of(steamRoot), new LibraryFoldersVdfReader(), tempDir.resolve("unused"));

    List<SteamLibrary> libs = locator.locate();

    assertThat(libs).hasSize(1);
    assertThat(libs.get(0).path()).isEqualTo(libRoot);
  }

  @Test
  void fallsBackToDefaultRootWhenRegistryAbsent(@TempDir Path tempDir) throws IOException {
    Path fallback = tempDir.resolve("fallback");
    Path libRoot = tempDir.resolve("lib");
    writeLibraryFolders(fallback, libRoot);

    WindowsSteamLibraryLocator locator =
        new WindowsSteamLibraryLocator(Optional::empty, new LibraryFoldersVdfReader(), fallback);

    assertThat(locator.locate()).extracting(SteamLibrary::path).containsExactly(libRoot);
  }

  @Test
  void fallsBackToDefaultRootWhenRegistryRootHasNoVdf(@TempDir Path tempDir) throws IOException {
    Path registryRoot = tempDir.resolve("no-vdf-here");
    Files.createDirectories(registryRoot);
    Path fallback = tempDir.resolve("fallback");
    Path libRoot = tempDir.resolve("lib");
    writeLibraryFolders(fallback, libRoot);

    WindowsSteamLibraryLocator locator =
        new WindowsSteamLibraryLocator(
            () -> Optional.of(registryRoot), new LibraryFoldersVdfReader(), fallback);

    assertThat(locator.locate()).extracting(SteamLibrary::path).containsExactly(libRoot);
  }

  @Test
  void returnsEmptyWhenNeitherCandidateHasVdf(@TempDir Path tempDir) {
    WindowsSteamLibraryLocator locator =
        new WindowsSteamLibraryLocator(
            () -> Optional.of(tempDir.resolve("missing-a")),
            new LibraryFoldersVdfReader(),
            tempDir.resolve("missing-b"));

    assertThat(locator.locate()).isEmpty();
  }

  @Test
  void doesNotProbeFallbackTwiceWhenRegistryEqualsFallback(@TempDir Path tempDir)
      throws IOException {
    Path shared = tempDir.resolve("shared");
    Path libRoot = tempDir.resolve("lib");
    writeLibraryFolders(shared, libRoot);

    WindowsSteamLibraryLocator locator =
        new WindowsSteamLibraryLocator(
            () -> Optional.of(shared), new LibraryFoldersVdfReader(), shared);

    assertThat(locator.locate()).extracting(SteamLibrary::path).containsExactly(libRoot);
  }

  @Test
  void rejectsNullArgs() {
    LibraryFoldersVdfReader reader = new LibraryFoldersVdfReader();
    SteamRootFinder finder = Optional::empty;
    Path fallback = Path.of(".").toAbsolutePath();

    assertThatNullPointerException().isThrownBy(() -> new WindowsSteamLibraryLocator(null, reader));
    assertThatNullPointerException().isThrownBy(() -> new WindowsSteamLibraryLocator(finder, null));
    assertThatNullPointerException()
        .isThrownBy(() -> new WindowsSteamLibraryLocator(null, reader, fallback));
    assertThatNullPointerException()
        .isThrownBy(() -> new WindowsSteamLibraryLocator(finder, null, fallback));
    assertThatNullPointerException()
        .isThrownBy(() -> new WindowsSteamLibraryLocator(finder, reader, null));
  }

  private static void writeLibraryFolders(Path steamRoot, Path libraryRoot) throws IOException {
    Path steamApps = steamRoot.resolve("steamapps");
    Files.createDirectories(steamApps);
    Files.createDirectories(libraryRoot);
    String escaped = libraryRoot.toString().replace("\\", "\\\\");
    String vdf =
        """
        "libraryfolders"
        {
          "0" { "path" "%s" "apps" { "730" "0" } }
        }
        """
            .formatted(escaped);
    Files.writeString(steamApps.resolve("libraryfolders.vdf"), vdf);
  }
}
