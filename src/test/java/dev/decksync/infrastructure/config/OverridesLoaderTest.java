package dev.decksync.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.decksync.application.Overrides;
import dev.decksync.domain.GameId;
import dev.decksync.domain.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OverridesLoaderTest {

  private final OverridesLoader loader = new OverridesLoader();

  @Test
  void parsesSlugKeyWithBothPlatforms() {
    String yaml =
        """
        elden-ring:
          windows: 'D:\\Saves\\EldenRing'
          linux: '/home/deck/SavesBackup/EldenRing'
        """;

    Overrides overrides = loader.parse(yaml);

    GameId id = new GameId.Slug("elden-ring");
    assertThat(overrides.forGame(id, new Platform.Windows())).contains("D:\\Saves\\EldenRing");
    assertThat(overrides.forGame(id, new Platform.Linux()))
        .contains("/home/deck/SavesBackup/EldenRing");
  }

  @Test
  void parsesNumericKeyAsSteamAppId() {
    String yaml =
        """
        1245620:
          windows: 'D:\\Saves\\EldenRing'
        """;

    Overrides overrides = loader.parse(yaml);

    assertThat(overrides.byGame()).containsOnlyKeys(new GameId.SteamAppId(1245620L));
  }

  @Test
  void parsesPartialOverrideWithOnlyOnePlatform() {
    String yaml =
        """
        deep-rock:
          linux: '/home/deck/saves/drg'
        """;

    Overrides overrides = loader.parse(yaml);

    GameId id = new GameId.Slug("deep-rock");
    assertThat(overrides.forGame(id, new Platform.Linux())).contains("/home/deck/saves/drg");
    assertThat(overrides.forGame(id, new Platform.Windows())).isEmpty();
  }

  @Test
  void preservesDocumentOrder() {
    String yaml =
        """
        first:
          windows: 'D:\\1'
        second:
          windows: 'D:\\2'
        third:
          windows: 'D:\\3'
        """;

    Overrides overrides = loader.parse(yaml);

    assertThat(overrides.byGame().keySet())
        .containsExactly(
            new GameId.Slug("first"), new GameId.Slug("second"), new GameId.Slug("third"));
  }

  @Test
  void emptyStringYieldsEmptyOverrides() {
    assertThat(loader.parse("")).isEqualTo(Overrides.EMPTY);
  }

  @Test
  void nullDocumentYieldsEmptyOverrides() {
    assertThat(loader.parse("# just a comment\n")).isEqualTo(Overrides.EMPTY);
  }

  @Test
  void loadReturnsEmptyWhenFileMissing(@TempDir Path dir) {
    assertThat(loader.load(dir.resolve("does-not-exist.yml"))).isEqualTo(Overrides.EMPTY);
  }

  @Test
  void loadReadsFromDisk(@TempDir Path dir) throws IOException {
    Path file = dir.resolve("overrides.yml");
    Files.writeString(
        file,
        """
        elden-ring:
          windows: 'D:\\Saves'
        """);

    Overrides overrides = loader.load(file);

    assertThat(overrides.forGame(new GameId.Slug("elden-ring"), new Platform.Windows()))
        .contains("D:\\Saves");
  }

  @Test
  void rejectsNonMappingRoot() {
    assertThatThrownBy(() -> loader.parse("- one\n- two\n"))
        .isInstanceOf(OverridesLoader.OverridesParseException.class)
        .hasMessageContaining("mapping at the top level");
  }

  @Test
  void rejectsEntryWithoutWindowsOrLinux() {
    String yaml =
        """
        elden-ring:
          mac: '/Users/a/saves'
        """;

    assertThatThrownBy(() -> loader.parse(yaml))
        .isInstanceOf(OverridesLoader.OverridesParseException.class)
        .hasMessageContaining("at least one of windows or linux");
  }

  @Test
  void rejectsEntryValueThatIsNotMapping() {
    assertThatThrownBy(() -> loader.parse("elden-ring: 'D:\\\\Saves'\n"))
        .isInstanceOf(OverridesLoader.OverridesParseException.class)
        .hasMessageContaining("must be a mapping");
  }

  @Test
  void rejectsBlankPlatformValue() {
    String yaml =
        """
        elden-ring:
          windows: ''
        """;

    assertThatThrownBy(() -> loader.parse(yaml))
        .isInstanceOf(OverridesLoader.OverridesParseException.class)
        .hasMessageContaining("non-blank string");
  }

  @Test
  void rejectsInvalidSlug() {
    String yaml =
        """
        Bad_Slug:
          windows: 'D:\\Saves'
        """;

    assertThatThrownBy(() -> loader.parse(yaml))
        .isInstanceOf(OverridesLoader.OverridesParseException.class)
        .hasMessageContaining("Bad_Slug");
  }

  @Test
  void rejectsNullArgs() {
    assertThatNullPointerException().isThrownBy(() -> loader.load(null));
    assertThatNullPointerException().isThrownBy(() -> loader.parse((String) null));
    assertThatNullPointerException().isThrownBy(() -> loader.parse((java.io.Reader) null));
    assertThatNullPointerException().isThrownBy(() -> loader.parse((java.io.InputStream) null));
  }
}
