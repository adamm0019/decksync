package dev.decksync.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class GameIdTest {

  @Test
  void steamAppIdRejectsZeroAndNegative() {
    assertThatIllegalArgumentException().isThrownBy(() -> new GameId.SteamAppId(0));
    assertThatIllegalArgumentException().isThrownBy(() -> new GameId.SteamAppId(-1));
  }

  @Test
  void steamAppIdsWithSameValueAreEqual() {
    assertThat(new GameId.SteamAppId(1245620)).isEqualTo(new GameId.SteamAppId(1245620));
    assertThat(new GameId.SteamAppId(1245620)).isNotEqualTo(new GameId.SteamAppId(1091500));
  }

  @Test
  void slugAcceptsKebabCase() {
    assertThat(new GameId.Slug("elden-ring").value()).isEqualTo("elden-ring");
    assertThat(new GameId.Slug("cyberpunk-2077").value()).isEqualTo("cyberpunk-2077");
    assertThat(new GameId.Slug("hades").value()).isEqualTo("hades");
  }

  @Test
  void slugRejectsUppercase() {
    assertThatIllegalArgumentException().isThrownBy(() -> new GameId.Slug("Elden-Ring"));
  }

  @Test
  void slugRejectsSpaces() {
    assertThatIllegalArgumentException().isThrownBy(() -> new GameId.Slug("elden ring"));
  }

  @Test
  void slugRejectsLeadingHyphen() {
    assertThatIllegalArgumentException().isThrownBy(() -> new GameId.Slug("-elden-ring"));
  }

  @Test
  void slugRejectsNullAndEmpty() {
    assertThatIllegalArgumentException().isThrownBy(() -> new GameId.Slug(null));
    assertThatIllegalArgumentException().isThrownBy(() -> new GameId.Slug(""));
  }

  @Test
  void steamAppIdAndSlugAreDistinctVariants() {
    GameId id = new GameId.SteamAppId(1245620);

    String kind =
        switch (id) {
          case GameId.SteamAppId s -> "steam";
          case GameId.Slug s -> "slug";
        };

    assertThat(kind).isEqualTo("steam");
  }
}
