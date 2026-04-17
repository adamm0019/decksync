package dev.decksync.cli;

import dev.decksync.application.BackupService;
import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.PeerReachability;
import dev.decksync.application.PeerStatus;
import dev.decksync.domain.GameId;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * {@code decksync status} — prints peer reachability plus the last-sync time per game. Reads last
 * sync from the history directory (the most recent timestamped snapshot is, by construction, the
 * last time we overwrote a local file), so no separate bookkeeping file is needed to answer "when
 * did I last sync X?".
 */
@Component
@Command(
    name = "status",
    mixinStandardHelpOptions = true,
    description = "Show peer reachability and last sync time per game.")
public class StatusCommand implements Runnable {

  private static final DateTimeFormatter DISPLAY =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

  private final PeerReachability reachability;
  private final BackupService backupService;
  private final GameCatalog catalog;
  private final DeckSyncConfig config;
  private final PrintStream out;

  @Autowired
  public StatusCommand(
      PeerReachability reachability,
      BackupService backupService,
      GameCatalog catalog,
      DeckSyncConfig config) {
    this(reachability, backupService, catalog, config, System.out);
  }

  StatusCommand(
      PeerReachability reachability,
      BackupService backupService,
      GameCatalog catalog,
      DeckSyncConfig config,
      PrintStream out) {
    this.reachability = reachability;
    this.backupService = backupService;
    this.catalog = catalog;
    this.config = config;
    this.out = out;
  }

  @Override
  public void run() {
    out.println("peer: " + config.peerUrl());
    PeerStatus status = reachability.probe();
    out.println("      " + describe(status));
    out.println();

    List<GameId> games =
        catalog.resolveInstalled().keySet().stream()
            .sorted(Comparator.comparing(StatusCommand::format))
            .toList();
    if (games.isEmpty()) {
      out.println("No installed games resolved on this host.");
      return;
    }
    int idWidth = games.stream().mapToInt(g -> format(g).length()).max().orElse(0);
    for (GameId game : games) {
      Optional<Instant> last = safeLatest(game);
      String when = last.map(StatusCommand::formatWhen).orElse("never");
      out.printf("%-" + idWidth + "s  last sync: %s%n", format(game), when);
    }
  }

  private Optional<Instant> safeLatest(GameId game) {
    try {
      return backupService.latestSnapshot(game);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read history for " + game, e);
    }
  }

  private static String describe(PeerStatus status) {
    return switch (status) {
      case PeerStatus.Reachable r -> "reachable (" + r.rtt().toMillis() + " ms)";
      case PeerStatus.Unreachable u -> "UNREACHABLE — " + u.reason();
    };
  }

  private static String formatWhen(Instant instant) {
    return ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DISPLAY);
  }

  private static String format(GameId id) {
    return switch (id) {
      case GameId.SteamAppId s -> "steam:" + s.appid();
      case GameId.Slug s -> s.value();
    };
  }
}
