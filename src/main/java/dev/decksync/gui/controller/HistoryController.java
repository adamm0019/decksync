package dev.decksync.gui.controller;

import dev.decksync.application.BackupService;
import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.ManifestEntry;
import dev.decksync.application.ManifestIndex;
import dev.decksync.application.RestoreService;
import dev.decksync.domain.GameId;
import dev.decksync.gui.util.RelativeTime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * History destination: a two-pane view of versioned backups. Left sidebar lists every game that has
 * at least one snapshot on disk; right pane shows that game's snapshots newest-first with a
 * one-click rollback on every row. Selecting a different game swaps the right pane without
 * rebuilding the sidebar.
 *
 * <p>Restore runs on a virtual thread via {@link RestoreService#restore}. Because a restore itself
 * produces fresh backups (the pre-restore safety copy), the snapshot list is reloaded once the
 * worker finishes so the new row appears at the top.
 */
@Component
public class HistoryController {

  private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

  private static final DateTimeFormatter ABSOLUTE =
      DateTimeFormatter.ofPattern("MMM d · HH:mm").withZone(ZoneId.systemDefault());

  private final BackupService backupService;
  private final RestoreService restoreService;
  private final GameCatalog catalog;
  private final DeckSyncConfig config;
  private final ManifestIndex manifestIndex;
  private final Clock clock;

  @FXML private Label countLabel;
  @FXML private HBox contentPane;
  @FXML private ListView<GameEntry> gamesList;
  @FXML private VBox detailPane;
  @FXML private VBox detailHeader;
  @FXML private Label detailTitle;
  @FXML private Label detailSubtitle;
  @FXML private VBox snapshotsList;
  @FXML private VBox detailEmpty;
  @FXML private VBox emptyState;

  private GameId selectedGame;

  public HistoryController(
      BackupService backupService,
      RestoreService restoreService,
      GameCatalog catalog,
      DeckSyncConfig config,
      ManifestIndex manifestIndex,
      Clock clock) {
    this.backupService = backupService;
    this.restoreService = restoreService;
    this.catalog = catalog;
    this.config = config;
    this.manifestIndex = manifestIndex;
    this.clock = clock;
  }

  @FXML
  void initialize() {
    gamesList.setCellFactory(list -> new GameCell());
    gamesList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, now) -> onGameSelected(now));

    showEmpty(true);
    Thread.ofVirtual().name("history-load").start(this::loadAll);
  }

  private void loadAll() {
    List<GameEntry> entries = new ArrayList<>();
    List<GameId> candidates = resolveCandidateGames();
    for (GameId id : candidates) {
      try {
        List<Instant> snapshots = backupService.listSnapshots(id);
        if (!snapshots.isEmpty()) {
          entries.add(new GameEntry(id, displayName(id), snapshots));
        }
      } catch (IOException e) {
        log.warn("listSnapshots failed for {}: {}", id, e.getMessage());
      }
    }
    entries.sort(Comparator.comparing(GameEntry::displayName, String.CASE_INSENSITIVE_ORDER));
    Platform.runLater(() -> renderGames(entries));
  }

  private List<GameId> resolveCandidateGames() {
    Map<GameId, ?> installed = catalog.resolveInstalled();
    Map<GameId, Boolean> seen = new LinkedHashMap<>();
    for (GameId id : config.watchedGames()) {
      seen.put(id, true);
    }
    for (GameId id : installed.keySet()) {
      seen.putIfAbsent(id, true);
    }
    return new ArrayList<>(seen.keySet());
  }

  private void renderGames(List<GameEntry> entries) {
    if (entries.isEmpty()) {
      showEmpty(true);
      countLabel.setText("");
      return;
    }
    showEmpty(false);
    ObservableList<GameEntry> items = FXCollections.observableArrayList(entries);
    gamesList.setItems(items);
    long totalSnapshots = entries.stream().mapToLong(e -> e.snapshots().size()).sum();
    countLabel.setText(
        entries.size()
            + " game"
            + (entries.size() == 1 ? "" : "s")
            + " · "
            + totalSnapshots
            + " snapshot"
            + (totalSnapshots == 1 ? "" : "s"));
    gamesList.getSelectionModel().selectFirst();
  }

  private void showEmpty(boolean empty) {
    emptyState.setVisible(empty);
    emptyState.setManaged(empty);
    contentPane.setVisible(!empty);
    contentPane.setManaged(!empty);
  }

  private void showDetailEmpty(boolean empty) {
    detailEmpty.setVisible(empty);
    detailEmpty.setManaged(empty);
    detailHeader.setVisible(!empty);
    detailHeader.setManaged(!empty);
  }

  private void onGameSelected(GameEntry entry) {
    if (entry == null) {
      selectedGame = null;
      showDetailEmpty(true);
      snapshotsList.getChildren().clear();
      return;
    }
    selectedGame = entry.gameId();
    showDetailEmpty(false);
    detailTitle.setText(entry.displayName());
    int n = entry.snapshots().size();
    detailSubtitle.setText(n + " snapshot" + (n == 1 ? "" : "s") + " on disk");
    renderSnapshots(entry);
  }

  private void renderSnapshots(GameEntry entry) {
    snapshotsList.getChildren().clear();
    Instant now = clock.instant();
    boolean first = true;
    for (Instant snap : entry.snapshots()) {
      SnapshotRow row = new SnapshotRow(entry.gameId(), snap, first, now);
      snapshotsList.getChildren().add(row);
      first = false;
    }
  }

  private void confirmAndRestore(GameId game, Instant snapshot, SnapshotRow row) {
    Alert alert = new Alert(AlertType.CONFIRMATION);
    alert.setTitle("Restore snapshot");
    alert.setHeaderText("Restore " + displayName(game) + " to " + ABSOLUTE.format(snapshot) + "?");
    alert.setContentText(
        "Your current save is backed up before the restore runs — you can undo this from History.");
    alert.initOwner(detailPane.getScene().getWindow());
    alert
        .showAndWait()
        .filter(b -> b == ButtonType.OK)
        .ifPresent(b -> runRestore(game, snapshot, row));
  }

  private void runRestore(GameId game, Instant snapshot, SnapshotRow row) {
    row.setRestoring();
    Thread.ofVirtual()
        .name("history-restore")
        .start(
            () -> {
              try {
                int n = restoreService.restore(game, snapshot).size();
                log.info("restored {} files for {} from {}", n, game, snapshot);
                Platform.runLater(() -> onRestoreDone(game));
              } catch (RuntimeException e) {
                log.warn("restore failed for {} {}: {}", game, snapshot, e.getMessage());
                Platform.runLater(() -> row.setFailure(e.getMessage()));
              }
            });
  }

  private void onRestoreDone(GameId game) {
    Thread.ofVirtual()
        .name("history-refresh")
        .start(
            () -> {
              try {
                List<Instant> fresh = backupService.listSnapshots(game);
                Platform.runLater(() -> refreshSelected(game, fresh));
              } catch (IOException e) {
                log.warn("reload after restore failed for {}: {}", game, e.getMessage());
              }
            });
  }

  private void refreshSelected(GameId game, List<Instant> snapshots) {
    ObservableList<GameEntry> items = gamesList.getItems();
    if (items == null) {
      return;
    }
    for (int i = 0; i < items.size(); i++) {
      GameEntry entry = items.get(i);
      if (entry.gameId().equals(game)) {
        GameEntry updated = new GameEntry(game, entry.displayName(), snapshots);
        items.set(i, updated);
        if (game.equals(selectedGame)) {
          onGameSelected(updated);
        }
        return;
      }
    }
  }

  private int countFiles(GameId game, Instant snapshot) {
    Path dir = backupService.snapshotDir(game, snapshot);
    if (!Files.isDirectory(dir)) {
      return 0;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      return (int) walk.filter(Files::isRegularFile).count();
    } catch (IOException e) {
      return 0;
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

  private record GameEntry(GameId gameId, String displayName, List<Instant> snapshots) {}

  private static final class GameCell extends ListCell<GameEntry> {
    @Override
    protected void updateItem(GameEntry item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        return;
      }
      Label name = new Label(item.displayName());
      name.getStyleClass().add("history-game-name");
      int n = item.snapshots().size();
      Label count = new Label(n + " snapshot" + (n == 1 ? "" : "s"));
      count.getStyleClass().add("history-game-count");
      VBox box = new VBox(2, name, count);
      box.getStyleClass().add("history-game-cell");
      setText(null);
      setGraphic(box);
    }
  }

  private final class SnapshotRow extends HBox {
    private final Button restoreButton = new Button("Restore");
    private final Label statusLabel = new Label();

    SnapshotRow(GameId gameId, Instant snapshot, boolean isLatest, Instant now) {
      getStyleClass().add("snapshot-row");
      if (isLatest) {
        getStyleClass().add("latest");
      }
      setSpacing(12);

      Label dot = new Label("●");
      dot.getStyleClass().add("snapshot-dot");

      Label relative = new Label(RelativeTime.format(snapshot, now));
      relative.getStyleClass().add("snapshot-relative");
      Label absolute = new Label(ABSOLUTE.format(snapshot));
      absolute.getStyleClass().add("snapshot-absolute");
      VBox when = new VBox(1, relative, absolute);

      int files = countFiles(gameId, snapshot);
      Label size = new Label(files + " file" + (files == 1 ? "" : "s"));
      size.getStyleClass().add("snapshot-meta");

      Region spacer = new Region();
      HBox.setHgrow(spacer, Priority.ALWAYS);

      statusLabel.getStyleClass().add("snapshot-status");
      statusLabel.setVisible(false);
      statusLabel.setManaged(false);

      restoreButton.getStyleClass().add("button-ghost");
      restoreButton.setOnAction(e -> confirmAndRestore(gameId, snapshot, this));
      if (isLatest) {
        restoreButton.setDisable(true);
        restoreButton.setText("Current");
      }

      getChildren().addAll(dot, when, size, spacer, statusLabel, restoreButton);
      setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    }

    void setRestoring() {
      restoreButton.setDisable(true);
      statusLabel.setText("Restoring…");
      statusLabel.setVisible(true);
      statusLabel.setManaged(true);
    }

    void setFailure(String message) {
      restoreButton.setDisable(false);
      statusLabel.setText(message == null ? "Restore failed" : message);
      statusLabel.setVisible(true);
      statusLabel.setManaged(true);
      statusLabel.getStyleClass().add("failed");
    }
  }
}
