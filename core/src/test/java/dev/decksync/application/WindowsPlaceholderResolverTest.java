package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsPlaceholderResolverTest {

  @TempDir Path tempDir;

  private Path home() {
    return tempDir.resolve("home");
  }

  private Path library() {
    return tempDir.resolve("lib");
  }

  private Path install() {
    return library().resolve("game");
  }

  private FakeEnvironment env() {
    return new FakeEnvironment(home(), "alice");
  }

  private ExpansionContext ctx() {
    return new ExpansionContext(
        "Elden Ring", Optional.of("76561198000000001"), library(), install());
  }

  @Test
  void expandsBaseAndGameToInstallDir() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("<base>/save", ctx())).isEqualTo(install() + "/save");
    assertThat(r.expand("<game>/save", ctx())).isEqualTo(install() + "/save");
  }

  @Test
  void expandsRootToLibrary() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("<root>/steamapps", ctx())).isEqualTo(library() + "/steamapps");
  }

  @Test
  void expandsStoreUserId() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("<storeUserId>/remote", ctx())).isEqualTo("76561198000000001/remote");
  }

  @Test
  void throwsWhenStoreUserIdRequiredButAbsent() {
    var r = new PlaceholderResolver.Windows(env());
    var ctxWithoutUser = new ExpansionContext("Elden Ring", Optional.empty(), library(), install());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> r.expand("<storeUserId>", ctxWithoutUser))
        .withMessageContaining("storeUserId");
  }

  @Test
  void expandsHomeAndOsUserName() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("<home>", ctx())).isEqualTo(home().toString());
    assertThat(r.expand("<osUserName>", ctx())).isEqualTo("alice");
  }

  @Test
  void expandsWinAppDataFromEnv() {
    var r = new PlaceholderResolver.Windows(env().set("APPDATA", "X:\\Roaming"));

    assertThat(r.expand("<winAppData>\\Game", ctx())).isEqualTo("X:\\Roaming\\Game");
  }

  @Test
  void winAppDataFallsBackToHome() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("<winAppData>", ctx()))
        .isEqualTo(home().resolve("AppData/Roaming").toString());
  }

  @Test
  void expandsWinLocalAppDataFromEnv() {
    var r = new PlaceholderResolver.Windows(env().set("LOCALAPPDATA", "X:\\Local"));

    assertThat(r.expand("<winLocalAppData>", ctx())).isEqualTo("X:\\Local");
  }

  @Test
  void winLocalAppDataFallsBackToHome() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("<winLocalAppData>", ctx()))
        .isEqualTo(home().resolve("AppData/Local").toString());
  }

  @Test
  void expandsWinDocuments() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("<winDocuments>", ctx())).isEqualTo(home().resolve("Documents").toString());
  }

  @Test
  void expandsWinPublicFromEnv() {
    var r = new PlaceholderResolver.Windows(env().set("PUBLIC", "D:\\SharedPublic"));

    assertThat(r.expand("<winPublic>", ctx())).isEqualTo("D:\\SharedPublic");
  }

  @Test
  void winPublicFallsBackToStandardPath() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("<winPublic>", ctx())).isEqualTo("C:\\Users\\Public");
  }

  @Test
  void expandsWinDirFromEnvWithWindirPreferredOverSystemroot() {
    var r =
        new PlaceholderResolver.Windows(
            env().set("WINDIR", "D:\\Win").set("SYSTEMROOT", "E:\\Sys"));

    assertThat(r.expand("<winDir>", ctx())).isEqualTo("D:\\Win");
  }

  @Test
  void winDirFallsThroughToSystemroot() {
    var r = new PlaceholderResolver.Windows(env().set("SYSTEMROOT", "E:\\Sys"));

    assertThat(r.expand("<winDir>", ctx())).isEqualTo("E:\\Sys");
  }

  @Test
  void winDirFallsBackToStandardPath() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("<winDir>", ctx())).isEqualTo("C:\\Windows");
  }

  @Test
  void throwsOnLinuxOnlyPlaceholder() {
    var r = new PlaceholderResolver.Windows(env());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> r.expand("<xdgConfig>", ctx()))
        .withMessageContaining("xdgConfig");
  }

  @Test
  void throwsOnUnknownPlaceholder() {
    var r = new PlaceholderResolver.Windows(env());

    assertThatIllegalArgumentException()
        .isThrownBy(() -> r.expand("<bogus>", ctx()))
        .withMessageContaining("bogus");
  }

  @Test
  void expandsMultiplePlaceholdersInOneString() {
    var r = new PlaceholderResolver.Windows(env().set("APPDATA", "X:\\Roaming"));

    assertThat(r.expand("<winAppData>\\<game>\\user-<storeUserId>", ctx()))
        .isEqualTo("X:\\Roaming\\" + install() + "\\user-76561198000000001");
  }

  @Test
  void passesThroughStringsWithoutPlaceholders() {
    var r = new PlaceholderResolver.Windows(env());

    assertThat(r.expand("C:\\Games\\foo\\save.bin", ctx())).isEqualTo("C:\\Games\\foo\\save.bin");
  }

  @Test
  void rejectsNullEnv() {
    assertThatNullPointerException().isThrownBy(() -> new PlaceholderResolver.Windows(null));
  }

  @Test
  void rejectsNullArgs() {
    var r = new PlaceholderResolver.Windows(env());

    assertThatNullPointerException().isThrownBy(() -> r.expand(null, ctx()));
    assertThatNullPointerException().isThrownBy(() -> r.expand("<base>", null));
  }
}
