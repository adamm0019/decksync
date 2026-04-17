package dev.decksync.infrastructure.steam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.decksync.application.ExpansionContext;
import dev.decksync.application.PlaceholderResolver;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtonEnvironmentTest {

  @Test
  void homeIsThePrefixUserDir(@TempDir Path tempDir) {
    Path userDir = tempDir.resolve("pfx/drive_c/users/steamuser");
    ProtonEnvironment env = new ProtonEnvironment(userDir);

    assertThat(env.home()).isEqualTo(userDir);
  }

  @Test
  void userNameIsSteamuser(@TempDir Path tempDir) {
    ProtonEnvironment env = new ProtonEnvironment(tempDir.resolve("pfx/drive_c/users/steamuser"));

    assertThat(env.userName()).isEqualTo("steamuser");
  }

  @Test
  void appdataResolvesInsidePrefix(@TempDir Path tempDir) {
    Path userDir = tempDir.resolve("pfx/drive_c/users/steamuser");
    ProtonEnvironment env = new ProtonEnvironment(userDir);

    assertThat(env.get("APPDATA")).contains(userDir.resolve("AppData/Roaming").toString());
    assertThat(env.get("LOCALAPPDATA")).contains(userDir.resolve("AppData/Local").toString());
  }

  @Test
  void publicAndWindirPointIntoDriveC(@TempDir Path tempDir) {
    Path driveC = tempDir.resolve("pfx/drive_c");
    ProtonEnvironment env = new ProtonEnvironment(driveC.resolve("users/steamuser"));

    assertThat(env.get("PUBLIC")).contains(driveC.resolve("users/Public").toString());
    assertThat(env.get("WINDIR")).contains(driveC.resolve("windows").toString());
    assertThat(env.get("SYSTEMROOT")).contains(driveC.resolve("windows").toString());
  }

  @Test
  void unknownVariableIsEmpty(@TempDir Path tempDir) {
    ProtonEnvironment env = new ProtonEnvironment(tempDir.resolve("pfx/drive_c/users/steamuser"));

    assertThat(env.get("RANDOM_VAR")).isEmpty();
  }

  @Test
  void composesWithWindowsResolverForCrossPlayExpansion(@TempDir Path tempDir) {
    Path userDir = tempDir.resolve("pfx/drive_c/users/steamuser");
    Path installBase = tempDir.resolve("games/EldenRing");
    ProtonEnvironment env = new ProtonEnvironment(userDir);
    PlaceholderResolver.Windows resolver = new PlaceholderResolver.Windows(env);
    ExpansionContext ctx =
        new ExpansionContext("Elden Ring", Optional.empty(), installBase, installBase);

    String expanded =
        resolver.expand("<winAppData>/EldenRing/<game>/saves", ctx).replace('\\', '/');

    assertThat(expanded)
        .isEqualTo(
            (userDir.resolve("AppData/Roaming/EldenRing").toString() + "/" + installBase + "/saves")
                .replace('\\', '/'));
  }

  @Test
  void rejectsRelativeUserDir() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ProtonEnvironment(Path.of("relative/pfx/drive_c/users/steamuser")))
        .withMessageContaining("absolute");
  }

  @Test
  void rejectsNullUserDir() {
    assertThatNullPointerException().isThrownBy(() -> new ProtonEnvironment(null));
  }
}
