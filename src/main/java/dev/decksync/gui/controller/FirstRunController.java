package dev.decksync.gui.controller;

import dev.decksync.application.ConfigInitializer;
import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.Environment;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.ManifestEntry;
import dev.decksync.application.ManifestIndex;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Five-step first-run wizard: welcome → peer URL → games → behaviour → done. Writes {@code
 * ~/.decksync/config.yml} through the same {@link ConfigInitializer} the CLI uses, so both paths
 * produce byte-identical files. The running Spring context isn't rebuilt — on finish, the wizard
 * shows a "restart to apply" note because the {@link DeckSyncConfig} bean was read at startup.
 */
@Component
public class FirstRunController {

  private static final Logger log = LoggerFactory.getLogger(FirstRunController.class);

  private final GameCatalog catalog;
  private final Environment env;
  private final ManifestIndex manifestIndex;

  @FXML private Label stepIndicatorLabel;
  @FXML private Label stepTitleLabel;
  @FXML private VBox welcomeStep;
  @FXML private VBox peerStep;
  @FXML private VBox gamesStep;
  @FXML private VBox behaviourStep;
  @FXML private VBox doneStep;
  @FXML private TextField peerUrlField;
  @FXML private Label peerErrorLabel;
  @FXML private Label gamesLead;
  @FXML private CheckBox syncAllCheck;
  @FXML private VBox gamesBox;
  @FXML private Spinner<Integer> retentionSpinner;
  @FXML private Spinner<Integer> portSpinner;
  @FXML private Label doneSummary;
  @FXML private Button backButton;
  @FXML private Button nextButton;
  @FXML private Button skipButton;

  private final List<VBox> steps = new ArrayList<>();
  private final List<String> titles = List.of("Welcome", "Peer", "Games", "Behaviour", "Done");
  private final Map<CheckBox, GameId> gameChecks = new LinkedHashMap<>();
  private Stage stage;
  private int currentStep;

  public FirstRunController(GameCatalog catalog, Environment env, ManifestIndex manifestIndex) {
    this.catalog = catalog;
    this.env = env;
    this.manifestIndex = manifestIndex;
  }

  @FXML
  void initialize() {
    steps.addAll(List.of(welcomeStep, peerStep, gamesStep, behaviourStep, doneStep));
    retentionSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            1, 200, DeckSyncConfig.DEFAULT_RETENTION));
    portSpinner.setValueFactory(
        new SpinnerValueFactory.IntegerSpinnerValueFactory(
            1024, 65535, DeckSyncConfig.DEFAULT_PORT));
    peerErrorLabel.setVisible(false);
    peerErrorLabel.setManaged(false);
    populateGames();
    syncAllCheck
        .selectedProperty()
        .addListener((obs, old, now) -> gamesBox.setDisable(Boolean.TRUE.equals(now)));
    syncAllCheck.setSelected(true);
    showStep(0);
  }

  private void populateGames() {
    Map<GameId, AbsolutePath> installed = catalog.resolveInstalled();
    if (installed.isEmpty()) {
      gamesLead.setText(
          "No installed games resolved on this host — DeckSync will sync whatever the peer has"
              + " once its library lands.");
      syncAllCheck.setDisable(true);
      return;
    }
    gamesLead.setText(
        "Found "
            + installed.size()
            + " installed game"
            + (installed.size() == 1 ? "" : "s")
            + " on this machine.");
    List<GameId> sorted =
        installed.keySet().stream()
            .sorted(Comparator.comparing(this::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    for (GameId id : sorted) {
      CheckBox cb = new CheckBox(displayName(id));
      cb.getStyleClass().add("wizard-game-check");
      gameChecks.put(cb, id);
      gamesBox.getChildren().add(cb);
    }
  }

  void setStage(Stage stage) {
    this.stage = stage;
  }

  private void showStep(int index) {
    currentStep = index;
    for (int i = 0; i < steps.size(); i++) {
      VBox step = steps.get(i);
      boolean active = i == index;
      step.setVisible(active);
      step.setManaged(active);
    }
    stepIndicatorLabel.setText("Step " + (index + 1) + " of " + steps.size());
    stepTitleLabel.setText(titles.get(index));
    backButton.setDisable(index == 0 || index == steps.size() - 1);
    backButton.setVisible(index != steps.size() - 1);
    backButton.setManaged(index != steps.size() - 1);
    skipButton.setVisible(index != steps.size() - 1);
    skipButton.setManaged(index != steps.size() - 1);
    if (index == steps.size() - 1) {
      nextButton.setText("Close");
    } else if (index == steps.size() - 2) {
      nextButton.setText("Finish");
    } else {
      nextButton.setText("Next");
    }
  }

  @FXML
  void onBack() {
    if (currentStep > 0) {
      showStep(currentStep - 1);
    }
  }

  @FXML
  void onNext() {
    if (currentStep == 1 && !validatePeer()) {
      return;
    }
    if (currentStep == steps.size() - 2) {
      if (!finishAndWrite()) {
        return;
      }
      showStep(currentStep + 1);
      return;
    }
    if (currentStep == steps.size() - 1) {
      closeStage();
      return;
    }
    showStep(currentStep + 1);
  }

  @FXML
  void onSkip() {
    closeStage();
  }

  private boolean validatePeer() {
    String raw = peerUrlField.getText() == null ? "" : peerUrlField.getText().trim();
    if (raw.isBlank()) {
      showPeerError("Enter a URL or LAN IP — e.g. 192.168.1.11");
      return false;
    }
    String candidate =
        raw.matches("^https?://.*") ? raw : "http://" + raw + ":" + DeckSyncConfig.DEFAULT_PORT;
    try {
      URI uri = new URI(candidate);
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new URISyntaxException(candidate, "no host");
      }
      peerErrorLabel.setVisible(false);
      peerErrorLabel.setManaged(false);
      return true;
    } catch (URISyntaxException e) {
      showPeerError("That doesn't look like a valid URL — try 192.168.1.11 or http://host:port");
      return false;
    }
  }

  private void showPeerError(String message) {
    peerErrorLabel.setText(message);
    peerErrorLabel.setVisible(true);
    peerErrorLabel.setManaged(true);
  }

  private boolean finishAndWrite() {
    URI peerUrl = parsePeerUrl();
    List<GameId> games = collectGames();
    int retention =
        retentionSpinner.getValue() == null
            ? DeckSyncConfig.DEFAULT_RETENTION
            : retentionSpinner.getValue();
    int port =
        portSpinner.getValue() == null ? DeckSyncConfig.DEFAULT_PORT : portSpinner.getValue();
    DeckSyncConfig config;
    try {
      config = new DeckSyncConfig(peerUrl, games, port, retention);
    } catch (IllegalArgumentException e) {
      showPeerError(e.getMessage());
      showStep(1);
      return false;
    }
    Path configFile = env.home().resolve(".decksync/config.yml");
    try {
      Path parent = configFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(configFile, ConfigInitializer.render(config), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write " + configFile, e);
    }
    log.info("first-run wizard wrote {}", configFile);
    doneSummary.setText(
        "Config written to "
            + configFile
            + ".\nPeer "
            + peerUrl
            + " · "
            + (games.isEmpty() ? "all installed games" : games.size() + " games pinned")
            + " · keep "
            + retention
            + " snapshots per game.");
    return true;
  }

  private URI parsePeerUrl() {
    String raw = peerUrlField.getText().trim();
    String candidate =
        raw.matches("^https?://.*") ? raw : "http://" + raw + ":" + DeckSyncConfig.DEFAULT_PORT;
    return URI.create(candidate);
  }

  private List<GameId> collectGames() {
    if (syncAllCheck.isSelected()) {
      return List.of();
    }
    List<GameId> picked = new ArrayList<>();
    for (Map.Entry<CheckBox, GameId> e : gameChecks.entrySet()) {
      if (e.getKey().isSelected()) {
        picked.add(e.getValue());
      }
    }
    return picked;
  }

  private void closeStage() {
    if (stage != null) {
      stage.close();
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

  /** Open the wizard as an application-modal stage. Caller is responsible for deciding when. */
  public static Stage showModal(ApplicationContext ctx, Window owner) {
    FXMLLoader loader = new FXMLLoader(FirstRunController.class.getResource("/fxml/firstrun.fxml"));
    loader.setControllerFactory(ctx::getBean);
    try {
      VBox root = loader.load();
      FirstRunController controller = loader.getController();
      Stage stage = new Stage();
      stage.setTitle("DeckSync setup");
      stage.initOwner(owner);
      stage.initModality(Modality.APPLICATION_MODAL);
      stage.setScene(new Scene(root));
      controller.setStage(stage);
      // Defer showing until owner's next frame so it can't show behind the main window.
      Platform.runLater(stage::show);
      return stage;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load firstrun.fxml", e);
    }
  }
}
