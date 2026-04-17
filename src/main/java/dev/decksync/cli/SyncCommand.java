package dev.decksync.cli;

import dev.decksync.application.GameCatalog;
import dev.decksync.application.GameIdParser;
import dev.decksync.application.PeerClient;
import dev.decksync.application.PeerException;
import dev.decksync.application.PeerFileNotFoundException;
import dev.decksync.application.SyncOutcome;
import dev.decksync.application.SyncService;
import dev.decksync.domain.GameId;
import dev.decksync.domain.SyncAction;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code decksync sync [--game <id>] [--dry-run]} — pulls newer saves from the configured peer,
 * backing up local files before overwriting them. With {@code --game} only that game is synced;
 * otherwise every game both peers agree is installed gets synced in turn. {@code --dry-run} prints
 * the plan without touching the filesystem, which is how Phase 1's success criterion expects
 * operators to preview a sync before trusting it.
 */
@Component
@Command(
    name = "sync",
    mixinStandardHelpOptions = true,
    description = "Sync saves from the configured peer, backing up local files before overwrite.")
public class SyncCommand implements Callable<Integer> {

  private final SyncService syncService;
  private final PeerClient peer;
  private final GameCatalog catalog;
  private final PrintStream out;
  private final PrintStream err;

  @Option(
      names = "--game",
      paramLabel = "<gameId>",
      description = "Sync only this game (Steam appid or slug).")
  private String gameIdRaw;

  @Option(names = "--dry-run", description = "Print the plan without applying any changes.")
  private boolean dryRun;

  @Autowired
  public SyncCommand(SyncService syncService, PeerClient peer, GameCatalog catalog) {
    this(syncService, peer, catalog, System.out, System.err);
  }

  SyncCommand(
      SyncService syncService,
      PeerClient peer,
      GameCatalog catalog,
      PrintStream out,
      PrintStream err) {
    this.syncService = syncService;
    this.peer = peer;
    this.catalog = catalog;
    this.out = out;
    this.err = err;
  }

  void setGameIdRaw(String raw) {
    this.gameIdRaw = raw;
  }

  void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }

  @Override
  public Integer call() {
    List<GameId> games;
    try {
      games = resolveGames();
    } catch (IllegalArgumentException e) {
      err.println("error: " + e.getMessage());
      return 2;
    } catch (PeerException e) {
      err.println("error: peer unreachable: " + e.getMessage());
      return 1;
    }
    if (games.isEmpty()) {
      out.println("No games installed on both peers.");
      return 0;
    }

    int failures = 0;
    for (GameId game : games) {
      try {
        SyncOutcome outcome = syncService.syncGame(game, dryRun);
        printOutcome(game, outcome);
      } catch (PeerFileNotFoundException e) {
        err.println("error: " + format(game) + ": peer has no such file — " + e.getMessage());
        failures++;
      } catch (PeerException e) {
        err.println("error: " + format(game) + ": peer error — " + e.getMessage());
        failures++;
      } catch (RuntimeException e) {
        err.println("error: " + format(game) + ": " + e.getMessage());
        failures++;
      }
    }
    return failures == 0 ? 0 : 1;
  }

  private List<GameId> resolveGames() {
    if (gameIdRaw != null) {
      return List.of(GameIdParser.parse(gameIdRaw));
    }
    Set<GameId> peerGames = new HashSet<>(peer.listGames());
    return catalog.resolveInstalled().keySet().stream()
        .filter(peerGames::contains)
        .sorted(Comparator.comparing(SyncCommand::format))
        .toList();
  }

  private void printOutcome(GameId game, SyncOutcome outcome) {
    String header = dryRun ? "[dry-run] " : "";
    out.println(header + format(game));
    for (SyncAction action : outcome.plan().actions()) {
      out.println("  " + describe(action));
    }
    if (!dryRun) {
      out.println("  applied: " + outcome.appliedPaths().size());
    }
  }

  private static String describe(SyncAction action) {
    return switch (action) {
      case SyncAction.Pull p -> "PULL   " + p.path().path() + " (" + p.remote().size() + " bytes)";
      case SyncAction.Skip s -> "SKIP   " + s.path().path() + " (" + s.reason() + ")";
      case SyncAction.Conflict c -> "CONFLICT " + c.path().path();
    };
  }

  private static String format(GameId id) {
    return switch (id) {
      case GameId.SteamAppId s -> "steam:" + s.appid();
      case GameId.Slug s -> s.value();
    };
  }
}
