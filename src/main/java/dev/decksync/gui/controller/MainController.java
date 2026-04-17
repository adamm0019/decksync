package dev.decksync.gui.controller;

import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.PeerReachability;
import dev.decksync.application.PeerStatus;
import dev.decksync.domain.GameId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
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

  @FXML private StackPane destinationHost;
  @FXML private HBox peerPill;
  @FXML private Label peerLabel;
  @FXML private Button libraryNavButton;
  @FXML private Button historyNavButton;
  @FXML private Button settingsNavButton;

  public MainController(
      ApplicationContext context,
      PeerReachability reachability,
      GameCatalog catalog,
      DeckSyncConfig config) {
    this.context = context;
    this.reachability = reachability;
    this.catalog = catalog;
    this.config = config;
  }

  @FXML
  void initialize() {
    setPeerPill(PillState.UNKNOWN, "Peer: checking…");
    onShowLibrary();
    Thread.ofVirtual().name("peer-pill-probe").start(this::refreshPeerPill);
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
}
