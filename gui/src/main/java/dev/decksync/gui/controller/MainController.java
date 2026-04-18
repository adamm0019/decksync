package dev.decksync.gui.controller;

import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.Environment;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.PeerReachability;
import dev.decksync.application.PeerStatus;
import dev.decksync.domain.GameId;
import dev.decksync.gui.log.GuiLogBuffer;
import dev.decksync.gui.log.GuiLogEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Top-level shell controller. Owns the top bar + nav and swaps child FXMLs into the center
 * StackPane when the user clicks a nav button. Per-destination behaviour lives in {@link
 * LibraryController} / {@link HistoryController} / {@link SettingsController}.
 */
@Component
public class MainController {

  private final ApplicationContext context;
  private final PeerReachability reachability;
  private final GameCatalog catalog;
  private final DeckSyncConfig config;
  private final Environment env;

  @FXML private StackPane destinationHost;
  @FXML private HBox peerPill;
  @FXML private Label peerLabel;
  @FXML private Button libraryNavButton;
  @FXML private Button historyNavButton;
  @FXML private Button settingsNavButton;
  @FXML private VBox logDrawerContent;
  @FXML private Label logDrawerHandle;
  @FXML private Label logCountLabel;
  @FXML private ListView<GuiLogEvent> logListView;

  private final ObservableList<GuiLogEvent> logRows = FXCollections.observableArrayList();
  private boolean logDrawerOpen;

  public MainController(
      ApplicationContext context,
      PeerReachability reachability,
      GameCatalog catalog,
      DeckSyncConfig config,
      Environment env) {
    this.context = context;
    this.reachability = reachability;
    this.catalog = catalog;
    this.config = config;
    this.env = env;
  }

  @FXML
  void initialize() {
    setPeerPill(PillState.UNKNOWN, "Peer: checking…");
    onShowLibrary();
    Thread.ofVirtual().name("peer-pill-probe").start(this::refreshPeerPill);
    initLogDrawer();
    // Scene isn't available at FXML load — hook accelerators once it appears.
    destinationHost
        .sceneProperty()
        .addListener(
            (obs, oldScene, scene) -> {
              if (scene != null) {
                installAccelerators(scene);
              }
            });
    maybeShowFirstRun();
  }

  private void installAccelerators(Scene scene) {
    scene
        .getAccelerators()
        .put(
            new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN),
            this::onShowLibrary);
    scene
        .getAccelerators()
        .put(
            new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN),
            this::onShowHistory);
    scene
        .getAccelerators()
        .put(
            new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.SHORTCUT_DOWN),
            this::onShowSettings);
    scene
        .getAccelerators()
        .put(
            new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN),
            this::onToggleLogDrawer);
  }

  private void initLogDrawer() {
    logListView.setItems(logRows);
    logListView.setCellFactory(lv -> new LogRowCell());
    logListView.setFocusTraversable(false);
    logRows.setAll(GuiLogBuffer.shared().snapshot());
    updateLogCountLabel();
    GuiLogBuffer.shared()
        .addListener(
            event ->
                Platform.runLater(
                    () -> {
                      // Only auto-scroll if the user is already near the bottom — don't yank a
                      // view that's been scrolled up to read older entries.
                      boolean wasAtBottom =
                          logRows.isEmpty() || logListView.getSelectionModel().isEmpty();
                      logRows.add(event);
                      while (logRows.size() > GuiLogBuffer.CAPACITY) {
                        logRows.remove(0);
                      }
                      updateLogCountLabel();
                      if (logDrawerOpen && wasAtBottom && !logRows.isEmpty()) {
                        logListView.scrollTo(logRows.size() - 1);
                      }
                    }));
  }

  private void updateLogCountLabel() {
    int n = logRows.size();
    logCountLabel.setText(n == 1 ? "1 entry" : n + " entries");
  }

  @FXML
  void onToggleLogDrawer() {
    logDrawerOpen = !logDrawerOpen;
    logDrawerHandle.setText(logDrawerOpen ? "▼ Hide log" : "▲ Show log");
    if (logDrawerOpen) {
      logDrawerContent.setOpacity(0);
      logDrawerContent.setManaged(true);
      logDrawerContent.setVisible(true);
      FadeTransition fade = new FadeTransition(javafx.util.Duration.millis(180), logDrawerContent);
      fade.setFromValue(0);
      fade.setToValue(1);
      fade.play();
      if (!logRows.isEmpty()) {
        logListView.scrollTo(logRows.size() - 1);
      }
    } else {
      FadeTransition fade = new FadeTransition(javafx.util.Duration.millis(120), logDrawerContent);
      fade.setFromValue(logDrawerContent.getOpacity());
      fade.setToValue(0);
      fade.setOnFinished(
          e -> {
            logDrawerContent.setVisible(false);
            logDrawerContent.setManaged(false);
          });
      fade.play();
    }
  }

  @FXML
  void onClearLog() {
    logRows.clear();
    updateLogCountLabel();
  }

  private void maybeShowFirstRun() {
    if (Files.isRegularFile(env.home().resolve(".decksync/config.yml"))) {
      return;
    }
    Platform.runLater(
        () -> {
          Window owner = peerPill.getScene() == null ? null : peerPill.getScene().getWindow();
          FirstRunController.showModal(context, owner);
        });
  }

  private void refreshPeerPill() {
    PeerStatus status = reachability.probe();
    Platform.runLater(() -> applyPeerStatus(status));
  }

  private void applyPeerStatus(PeerStatus status) {
    if (status instanceof PeerStatus.Reachable reachable) {
      Duration rtt = reachable.rtt();
      setPeerPill(PillState.OK, "Peer connected · " + rtt.toMillis() + "ms");
    } else if (status instanceof PeerStatus.Unreachable) {
      setPeerPill(PillState.BAD, "Peer unreachable");
    }
  }

  @FXML
  void onShowLibrary() {
    showDestination("/fxml/library.fxml", libraryNavButton);
  }

  @FXML
  void onShowHistory() {
    showDestination("/fxml/history.fxml", historyNavButton);
  }

  @FXML
  void onShowSettings() {
    showDestination("/fxml/settings.fxml", settingsNavButton);
  }

  // Gear icon also jumps to Settings — two paths, one destination.
  @FXML
  void onOpenSettings() {
    onShowSettings();
  }

  @FXML
  void onSyncAll() {
    openPreview(resolveAllWatchedGames());
  }

  /**
   * Open the preview sheet for the given games. Invoked from {@link LibraryController} when the
   * per-card "Sync now" button fires.
   */
  public void openPreview(List<GameId> games) {
    if (games.isEmpty()) {
      return;
    }
    Window owner = peerPill.getScene().getWindow();
    PreviewController.showModal(context, owner, games);
  }

  private List<GameId> resolveAllWatchedGames() {
    List<GameId> configured = config.watchedGames();
    List<GameId> resolved = new ArrayList<>();
    if (configured.isEmpty()) {
      resolved.addAll(catalog.resolveInstalled().keySet());
    } else {
      resolved.addAll(configured);
    }
    return resolved;
  }

  private void showDestination(String fxmlPath, Button activeNav) {
    FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
    loader.setControllerFactory(context::getBean);
    Node root;
    try {
      root = loader.load();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load " + fxmlPath, e);
    }
    destinationHost.getChildren().setAll(root);
    setActiveNav(activeNav);
  }

  private void setActiveNav(Button activeNav) {
    for (Button b : List.of(libraryNavButton, historyNavButton, settingsNavButton)) {
      b.getStyleClass().remove("active");
      if (b == activeNav) {
        b.getStyleClass().add("active");
      }
    }
  }

  /** Pill colour via style class; neutral grey when UNKNOWN. */
  private void setPeerPill(PillState state, String labelText) {
    peerPill.getStyleClass().removeAll("ok", "warn", "bad");
    switch (state) {
      case OK -> peerPill.getStyleClass().add("ok");
      case WARN -> peerPill.getStyleClass().add("warn");
      case BAD -> peerPill.getStyleClass().add("bad");
      case UNKNOWN -> {
        // No modifier — keeps the neutral tertiary-grey dot.
      }
    }
    peerLabel.setText(labelText);
  }

  private enum PillState {
    OK,
    WARN,
    BAD,
    UNKNOWN
  }

  /**
   * ListCell for a single log row. Uses three labels in an HBox — monospaced timestamp,
   * colour-coded level, then logger + message. Updated in-place on reuse (JavaFX recycles cells).
   */
  private static final class LogRowCell extends ListCell<GuiLogEvent> {

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Label time = new Label();
    private final Label level = new Label();
    private final Label message = new Label();
    private final HBox row;

    LogRowCell() {
      time.getStyleClass().add("log-time");
      level.getStyleClass().add("log-level");
      message.getStyleClass().add("log-message");
      HBox.setHgrow(message, javafx.scene.layout.Priority.ALWAYS);
      message.setMaxWidth(Double.MAX_VALUE);
      row = new HBox(8, time, level, message);
      row.getStyleClass().add("log-row");
      setGraphic(null);
    }

    @Override
    protected void updateItem(GuiLogEvent item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setText(null);
        setGraphic(null);
        return;
      }
      time.setText(TIME_FMT.format(item.when()));
      level.setText(item.level());
      level.getStyleClass().removeAll("info", "warn", "error", "debug");
      level.getStyleClass().add(item.level().toLowerCase(java.util.Locale.ROOT));
      message.setText(item.logger() + " — " + item.message());
      setText(null);
      setGraphic(row);
    }
  }
}
