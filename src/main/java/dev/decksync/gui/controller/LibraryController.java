package dev.decksync.gui.controller;

import dev.decksync.application.BackupService;
import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.ManifestEntry;
import dev.decksync.application.ManifestIndex;
import dev.decksync.application.PeerReachability;
import dev.decksync.application.PeerStatus;
import dev.decksync.application.SyncService;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.domain.SyncAction;
import dev.decksync.domain.SyncPlan;
import dev.decksync.gui.util.RelativeTime;
import dev.decksync.gui.view.CardStatus;
import dev.decksync.gui.view.GameCardView;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Library destination: one card per game with live sync status. On init we resolve the game list
 * from catalog + config, kick off a peer reachability probe plus per-game dry-runs on virtual
 * threads, and update each card in-place as results come in. Background → JavaFX thread marshalling
 * goes through {@link Platform#runLater}.
 */
@Component
public class LibraryController {

  private static final Logger log = LoggerFactory.getLogger(LibraryController.class);

  private final GameCatalog catalog;
  private final DeckSyncConfig config;
  private final PeerReachability reachability;
  private final SyncService syncService;
  private final BackupService backupService;
  private final ManifestIndex manifestIndex;
  private final Clock clock;
  private final MainController mainController;

  @FXML private VBox emptyState;
  @FXML private FlowPane cardGrid;
  @FXML private ComboBox<Filter> filterCombo;
  @FXML private Label countLabel;

  private final Map<GameId, GameCardView> cards = new LinkedHashMap<>();

  public LibraryController(
      GameCatalog catalog,
      DeckSyncConfig config,
      PeerReachability reachability,
      SyncService syncService,
      BackupService backupService,
      ManifestIndex manifestIndex,
      Clock clock,
      MainController mainController) {
    this.catalog = catalog;
    this.config = config;
    this.reachability = reachability;
    this.syncService = syncService;
    this.backupService = backupService;
    this.manifestIndex = manifestIndex;
    this.clock = clock;
    this.mainController = mainController;
  }

  @FXML
  void initialize() {
    // Controllers are Spring singletons — clear any state left from a previous nav-in before
    // wiring the fresh FXML nodes.
    cards.clear();
    cardGrid.getChildren().clear();

    filterCombo.setItems(FXCollections.observableArrayList(Filter.values()));
    filterCombo.getSelectionModel().select(Filter.ALL);
    filterCombo.valueProperty().addListener((obs, old, now) -> applyFilter(now));

    Map<GameId, AbsolutePath> installed = catalog.resolveInstalled();
    List<GameId> gameIds = resolveGameList(installed);
    if (gameIds.isEmpty()) {
      emptyState.setVisible(true);
      emptyState.setManaged(true);
      cardGrid.setVisible(false);
      cardGrid.setManaged(false);
      countLabel.setText("");
      return;
    }

    emptyState.setVisible(false);
    emptyState.setManaged(false);
    cardGrid.setVisible(true);
    cardGrid.setManaged(true);

    for (GameId id : gameIds) {
      GameCardView card = new GameCardView(id, displayName(id));
      card.update(CardStatus.LOADING, "");
      card.setOnSyncNow(gameId -> mainController.openPreview(List.of(gameId)));
      cards.put(id, card);
      cardGrid.getChildren().add(card);
    }
    countLabel.setText(gameIds.size() + " game" + (gameIds.size() == 1 ? "" : "s"));

    Thread.ofVirtual().name("library-refresh").start(() -> refreshAll(installed));
  }

  private List<GameId> resolveGameList(Map<GameId, AbsolutePath> installed) {
    List<GameId> configured = config.watchedGames();
    List<GameId> all = new ArrayList<>();
    if (configured.isEmpty()) {
      all.addAll(installed.keySet());
    } else {
      all.addAll(configured);
    }
    all.sort(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER));
    return all;
  }

  private void refreshAll(Map<GameId, AbsolutePath> installed) {
    PeerStatus status = reachability.probe();
    if (status instanceof PeerStatus.Unreachable unreach) {
      log.info("library refresh: peer unreachable ({})", unreach.reason());
      for (GameCardView card : cards.values()) {
        Platform.runLater(() -> card.update(CardStatus.OFFLINE, "Peer not reachable"));
      }
      return;
    }
    for (Map.Entry<GameId, GameCardView> entry : cards.entrySet()) {
      GameId id = entry.getKey();
      GameCardView card = entry.getValue();
      Thread.ofVirtual()
          .name("library-game-" + id)
          .start(() -> refreshOne(id, card, installed.containsKey(id)));
    }
  }

  private void refreshOne(GameId id, GameCardView card, boolean installedHere) {
    String time = formatLatest(id);
    if (!installedHere) {
      Platform.runLater(() -> card.update(CardStatus.NOT_INSTALLED, "No local save path"));
      return;
    }
    try {
      SyncPlan plan = syncService.syncGame(id, true).plan();
      CardStatus derived = deriveStatus(plan);
      Platform.runLater(() -> card.update(derived, time));
    } catch (RuntimeException e) {
      log.warn("dry-run failed for {}: {}", id, e.getMessage());
      Platform.runLater(() -> card.update(CardStatus.ERROR, "Sync check failed"));
    }
  }

  private String formatLatest(GameId id) {
    try {
      Optional<Instant> last = backupService.latestSnapshot(id);
      return last.map(t -> RelativeTime.format(t, clock.instant())).orElse("Never synced");
    } catch (Exception e) {
      return "";
    }
  }

  // CONFLICT > NEWER_PEER > NEWER_HERE > IN_SYNC. An empty plan (no files either side) reads as
  // "in sync" — there's nothing to disagree about.
  private static CardStatus deriveStatus(SyncPlan plan) {
    boolean hasConflict = false;
    boolean hasPull = false;
    boolean hasLocalNewer = false;
    for (SyncAction action : plan.actions()) {
      if (action instanceof SyncAction.Conflict) {
        hasConflict = true;
      } else if (action instanceof SyncAction.Pull) {
        hasPull = true;
      } else if (action instanceof SyncAction.Skip skip) {
        SyncAction.Skip.Reason reason = skip.reason();
        if (reason == SyncAction.Skip.Reason.LOCAL_NEWER
            || reason == SyncAction.Skip.Reason.LOCAL_ONLY) {
          hasLocalNewer = true;
        }
      }
    }
    if (hasConflict) return CardStatus.CONFLICT;
    if (hasPull) return CardStatus.NEWER_PEER;
    if (hasLocalNewer) return CardStatus.NEWER_HERE;
    return CardStatus.IN_SYNC;
  }

  private void applyFilter(Filter filter) {
    for (Node node : cardGrid.getChildren()) {
      if (node instanceof GameCardView card) {
        boolean show = filter.matches(card.currentStatus());
        card.setVisible(show);
        card.setManaged(show);
      }
    }
  }

  private String displayName(GameId id) {
    return switch (id) {
      case GameId.SteamAppId s -> {
        ManifestEntry entry = manifestIndex.bySteamAppId().get(s.appid());
        yield entry != null ? entry.name() : "steam:" + s.appid();
      }
      case GameId.Slug s -> s.value();
    };
  }

  enum Filter {
    ALL("All") {
      @Override
      boolean matches(CardStatus status) {
        return true;
      }
    },
    NEEDS_SYNC("Needs sync") {
      @Override
      boolean matches(CardStatus status) {
        return status == CardStatus.NEWER_PEER
            || status == CardStatus.NEWER_HERE
            || status == CardStatus.CONFLICT;
      }
    },
    IN_SYNC("In sync") {
      @Override
      boolean matches(CardStatus status) {
        return status == CardStatus.IN_SYNC;
      }
    },
    ISSUES("Issues") {
      @Override
      boolean matches(CardStatus status) {
        return status == CardStatus.CONFLICT
            || status == CardStatus.ERROR
            || status == CardStatus.OFFLINE;
      }
    };

    private final String label;

    Filter(String label) {
      this.label = label;
    }

    abstract boolean matches(CardStatus status);

    @Override
    public String toString() {
      return label;
    }
  }
}
