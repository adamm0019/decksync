package dev.decksync.gui.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
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

  @FXML private StackPane destinationHost;
  @FXML private HBox peerPill;
  @FXML private Label peerLabel;
  @FXML private Button libraryNavButton;
  @FXML private Button historyNavButton;
  @FXML private Button settingsNavButton;

  public MainController(ApplicationContext context) {
    this.context = context;
  }

  @FXML
  void initialize() {
    setPeerStatus(PeerStatus.UNKNOWN, "Peer: not configured");
    onShowLibrary();
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
    // Wired in M7d. Kept here so the FXML reference doesn't break.
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

  /**
   * Peer state classes toggle the pill's colour via CSS. Live wiring to a reachability probe lands
   * in M7c when the card grid needs the same data.
   */
  void setPeerStatus(PeerStatus status, String labelText) {
    peerPill.getStyleClass().removeAll("ok", "warn", "bad");
    switch (status) {
      case OK -> peerPill.getStyleClass().add("ok");
      case WARN -> peerPill.getStyleClass().add("warn");
      case BAD -> peerPill.getStyleClass().add("bad");
      case UNKNOWN -> {
        // No modifier — keeps the neutral tertiary-grey dot.
      }
    }
    peerLabel.setText(labelText);
  }

  enum PeerStatus {
    OK,
    WARN,
    BAD,
    UNKNOWN
  }
}
