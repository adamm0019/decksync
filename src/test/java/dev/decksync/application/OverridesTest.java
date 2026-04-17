package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.decksync.domain.GameId;
import dev.decksync.domain.Platform;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OverridesTest {

  @Test
  void forGameReturnsPlatformOverrideValue() {
    GameId id = new GameId.Slug("elden-ring");
    Overrides overrides =
        new Overrides(
            Map.of(id, new PlatformOverride(Optional.of("D:\\Saves"), Optional.of("/saves"))));

    assertThat(overrides.forGame(id, new Platform.Windows())).contains("D:\\Saves");
    assertThat(overrides.forGame(id, new Platform.Linux())).contains("/saves");
  }

  @Test
  void forGameReturnsEmptyWhenGameUnknown() {
    assertThat(Overrides.EMPTY.forGame(new GameId.Slug("unknown"), new Platform.Windows()))
        .isEmpty();
  }

  @Test
  void byGameIsDefensivelyCopied() {
    GameId id = new GameId.Slug("game-one");
    var mutable = new HashMap<GameId, PlatformOverride>();
    mutable.put(id, new PlatformOverride(Optional.of("/a"), Optional.empty()));

    Overrides overrides = new Overrides(mutable);
    mutable.put(
        new GameId.Slug("game-two"), new PlatformOverride(Optional.of("/b"), Optional.empty()));

    assertThat(overrides.byGame()).containsOnlyKeys(id);
  }

  @Test
  void rejectsNullArgs() {
    assertThatNullPointerException().isThrownBy(() -> new Overrides(null));
    assertThatNullPointerException()
        .isThrownBy(() -> Overrides.EMPTY.forGame(null, new Platform.Windows()));
  }
}
