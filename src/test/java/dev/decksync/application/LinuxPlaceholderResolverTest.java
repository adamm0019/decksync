package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinuxPlaceholderResolverTest {

  @TempDir Path tempDir;

  private Path home() {
    return tempDir.resolve("home");
  }

  private Path library() {
    return tempDir.resolve("steam");
  }

  private Path install() {
    return library().resolve("game");
  }

  private FakeEnvironment env() {
    return new FakeEnvironment(home(), "deck");
  }

  private ExpansionContext ctx() {
    return new ExpansionContext(
        "Elden Ring", Optional.of("76561198000000001"), library(), install());
  }

  @Test
  void expandsBaseAndRoot() {
    var r = new PlaceholderResolver.Linux(env());

    assertThat(r.expand("<base>/save", ctx())).isEqualTo(install() + "/save");
    assertThat(r.expand("<root>", ctx())).isEqualTo(library().toString());
  }

  @Test
  void expandsHomeAndOsUserName() {
    var r = new PlaceholderResolver.Linux(env());

    assertThat(r.expand("<home>", ctx())).isEqualTo(home().toString());
    assertThat(r.expand("<osUserName>", ctx())).isEqualTo("deck");
  }

  @Test
  void expandsStoreUserId() {
    var r = new PlaceholderResolver.Linux(env());

    assertThat(r.expand("userdata/<storeUserId>", ctx())).isEqualTo("userdata/76561198000000001");
  }

  @Test
  void expandsXdgConfigFromEnv() {
    var r = new PlaceholderResolver.Linux(env().set("XDG_CONFIG_HOME", "/var/config"));

    assertThat(r.expand("<xdgConfig>/game", ctx())).isEqualTo("/var/config/game");
  }

  @Test
  void xdgConfigFallsBackToHomeDotConfig() {
    var r = new PlaceholderResolver.Linux(env());

    assertThat(r.expand("<xdgConfig>", ctx())).isEqualTo(home().resolve(".config").toString());
  }

  @Test
  void expandsXdgDataFromEnv() {
    var r = new PlaceholderResolver.Linux(env().set("XDG_DATA_HOME", "/var/data"));

    assertThat(r.expand("<xdgData>/game", ctx())).isEqualTo("/var/data/game");
  }

  @Test
  void xdgDataFallsBackToLocalShare() {
    var r = new PlaceholderResolver.Linux(env());

    assertThat(r.expand("<xdgData>", ctx())).isEqualTo(home().resolve(".local/share").toString());
  }

  @Test
  void throwsOnWindowsPlaceholder() {
    var r = new PlaceholderResolver.Linux(env());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> r.expand("<winAppData>", ctx()))
        .withMessageContaining("winAppData");
  }

  @Test
  void throwsOnUnknownPlaceholder() {
    var r = new PlaceholderResolver.Linux(env());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> r.expand("<nope>", ctx()))
        .withMessageContaining("nope");
  }

  @Test
  void rejectsNullEnv() {
    assertThatNullPointerException().isThrownBy(() -> new PlaceholderResolver.Linux(null));
  }

  @Test
  void rejectsNullArgs() {
    var r = new PlaceholderResolver.Linux(env());

    assertThatNullPointerException().isThrownBy(() -> r.expand(null, ctx()));
    assertThatNullPointerException().isThrownBy(() -> r.expand("<base>", null));
  }
}
