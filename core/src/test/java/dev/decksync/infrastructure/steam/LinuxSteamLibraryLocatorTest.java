package dev.decksync.infrastructure.steam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.decksync.application.FakeEnvironment;
import dev.decksync.application.SteamLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxSteamLibraryLocatorTest {

  @Test
  void findsNativeInstall(@TempDir Path home) throws IOException {
    Path libRoot = home.resolve("games");
    writeLibraryFolders(home.resolve(".steam/steam"), libRoot);

    LinuxSteamLibraryLocator locator =
        new LinuxSteamLibraryLocator(
            new FakeEnvironment(home, "deck"), new LibraryFoldersVdfReader());

    assertThat(locator.locate()).extracting(SteamLibrary::path).containsExactly(libRoot);
  }

  @Test
  void fallsBackToFlatpakWhenNativeMissing(@TempDir Path home) throws IOException {
    Path libRoot = home.resolve("games");
    writeLibraryFolders(home.resolve(".var/app/com.valvesoftware.Steam/.steam/steam"), libRoot);

    LinuxSteamLibraryLocator locator =
        new LinuxSteamLibraryLocator(
            new FakeEnvironment(home, "deck"), new LibraryFoldersVdfReader());

    assertThat(locator.locate()).extracting(SteamLibrary::path).containsExactly(libRoot);
  }

  @Test
  void prefersNativeOverFlatpakWhenBothPresent(@TempDir Path home) throws IOException {
    Path nativeLib = home.resolve("native-lib");
    Path flatpakLib = home.resolve("flatpak-lib");
    writeLibraryFolders(home.resolve(".steam/steam"), nativeLib);
    writeLibraryFolders(home.resolve(".var/app/com.valvesoftware.Steam/.steam/steam"), flatpakLib);

    List<SteamLibrary> libs =
        new LinuxSteamLibraryLocator(
                new FakeEnvironment(home, "deck"), new LibraryFoldersVdfReader())
            .locate();

    assertThat(libs).extracting(SteamLibrary::path).containsExactly(nativeLib);
  }

  @Test
  void returnsEmptyWhenNeitherPathExists(@TempDir Path home) {
    assertThat(
            new LinuxSteamLibraryLocator(
                    new FakeEnvironment(home, "deck"), new LibraryFoldersVdfReader())
                .locate())
        .isEmpty();
  }

  @Test
  void rejectsNullArgs(@TempDir Path home) {
    LibraryFoldersVdfReader reader = new LibraryFoldersVdfReader();
    FakeEnvironment env = new FakeEnvironment(home, "deck");

    assertThatNullPointerException().isThrownBy(() -> new LinuxSteamLibraryLocator(null, reader));
    assertThatNullPointerException().isThrownBy(() -> new LinuxSteamLibraryLocator(env, null));
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
