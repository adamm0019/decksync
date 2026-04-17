package dev.decksync.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.application.GameCatalog;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListGamesCommandTest {

  @Test
  void printsEmptyMessageWhenNoGamesResolved() {
    String output = runWith(Map.of());
    assertThat(output).isEqualTo("No installed games resolved." + System.lineSeparator());
  }

  @Test
  void printsSingleSteamAppIdRow(@TempDir Path tmp) {
    Path savePath = tmp.resolve("EldenRing");
    Map<GameId, AbsolutePath> games = new LinkedHashMap<>();
    games.put(new GameId.SteamAppId(1245620L), new AbsolutePath(savePath));
    String output = runWith(games);
    assertThat(output).contains("steam:1245620");
    assertThat(output).contains(savePath.toString());
  }

  @Test
  void sortsRowsByFormattedIdAndAlignsPaths(@TempDir Path tmp) {
    Path portal = tmp.resolve("portal");
    Path elden = tmp.resolve("elden-ring");
    Path halfLife = tmp.resolve("half-life");

    Map<GameId, AbsolutePath> games = new LinkedHashMap<>();
    games.put(new GameId.SteamAppId(400L), new AbsolutePath(portal));
    games.put(new GameId.SteamAppId(1245620L), new AbsolutePath(elden));
    games.put(new GameId.SteamAppId(70L), new AbsolutePath(halfLife));

    String output = runWith(games);
    String[] lines = output.split("\\R");

    assertThat(lines).hasSize(3);
    assertThat(lines[0]).startsWith("steam:1245620");
    assertThat(lines[1]).startsWith("steam:400 ");
    assertThat(lines[2]).startsWith("steam:70 ");
    int pathColumn = lines[0].indexOf(elden.toString());
    assertThat(lines[1].indexOf(portal.toString())).isEqualTo(pathColumn);
    assertThat(lines[2].indexOf(halfLife.toString())).isEqualTo(pathColumn);
  }

  @Test
  void printsSlugRow(@TempDir Path tmp) {
    Path savePath = tmp.resolve("sv");
    Map<GameId, AbsolutePath> games = new LinkedHashMap<>();
    games.put(new GameId.Slug("stardew-valley"), new AbsolutePath(savePath));
    String output = runWith(games);
    assertThat(output).startsWith("stardew-valley");
    assertThat(output).contains(savePath.toString());
  }

  private static String runWith(Map<GameId, AbsolutePath> games) {
    Map<GameId, AbsolutePath> snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(games));
    GameCatalog catalog = () -> snapshot;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
    new ListGamesCommand(catalog, out).run();
    return buffer.toString(StandardCharsets.UTF_8);
  }
}
