package dev.decksync.infrastructure.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.decksync.application.ManifestEntry;
import dev.decksync.application.SavePathRule;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LudusaviManifestParserTest {

  private static InputStream yaml(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void parsesEmptyDocument() {
    var parser = new LudusaviManifestParser();

    assertThat(parser.parse(yaml(""))).isEmpty();
  }

  @Test
  void parsesMinimalEntry() {
    var parser = new LudusaviManifestParser();
    String doc =
        """
        Free Game: {}
        """;

    var entries = parser.parse(yaml(doc));

    assertThat(entries).hasSize(1);
    ManifestEntry e = entries.get(0);
    assertThat(e.name()).isEqualTo("Free Game");
    assertThat(e.steamAppId()).isEmpty();
    assertThat(e.installDirs()).isEmpty();
    assertThat(e.savePaths()).isEmpty();
  }

  @Test
  void parsesSteamAppId() {
    var parser = new LudusaviManifestParser();
    String doc =
        """
        Elden Ring:
          steam:
            id: 1245620
        """;

    assertThat(parser.parse(yaml(doc)).get(0).steamAppId()).contains(1245620L);
  }

  @Test
  void parsesInstallDirs() {
    var parser = new LudusaviManifestParser();
    String doc =
        """
        Elden Ring:
          installDir:
            "ELDEN RING": {}
            "Elden Ring": {}
        """;

    assertThat(parser.parse(yaml(doc)).get(0).installDirs())
        .containsExactlyInAnyOrder("ELDEN RING", "Elden Ring");
  }

  @Test
  void parsesSavePathWithTagsAndWhen() {
    var parser = new LudusaviManifestParser();
    String doc =
        """
        Game:
          files:
            "<base>/Saved/SaveGames":
              tags: [save, config]
              when:
                - os: windows
                - os: linux
                  store: steam
        """;

    ManifestEntry e = parser.parse(yaml(doc)).get(0);
    assertThat(e.savePaths()).hasSize(1);
    SavePathRule rule = e.savePaths().get(0);
    assertThat(rule.template()).isEqualTo("<base>/Saved/SaveGames");
    assertThat(rule.tags()).containsExactlyInAnyOrder("save", "config");
    assertThat(rule.when()).hasSize(2);
    assertThat(rule.when().get(0).os()).contains("windows");
    assertThat(rule.when().get(0).store()).isEmpty();
    assertThat(rule.when().get(1).os()).contains("linux");
    assertThat(rule.when().get(1).store()).contains("steam");
  }

  @Test
  void parsesSavePathWithoutWhenOrTags() {
    var parser = new LudusaviManifestParser();
    String doc =
        """
        Game:
          files:
            "<base>/save": {}
        """;

    SavePathRule rule = parser.parse(yaml(doc)).get(0).savePaths().get(0);
    assertThat(rule.template()).isEqualTo("<base>/save");
    assertThat(rule.tags()).isEmpty();
    assertThat(rule.when()).isEmpty();
  }

  @Test
  void ignoresUnknownTopLevelFields() {
    var parser = new LudusaviManifestParser();
    String doc =
        """
        Game:
          steam:
            id: 42
          registry:
            "HKCU/Software/Game":
              tags: [config]
          launch:
            "/foo":
              - when:
                  - os: windows
          gog:
            id: 1234
          cloud:
            steam: true
        """;

    ManifestEntry e = parser.parse(yaml(doc)).get(0);
    assertThat(e.steamAppId()).contains(42L);
    assertThat(e.savePaths()).isEmpty();
    assertThat(e.installDirs()).isEmpty();
  }

  @Test
  void parsesMultipleEntriesPreservingOrder() {
    var parser = new LudusaviManifestParser();
    String doc =
        """
        Alpha:
          steam:
            id: 1
        Bravo:
          steam:
            id: 2
        Charlie:
          steam:
            id: 3
        """;

    assertThat(parser.parse(yaml(doc)))
        .extracting(ManifestEntry::name)
        .containsExactly("Alpha", "Bravo", "Charlie");
  }

  @Test
  void handlesSteamEntryWithoutId() {
    var parser = new LudusaviManifestParser();
    String doc =
        """
        Game:
          steam: {}
        """;

    assertThat(parser.parse(yaml(doc)).get(0).steamAppId()).isEmpty();
  }

  @Test
  void rejectsNullInput() {
    assertThatNullPointerException().isThrownBy(() -> new LudusaviManifestParser().parse(null));
  }
}
