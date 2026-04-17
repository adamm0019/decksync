package dev.decksync.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.Environment;
import dev.decksync.application.GameCatalog;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SetupCommandTest {

  @Test
  void happyPath_promptsPeerPortRetentionGames_andWritesConfig(@TempDir Path tmp) throws Exception {
    // stdin: peer URL, port (blank → default), retention (blank → default), games (all)
    String inputs =
        String.join(
                "\n",
                "http://192.168.1.11:47824", // peer
                "", // port default
                "", // retention default
                "all") // games
            + "\n";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SetupCommand cmd =
        new SetupCommand(
            stubCatalog(tmp, new GameId.SteamAppId(1245620L), new GameId.Slug("stardew-valley")),
            envRootedAt(tmp),
            new BufferedReader(new StringReader(inputs)),
            new PrintStream(out, true, StandardCharsets.UTF_8));
    cmd.skipOsSetup = true;

    cmd.run();

    Path configFile = tmp.resolve(".decksync/config.yml");
    String written = Files.readString(configFile);
    assertThat(written).contains("url: http://192.168.1.11:47824");
    assertThat(written).contains("port: " + DeckSyncConfig.DEFAULT_PORT);
    assertThat(written).contains("retention: " + DeckSyncConfig.DEFAULT_RETENTION);
    assertThat(written).contains("games: []");
  }

  @Test
  void bareIpIsWrappedToHttpUrlWithDefaultPort(@TempDir Path tmp) throws Exception {
    String inputs = String.join("\n", "10.0.0.5", "", "", "all") + "\n";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SetupCommand cmd =
        new SetupCommand(
            stubCatalog(tmp),
            envRootedAt(tmp),
            new BufferedReader(new StringReader(inputs)),
            new PrintStream(out, true, StandardCharsets.UTF_8));
    cmd.skipOsSetup = true;

    cmd.run();

    String written = Files.readString(tmp.resolve(".decksync/config.yml"));
    assertThat(written).contains("url: http://10.0.0.5:" + DeckSyncConfig.DEFAULT_PORT);
  }

  @Test
  void numericGameSelectionPicksFromResolvedCatalog(@TempDir Path tmp) throws Exception {
    // Games are sorted alphabetically by their rendered form, so:
    //   [1] stardew-valley
    //   [2] steam:1245620
    String inputs = String.join("\n", "http://192.168.1.11:47824", "", "", "1") + "\n";
    SetupCommand cmd =
        new SetupCommand(
            stubCatalog(tmp, new GameId.SteamAppId(1245620L), new GameId.Slug("stardew-valley")),
            envRootedAt(tmp),
            new BufferedReader(new StringReader(inputs)),
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    cmd.skipOsSetup = true;

    cmd.run();

    String written = Files.readString(tmp.resolve(".decksync/config.yml"));
    assertThat(written).contains("- stardew-valley");
    assertThat(written).doesNotContain("- steam:1245620");
  }

  @Test
  void invalidUrlRepromptsUntilValid(@TempDir Path tmp) throws Exception {
    String inputs =
        String.join(
                "\n",
                "not a url", // rejected
                "http://example.org:47824", // accepted
                "",
                "",
                "all")
            + "\n";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SetupCommand cmd =
        new SetupCommand(
            stubCatalog(tmp),
            envRootedAt(tmp),
            new BufferedReader(new StringReader(inputs)),
            new PrintStream(out, true, StandardCharsets.UTF_8));
    cmd.skipOsSetup = true;

    cmd.run();

    assertThat(out.toString(StandardCharsets.UTF_8)).contains("not a valid URL");
    String written = Files.readString(tmp.resolve(".decksync/config.yml"));
    assertThat(written).contains("url: http://example.org:47824");
  }

  @Test
  void existingConfigIsPreservedWhenForceNotSet(@TempDir Path tmp) throws Exception {
    Path configFile = tmp.resolve(".decksync/config.yml");
    Files.createDirectories(configFile.getParent());
    String original = "peer:\n  url: http://original.local:47824\n";
    Files.writeString(configFile, original);

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    SetupCommand cmd =
        new SetupCommand(
            stubCatalog(tmp),
            envRootedAt(tmp),
            new BufferedReader(new StringReader("")),
            new PrintStream(out, true, StandardCharsets.UTF_8));
    cmd.skipOsSetup = true;

    cmd.run();

    assertThat(Files.readString(configFile)).isEqualTo(original);
    assertThat(out.toString(StandardCharsets.UTF_8)).contains("--force");
  }

  private static GameCatalog stubCatalog(Path root, GameId... ids) {
    Map<GameId, AbsolutePath> map = new LinkedHashMap<>();
    for (GameId id : ids) {
      map.put(id, new AbsolutePath(root));
    }
    return () -> map;
  }

  private static Environment envRootedAt(Path root) {
    return new Environment() {
      @Override
      public Optional<String> get(String name) {
        return Optional.empty();
      }

      @Override
      public Path home() {
        return root;
      }

      @Override
      public String userName() {
        return "tester";
      }
    };
  }
}
