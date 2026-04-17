package dev.decksync.cli;

import dev.decksync.application.GameCatalog;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * {@code decksync list-games} — prints the set of installed games the daemon currently sees, along
 * with the resolved absolute save directory on this host. Primary sanity check during M2: running
 * it on a Windows PC and on a SteamOS Deck against the same Steam library should show the same
 * {@link GameId}s with platform-specific absolute paths on either side.
 */
@Component
@Command(
    name = "list-games",
    mixinStandardHelpOptions = true,
    description = "List installed games with their resolved save directories.")
public class ListGamesCommand implements Runnable {

  private final GameCatalog catalog;
  private final PrintStream out;

  public ListGamesCommand(GameCatalog catalog) {
    this(catalog, System.out);
  }

  ListGamesCommand(GameCatalog catalog, PrintStream out) {
    this.catalog = catalog;
    this.out = out;
  }

  @Override
  public void run() {
    Map<GameId, AbsolutePath> games = catalog.resolveInstalled();
    if (games.isEmpty()) {
      out.println("No installed games resolved.");
      return;
    }
    List<Map.Entry<GameId, AbsolutePath>> rows = new ArrayList<>(games.entrySet());
    rows.sort(Comparator.comparing(e -> formatId(e.getKey())));
    int idWidth = rows.stream().mapToInt(e -> formatId(e.getKey()).length()).max().orElse(0);
    for (Map.Entry<GameId, AbsolutePath> row : rows) {
      out.printf("%-" + idWidth + "s  %s%n", formatId(row.getKey()), row.getValue().path());
    }
  }

  private static String formatId(GameId id) {
    return switch (id) {
      case GameId.SteamAppId s -> "steam:" + s.appid();
      case GameId.Slug s -> s.value();
    };
  }
}
