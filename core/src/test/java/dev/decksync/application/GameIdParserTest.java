package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.decksync.domain.GameId;
import org.junit.jupiter.api.Test;

class GameIdParserTest {

  @Test
  void parsesBareNumberAsSteamAppId() {
    assertThat(GameIdParser.parse("1245620")).isEqualTo(new GameId.SteamAppId(1245620L));
  }

  @Test
  void parsesSteamPrefixAsSteamAppId() {
    assertThat(GameIdParser.parse("steam:1245620")).isEqualTo(new GameId.SteamAppId(1245620L));
  }

  @Test
  void parsesKebabSlug() {
    assertThat(GameIdParser.parse("elden-ring")).isEqualTo(new GameId.Slug("elden-ring"));
  }

  @Test
  void trimsSurroundingWhitespace() {
    assertThat(GameIdParser.parse("  1245620 ")).isEqualTo(new GameId.SteamAppId(1245620L));
  }

  @Test
  void rejectsBlank() {
    assertThatThrownBy(() -> GameIdParser.parse("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GameIdParser.parse("   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> GameIdParser.parse(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSteamPrefixWithNonNumeric() {
    assertThatThrownBy(() -> GameIdParser.parse("steam:not-a-number"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSlugWithUppercase() {
    assertThatThrownBy(() -> GameIdParser.parse("EldenRing"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
