package dev.decksync.gui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.springframework.stereotype.Component;

/**
 * Root controller for {@code main.fxml}. Kept intentionally empty in M7a — later milestones wire
 * peer reachability, per-game status, and the sync button through constructor-injected application
 * services.
 */
@Component
public class MainController {

  @FXML private Label statusLabel;

  @FXML
  void initialize() {
    if (statusLabel != null) {
      statusLabel.setText("GUI skeleton ready.");
    }
  }
}
