package dev.decksync.infrastructure.steam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.decksync.application.SteamLibrary;
import dev.decksync.application.SteamLibraryLocator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxProtonPrefixResolverTest {

  @Test
  void returnsPrefixWhenCompatdataExists(@TempDir Path tempDir) throws IOException {
    Path library = tempDir.resolve("lib");
    Path expected = createPrefix(library, 440L);
    SteamLibraryLocator locator = () -> List.of(new SteamLibrary(library, Set.of(440L)));

    assertThat(new LinuxProtonPrefixResolver(locator).resolve(440L)).contains(expected);
  }

  @Test
  void picksLibraryThatOwnsTheAppId(@TempDir Path tempDir) throws IOException {
    Path libA = tempDir.resolve("A");
    Path libB = tempDir.resolve("B");
    Files.createDirectories(libA);
    Path expected = createPrefix(libB, 730L);

    SteamLibraryLocator locator =
        () -> List.of(new SteamLibrary(libA, Set.of(440L)), new SteamLibrary(libB, Set.of(730L)));

    assertThat(new LinuxProtonPrefixResolver(locator).resolve(730L)).contains(expected);
  }

  @Test
  void returnsEmptyWhenAppIdNotInAnyLibrary(@TempDir Path tempDir) {
    SteamLibraryLocator locator = () -> List.of(new SteamLibrary(tempDir, Set.of(440L)));

    assertThat(new LinuxProtonPrefixResolver(locator).resolve(99999L)).isEmpty();
  }

  @Test
  void returnsEmptyWhenCompatdataDirectoryAbsent(@TempDir Path tempDir) {
    SteamLibraryLocator locator = () -> List.of(new SteamLibrary(tempDir, Set.of(440L)));

    assertThat(new LinuxProtonPrefixResolver(locator).resolve(440L)).isEmpty();
  }

  @Test
  void returnsEmptyWhenCompatdataExistsButPfxNotCreatedYet(@TempDir Path tempDir)
      throws IOException {
    Path library = tempDir.resolve("lib");
    Files.createDirectories(library.resolve("steamapps/compatdata/440"));
    SteamLibraryLocator locator = () -> List.of(new SteamLibrary(library, Set.of(440L)));

    assertThat(new LinuxProtonPrefixResolver(locator).resolve(440L)).isEmpty();
  }

  @Test
  void returnsEmptyWhenNoLibrariesReported() {
    assertThat(new LinuxProtonPrefixResolver(List::of).resolve(440L)).isEmpty();
  }

  @Test
  void rejectsNullLocator() {
    assertThatNullPointerException().isThrownBy(() -> new LinuxProtonPrefixResolver(null));
  }

  private static Path createPrefix(Path library, long appId) throws IOException {
    Path steamuser =
        library
            .resolve("steamapps")
            .resolve("compatdata")
            .resolve(Long.toString(appId))
            .resolve("pfx")
            .resolve("drive_c")
            .resolve("users")
            .resolve("steamuser");
    Files.createDirectories(steamuser);
    return steamuser;
  }
}
