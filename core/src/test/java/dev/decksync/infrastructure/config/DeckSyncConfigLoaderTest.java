package dev.decksync.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.decksync.application.DeckSyncConfig;
import dev.decksync.domain.GameId;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeckSyncConfigLoaderTest {

  private final DeckSyncConfigLoader loader = new DeckSyncConfigLoader();

  @Test
  void missingFileReturnsDefaults(@TempDir Path tmp) {
    DeckSyncConfig config = loader.load(tmp.resolve("no-such-file.yml"));

    assertThat(config).isEqualTo(DeckSyncConfig.defaults());
  }

  @Test
  void fullyPopulatedYamlParsesEachField() {
    String yaml =
        """
        peer:
          url: http://192.168.1.5:47824
        games:
          - 1245620
          - stardew-valley
        port: 55555
        retention: 5
        """;

    DeckSyncConfig config = loader.parse(yaml);

    assertThat(config.peerUrl()).isEqualTo(URI.create("http://192.168.1.5:47824"));
    assertThat(config.watchedGames())
        .containsExactly(new GameId.SteamAppId(1245620L), new GameId.Slug("stardew-valley"));
    assertThat(config.port()).isEqualTo(55555);
    assertThat(config.retention()).isEqualTo(5);
  }

  @Test
  void partialYamlFillsUnspecifiedFieldsFromDefaults() {
    DeckSyncConfig config =
        loader.parse(
            """
            peer:
              url: http://deck.local:47824
            """);

    assertThat(config.peerUrl()).isEqualTo(URI.create("http://deck.local:47824"));
    assertThat(config.watchedGames()).isEmpty();
    assertThat(config.port()).isEqualTo(DeckSyncConfig.DEFAULT_PORT);
    assertThat(config.retention()).isEqualTo(DeckSyncConfig.DEFAULT_RETENTION);
  }

  @Test
  void rejectsMalformedPeerUrl() {
    assertThatThrownBy(
            () ->
                loader.parse(
                    """
                    peer:
                      url: 'not a url'
                    """))
        .isInstanceOf(DeckSyncConfigLoader.ConfigParseException.class)
        .hasMessageContaining("peer.url");
  }

  @Test
  void rejectsUrlWithoutHost() {
    assertThatThrownBy(
            () ->
                loader.parse(
                    """
                    peer:
                      url: http:///path
                    """))
        .isInstanceOf(DeckSyncConfigLoader.ConfigParseException.class)
        .hasMessageContaining("host");
  }

  @Test
  void rejectsUnknownGameId() {
    assertThatThrownBy(
            () ->
                loader.parse(
                    """
                    games:
                      - 'NotValidId!'
                    """))
        .isInstanceOf(DeckSyncConfigLoader.ConfigParseException.class)
        .hasMessageContaining("invalid game id");
  }

  @Test
  void rejectsOutOfRangePort() {
    assertThatThrownBy(
            () ->
                loader.parse(
                    """
                    port: 99999
                    """))
        .isInstanceOf(DeckSyncConfigLoader.ConfigParseException.class)
        .hasMessageContaining("port");
  }

  @Test
  void rejectsZeroRetention() {
    assertThatThrownBy(
            () ->
                loader.parse(
                    """
                    retention: 0
                    """))
        .isInstanceOf(DeckSyncConfigLoader.ConfigParseException.class)
        .hasMessageContaining("retention");
  }

  @Test
  void loadsFromRealFile(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("config.yml");
    Files.writeString(
        file,
        """
        peer:
          url: http://deck.local:47824
        retention: 10
        """);

    DeckSyncConfig config = loader.load(file);

    assertThat(config.peerUrl()).isEqualTo(URI.create("http://deck.local:47824"));
    assertThat(config.retention()).isEqualTo(10);
  }
}
