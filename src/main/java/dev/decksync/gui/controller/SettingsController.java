package dev.decksync.gui.controller;

import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.Environment;
import dev.decksync.application.ManifestEntry;
import dev.decksync.application.ManifestIndex;
import dev.decksync.application.PeerReachability;
import dev.decksync.application.PeerStatus;
import dev.decksync.domain.GameId;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Settings destination: a read-only view of the parsed config plus a handful of actionable
 * shortcuts — "Test connection", "Open folder", "Copy firewall snippet". Edits go through {@code
 * ~/.decksync/config.yml} rather than the GUI in Phase 1 so there's exactly one source of truth for
 * peer URL + watched games + retention; the note at the top of the destination points users there.
 */
@Component
public class SettingsController {

  private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

  private final DeckSyncConfig config;
  private final Environment env;
  private final PeerReachability reachability;
  private final ManifestIndex manifestIndex;

  @FXML private Label peerUrlValue;
  @FXML private Label portValue;
  @FXML private Button testButton;
  @FXML private Label testResultLabel;
  @FXML private Label gamesSummaryLabel;
  @FXML private VBox gamesListBox;
  @FXML private Label retentionValue;
  @FXML private Label configPathValue;
  @FXML private Label historyPathValue;
  @FXML private Label cachePathValue;
  @FXML private Label firewallSnippetLabel;
  @FXML private Button copyFirewallButton;
  @FXML private Label autostartHintLabel;

  public SettingsController(
      DeckSyncConfig config,
      Environment env,
      PeerReachability reachability,
      ManifestIndex manifestIndex) {
    this.config = config;
    this.env = env;
    this.reachability = reachability;
    this.manifestIndex = manifestIndex;
  }

  @FXML
  void initialize() {
    peerUrlValue.setText(config.peerUrl().toString());
    portValue.setText(String.valueOf(config.port()));
    retentionValue.setText(
        config.retention() + " snapshot" + (config.retention() == 1 ? "" : "s") + " per game");

    Path home = env.home().resolve(".decksync");
    configPathValue.setText(home.resolve("config.yml").toString());
    historyPathValue.setText(home.resolve("history").toString());
    cachePathValue.setText(home.resolve("cache").toString());

    renderGames();
    renderFirewallSnippet();
    renderAutostartHint();
  }

  private void renderGames() {
    List<GameId> watched = config.watchedGames();
    gamesListBox.getChildren().clear();
    if (watched.isEmpty()) {
      gamesSummaryLabel.setText(
          "No explicit list — DeckSync syncs every game both peers have installed.");
      return;
    }
    gamesSummaryLabel.setText(
        watched.size() + " game" + (watched.size() == 1 ? "" : "s") + " pinned for sync");
    for (GameId id : watched) {
      Label label = new Label("• " + displayName(id));
      label.getStyleClass().add("settings-game-row");
      gamesListBox.getChildren().add(label);
    }
  }

  private void renderFirewallSnippet() {
    int port = config.port();
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    String snippet;
    if (os.contains("win")) {
      snippet =
          "netsh advfirewall firewall add rule name=\"DeckSync\" dir=in action=allow protocol=TCP"
              + " localport="
              + port;
    } else {
      snippet =
          "sudo firewall-cmd --permanent --add-port="
              + port
              + "/tcp && sudo firewall-cmd"
              + " --reload";
    }
    firewallSnippetLabel.setText(snippet);
  }

  private void renderAutostartHint() {
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    if (os.contains("win")) {
      autostartHintLabel.setText(
          "On Windows, launch DeckSync at login by adding a shortcut to"
              + " shell:startup (Win+R → shell:startup).");
    } else {
      autostartHintLabel.setText(
          "On Linux/SteamOS, enable the bundled user service:\n"
              + "systemctl --user enable --now decksync.service");
    }
  }

  @FXML
  void onTestConnection() {
    testButton.setDisable(true);
    testResultLabel.getStyleClass().removeAll("ok", "bad");
    testResultLabel.setText("Testing…");
    Thread.ofVirtual()
        .name("settings-peer-probe")
        .start(
            () -> {
              PeerStatus status = reachability.probe();
              Platform.runLater(() -> showTestResult(status));
            });
  }

  private void showTestResult(PeerStatus status) {
    testButton.setDisable(false);
    testResultLabel.getStyleClass().removeAll("ok", "bad");
    if (status instanceof PeerStatus.Reachable reachable) {
      testResultLabel.setText("Connected · " + reachable.rtt().toMillis() + "ms");
      testResultLabel.getStyleClass().add("ok");
    } else if (status instanceof PeerStatus.Unreachable unreach) {
      testResultLabel.setText("Unreachable · " + unreach.reason());
      testResultLabel.getStyleClass().add("bad");
    }
  }

  @FXML
  void onOpenConfigDir() {
    Path home = env.home().resolve(".decksync");
    try {
      Files.createDirectories(home);
    } catch (IOException e) {
      log.warn("could not create config dir {}: {}", home, e.getMessage());
    }
    if (!Desktop.isDesktopSupported()) {
      log.warn("Desktop integration not supported on this platform");
      return;
    }
    try {
      Desktop.getDesktop().open(home.toFile());
    } catch (IOException | UnsupportedOperationException e) {
      log.warn("failed to open {}: {}", home, e.getMessage());
    }
  }

  @FXML
  void onCopyFirewall() {
    ClipboardContent content = new ClipboardContent();
    content.putString(firewallSnippetLabel.getText());
    Clipboard.getSystemClipboard().setContent(content);
    copyFirewallButton.setText("Copied ✓");
    Thread.ofVirtual()
        .name("settings-copy-reset")
        .start(
            () -> {
              try {
                Thread.sleep(java.time.Duration.ofMillis(1500));
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
              Platform.runLater(() -> copyFirewallButton.setText("Copy snippet"));
            });
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
}
