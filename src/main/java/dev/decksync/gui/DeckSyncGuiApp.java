package dev.decksync.gui;

import java.io.IOException;
import java.io.UncheckedIOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX entry point. Bridges JavaFX's lifecycle (which insists on constructing the {@link
 * Application} subclass itself) with Spring's lifecycle (which owns all the beans we want to inject
 * into FXML controllers). Pattern: a short-lived static holder carries the Spring context from
 * {@link #launchWith} into the JavaFX-instantiated {@link #start}, which then wires FXMLLoader's
 * controller factory to {@code context::getBean} so controllers come from the Spring container.
 */
public class DeckSyncGuiApp extends Application {

  private static ConfigurableApplicationContext springContext;

  public static void launchWith(ConfigurableApplicationContext context, String[] args) {
    springContext = context;
    Application.launch(DeckSyncGuiApp.class, args);
  }

  @Override
  public void start(Stage stage) {
    FXMLLoader loader = new FXMLLoader(DeckSyncGuiApp.class.getResource("/fxml/main.fxml"));
    loader.setControllerFactory(springContext::getBean);
    Parent root;
    try {
      root = loader.load();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load main.fxml", e);
    }
    stage.setTitle("DeckSync");
    stage.setScene(new Scene(root));
    stage.show();
  }

  // Deliberately no stop() override — Spring Boot's main() closes the application context via
  // SpringApplication.exit() after the CLI Runnable returns. Closing it here as well races with
  // that path and throws IllegalStateException.
}
