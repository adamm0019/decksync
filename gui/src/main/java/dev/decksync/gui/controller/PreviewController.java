package dev.decksync.gui.controller;

import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.ManifestEntry;
import dev.decksync.application.ManifestIndex;
import dev.decksync.application.SyncService;
import dev.decksync.domain.FileEntry;
import dev.decksync.domain.GameId;
import dev.decksync.domain.SyncAction;
import dev.decksync.domain.SyncPlan;
import dev.decksync.gui.util.HumanBytes;
import dev.decksync.gui.util.RelativeTime;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Preview sheet: shows a dry-run of what "Sync now" would change and lets the user apply it. Opens
 * as an application-modal stage owned by the main window. Kept @Component singleton — state is
 * reset in {@link #setGames} so multiple opens don't leak rows from a previous invocation.
 */
@Component
public class PreviewController {

  private static final Logger log = LoggerFactory.getLogger(PreviewController.class);

  private final SyncService syncService;
  private final DeckSyncConfig config;
  private final ManifestIndex manifestIndex;
  private final Clock clock;

  @FXML private Label summaryLabel;
  @FXML private VBox rowContainer;
  @FXML private VBox emptyState;
  @FXML private Label backupHint;
  @FXML private Button cancelButton;
  @FXML private Button applyButton;

  private final Map<GameId, GameRow> rows = new LinkedHashMap<>();
  private Stage stage;

  public PreviewController(
      SyncService syncService, DeckSyncConfig config, ManifestIndex manifestIndex, Clock clock) {
    this.syncService = syncService;
    this.config = config;
    this.manifestIndex = manifestIndex;
    this.clock = clock;
  }

  @FXML
  void initialize() {
    rows.clear();
    rowContainer.getChildren().clear();
    emptyState.setVisible(false);
    emptyState.setManaged(false);
    applyButton.setDisable(true);
    summaryLabel.setText("Checking…");
    backupHint.setText("Backups on · most recent " + config.retention() + " snapshots kept");
  }

  void setStage(Stage stage) {
    this.stage = stage;
  }

  /** Kick off dry-runs for {@code gameIds} and populate the sheet. */
  void setGames(List<GameId> gameIds) {
    Thread.ofVirtual().name("preview-dry-run").start(() -> loadPreview(gameIds));
  }

  private void loadPreview(List<GameId> gameIds) {
    Map<GameId, SyncPlan> planned = new LinkedHashMap<>();
    for (GameId id : gameIds) {
      try {
        SyncPlan plan = syncService.syncGame(id, true).plan();
        if (hasActionable(plan)) {
          planned.put(id, plan);
        }
      } catch (RuntimeException e) {
        log.warn("preview dry-run failed for {}: {}", id, e.getMessage());
      }
    }
    Platform.runLater(() -> render(planned, gameIds.size()));
  }

  private void render(Map<GameId, SyncPlan> planned, int totalChecked) {
    if (planned.isEmpty()) {
      summaryLabel.setText("0 of " + totalChecked + " games have changes.");
      emptyState.setVisible(true);
      emptyState.setManaged(true);
      rowContainer.setVisible(false);
      rowContainer.setManaged(false);
      applyButton.setDisable(true);
      return;
    }
    summaryLabel.setText(planned.size() + " of " + totalChecked + " games have changes to pull.");
    for (Map.Entry<GameId, SyncPlan> e : planned.entrySet()) {
      GameRow row = new GameRow(e.getKey(), e.getValue());
      rows.put(e.getKey(), row);
      rowContainer.getChildren().add(row);
    }
    applyButton.setDisable(false);
  }

  @FXML
  void onCancel() {
    if (stage != null) {
      stage.close();
    }
  }

  @FXML
  void onApply() {
    applyButton.setDisable(true);
    cancelButton.setDisable(true);
    applyButton.setText("Applying…");
    for (GameRow row : rows.values()) {
      row.setState(RowState.PENDING);
    }
    Thread.ofVirtual().name("preview-apply").start(this::applyAll);
  }

  private void applyAll() {
    int ok = 0;
    int fail = 0;
    for (GameRow row : rows.values()) {
      GameId id = row.gameId;
      Platform.runLater(() -> row.setState(RowState.APPLYING));
      try {
        syncService.syncGame(id, false);
        Platform.runLater(() -> row.setState(RowState.DONE));
        ok++;
      } catch (RuntimeException e) {
        log.warn("apply failed for {}: {}", id, e.getMessage());
        Platform.runLater(() -> row.setFailure(e.getMessage()));
        fail++;
      }
    }
    final int okFinal = ok;
    final int failFinal = fail;
    Platform.runLater(() -> finishApply(okFinal, failFinal));
  }

  private void finishApply(int ok, int fail) {
    if (fail == 0) {
      if (stage != null) {
        stage.close();
      }
      return;
    }
    applyButton.setText("Close");
    applyButton.setDisable(false);
    applyButton.setOnAction(e -> stage.close());
    cancelButton.setVisible(false);
    cancelButton.setManaged(false);
    summaryLabel.setText(ok + " of " + (ok + fail) + " synced · " + fail + " failed");
  }

  private static boolean hasActionable(SyncPlan plan) {
    for (SyncAction action : plan.actions()) {
      if (action instanceof SyncAction.Pull || action instanceof SyncAction.Conflict) {
        return true;
      }
    }
    return false;
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

  public static Stage showModal(
      org.springframework.context.ApplicationContext ctx, Window owner, List<GameId> games) {
    javafx.fxml.FXMLLoader loader =
        new javafx.fxml.FXMLLoader(PreviewController.class.getResource("/fxml/preview.fxml"));
    loader.setControllerFactory(ctx::getBean);
    try {
      VBox root = loader.load();
      PreviewController controller = loader.getController();
      Stage stage = new Stage();
      stage.setTitle("Preview sync");
      stage.initOwner(owner);
      stage.initModality(Modality.APPLICATION_MODAL);
      stage.setScene(new Scene(root));
      controller.setStage(stage);
      controller.setGames(games);
      stage.show();
      return stage;
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException("Failed to load preview.fxml", e);
    }
  }

  /** Visual state of one game's row during apply. */
  private enum RowState {
    PENDING,
    APPLYING,
    DONE,
    FAILED
  }

  private final class GameRow extends VBox {
    private final GameId gameId;
    private final Label statusGlyph = new Label();
    private final Label errorLabel = new Label();

    GameRow(GameId gameId, SyncPlan plan) {
      this.gameId = gameId;
      getStyleClass().add("preview-row");

      HBox header = new HBox(10);
      header.getStyleClass().add("preview-row-header");
      Label name = new Label(displayName(gameId));
      name.getStyleClass().add("preview-row-title");
      Label meta = new Label(summarise(plan));
      meta.getStyleClass().add("preview-row-meta");
      Region spacer = new Region();
      HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
      statusGlyph.getStyleClass().add("preview-row-status");
      statusGlyph.setText("▲");
      header.getChildren().addAll(name, meta, spacer, statusGlyph);

      VBox fileList = new VBox(2);
      fileList.getStyleClass().add("preview-file-list");
      for (SyncAction action : plan.actions()) {
        if (action instanceof SyncAction.Pull pull) {
          fileList.getChildren().add(fileRow(pull.path().path(), pull.remote()));
        } else if (action instanceof SyncAction.Conflict conflict) {
          fileList.getChildren().add(fileRow("⚠ " + conflict.path().path(), conflict.remote()));
        }
      }

      errorLabel.getStyleClass().add("preview-row-error");
      errorLabel.setVisible(false);
      errorLabel.setManaged(false);

      getChildren().addAll(header, fileList, errorLabel);
    }

    private HBox fileRow(String path, FileEntry entry) {
      HBox row = new HBox(10);
      row.getStyleClass().add("preview-file");
      Label pathLabel = new Label(path);
      pathLabel.getStyleClass().add("preview-file-path");
      Region spacer = new Region();
      HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
      Label size = new Label(HumanBytes.format(entry.size()));
      size.getStyleClass().add("preview-file-meta");
      Label when = new Label("updated " + RelativeTime.format(entry.mtime(), Instant.now(clock)));
      when.getStyleClass().add("preview-file-meta");
      row.getChildren().addAll(pathLabel, spacer, size, when);
      return row;
    }

    void setState(RowState state) {
      errorLabel.setVisible(false);
      errorLabel.setManaged(false);
      switch (state) {
        case PENDING -> statusGlyph.setText("•");
        case APPLYING -> statusGlyph.setText("⟳");
        case DONE -> statusGlyph.setText("✓");
        case FAILED -> statusGlyph.setText("✕");
      }
      statusGlyph.getStyleClass().removeAll("pending", "applying", "done", "failed");
      statusGlyph.getStyleClass().add(state.name().toLowerCase(java.util.Locale.ROOT));
    }

    void setFailure(String message) {
      setState(RowState.FAILED);
      errorLabel.setText(message == null ? "Sync failed" : message);
      errorLabel.setVisible(true);
      errorLabel.setManaged(true);
    }

    private String summarise(SyncPlan plan) {
      long files = 0;
      long bytes = 0;
      List<SyncAction.Pull> pulls = new ArrayList<>();
      for (SyncAction action : plan.actions()) {
        if (action instanceof SyncAction.Pull pull) {
          pulls.add(pull);
        }
      }
      for (SyncAction.Pull pull : pulls) {
        files++;
        bytes += pull.remote().size();
      }
      return files + " file" + (files == 1 ? "" : "s") + " · " + HumanBytes.format(bytes);
    }
  }
}
