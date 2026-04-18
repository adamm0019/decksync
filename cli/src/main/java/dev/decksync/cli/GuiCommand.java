package dev.decksync.cli;

import dev.decksync.gui.DeckSyncGuiApp;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * {@code decksync gui} — launches the JavaFX window. Hands the running Spring context to {@link
 * DeckSyncGuiApp} so FXML controllers can be resolved as Spring beans. Blocks on the JavaFX thread
 * until the window closes (JavaFX then closes the Spring context in {@code stop()}).
 */
@Component
@Command(
    name = "gui",
    mixinStandardHelpOptions = true,
    description = "Launch the DeckSync graphical interface.")
public class GuiCommand implements Runnable {

  private final ConfigurableApplicationContext context;

  public GuiCommand(ConfigurableApplicationContext context) {
    this.context = context;
  }

  @Override
  public void run() {
    DeckSyncGuiApp.launchWith(context, new String[0]);
  }
}
