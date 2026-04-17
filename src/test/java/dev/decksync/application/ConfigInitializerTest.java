package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.domain.GameId;
import dev.decksync.infrastructure.config.DeckSyncConfigLoader;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigInitializerTest {

  private final DeckSyncConfigLoader loader = new DeckSyncConfigLoader();

  @Test
  void rendersFullyPopulatedConfig() {
    DeckSyncConfig config =
        new DeckSyncConfig(
            URI.create("http://192.168.1.11:47824"),
            List.of(new GameId.SteamAppId(1245620L), new GameId.Slug("stardew-valley")),
            47824,
            20);

    String yaml = ConfigInitializer.render(config);

    assertThat(yaml).contains("url: http://192.168.1.11:47824");
    assertThat(yaml).contains("port: 47824");
    assertThat(yaml).contains("retention: 20");
    assertThat(yaml).contains("- steam:1245620");
    assertThat(yaml).contains("- stardew-valley");
  }

  @Test
  void rendersEmptyGamesAsInlineList() {
    DeckSyncConfig config = DeckSyncConfig.defaults();

    String yaml = ConfigInitializer.render(config);

    assertThat(yaml).contains("games: []");
  }

  @Test
  void roundTripsThroughLoader() {
    DeckSyncConfig original =
        new DeckSyncConfig(
            URI.create("http://10.0.0.42:8080"),
            List.of(new GameId.SteamAppId(220L), new GameId.Slug("disco-elysium")),
            8080,
            5);

    DeckSyncConfig reloaded = loader.parse(ConfigInitializer.render(original));

    assertThat(reloaded).isEqualTo(original);
  }

  @Test
  void roundTripsDefaults() {
    DeckSyncConfig reloaded = loader.parse(ConfigInitializer.render(DeckSyncConfig.defaults()));

    assertThat(reloaded).isEqualTo(DeckSyncConfig.defaults());
  }
}
