package dev.decksync.infrastructure.steam;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WindowsRegistrySteamRootFinderTest {

  @Test
  void parsesStandardRegQueryOutput() {
    String output =
        """

        HKEY_CURRENT_USER\\Software\\Valve\\Steam
            SteamPath    REG_SZ    c:/program files (x86)/steam

        """;

    assertThat(WindowsRegistrySteamRootFinder.parse(output))
        .contains(Path.of("c:/program files (x86)/steam"));
  }

  @Test
  void parsesOutputWithBackslashedPath() {
    String output =
        """
        HKEY_CURRENT_USER\\Software\\Valve\\Steam
            SteamPath    REG_SZ    D:\\Games\\Steam
        """;

    assertThat(WindowsRegistrySteamRootFinder.parse(output)).contains(Path.of("D:\\Games\\Steam"));
  }

  @Test
  void parsesOutputWithCrLfLineEndings() {
    String output =
        "\r\nHKEY_CURRENT_USER\\Software\\Valve\\Steam\r\n"
            + "    SteamPath    REG_SZ    c:/steam\r\n";

    assertThat(WindowsRegistrySteamRootFinder.parse(output)).contains(Path.of("c:/steam"));
  }

  @Test
  void returnsEmptyWhenValueMissing() {
    String output =
        """
        ERROR: The system was unable to find the specified registry key or value.
        """;

    assertThat(WindowsRegistrySteamRootFinder.parse(output)).isEmpty();
  }

  @Test
  void returnsEmptyWhenSteamPathHasNoValue() {
    String output =
        """
        HKEY_CURRENT_USER\\Software\\Valve\\Steam
            SteamPath    REG_SZ
        """;

    assertThat(WindowsRegistrySteamRootFinder.parse(output)).isEmpty();
  }

  @Test
  void returnsEmptyForUnrelatedOutput() {
    assertThat(WindowsRegistrySteamRootFinder.parse("")).isEmpty();
    assertThat(WindowsRegistrySteamRootFinder.parse("some\nother\ntext")).isEmpty();
  }

  @Test
  void ignoresUnrelatedSteamPathPrefixesBeforeTheValue() {
    String output =
        """
        HKEY_CURRENT_USER\\Software\\Valve\\Steam
            SteamPathOther    REG_SZ    irrelevant
            SteamPath    REG_SZ    c:/steam
        """;

    assertThat(WindowsRegistrySteamRootFinder.parse(output)).contains(Path.of("c:/steam"));
  }
}
