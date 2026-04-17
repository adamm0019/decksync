package dev.decksync.gui.view;

import dev.decksync.domain.GameId;
import java.nio.file.Path;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A single library card: art placeholder, title, status chip, time string. Exposes {@link
 * #update(CardStatus, String)} so the library controller can swap state in-place when an async
 * probe completes without having to rebuild and re-layout the node.
 *
 * <p>Art fetching lands in M7h — for now the art region is a flat coloured rectangle. The per-card
 * "Sync now" button lands in M7d alongside the preview sheet.
 */
public final class GameCardView extends VBox {

  private static final double ART_WIDTH = 220;
  private static final double ART_HEIGHT = 100;

  private final GameId gameId;
  private final Label statusGlyph = new Label();
  private final Label statusLabel = new Label();
  private final Label timeLabel = new Label();
  private final HBox statusLine;
  private final Button syncButton = new Button("Sync now");
  private final StackPane artHost;
  private final ImageView artView = new ImageView();
  private CardStatus currentStatus = CardStatus.LOADING;
  private Consumer<GameId> onSyncNow;

  public GameCardView(GameId gameId, String displayName) {
    this.gameId = gameId;

    getStyleClass().add("game-card");
    setPrefWidth(220);

    Region artBackground = new Region();
    artBackground.getStyleClass().add("card-art");
    artBackground.setPrefSize(ART_WIDTH, ART_HEIGHT);

    artView.setFitWidth(ART_WIDTH);
    artView.setFitHeight(ART_HEIGHT);
    artView.setPreserveRatio(false);
    artView.setSmooth(true);
    artView.setVisible(false);

    artHost = new StackPane(artBackground, artView);
    artHost.setPrefSize(ART_WIDTH, ART_HEIGHT);
    artHost.setMinHeight(ART_HEIGHT);
    artHost.getStyleClass().add("card-art-host");

    Label title = new Label(displayName);
    title.getStyleClass().add("card-title");
    title.setWrapText(true);

    statusGlyph.getStyleClass().add("status-glyph");
    statusLabel.getStyleClass().add("status-label");
    statusLine = new HBox(6, statusGlyph, statusLabel);
    statusLine.getStyleClass().add("status-line");

    timeLabel.getStyleClass().add("card-time");

    syncButton.getStyleClass().addAll("button-ghost", "card-action");
    syncButton.setOnAction(
        e -> {
          if (onSyncNow != null) {
            onSyncNow.accept(gameId);
          }
        });
    syncButton.setVisible(false);
    syncButton.setManaged(false);

    getChildren().addAll(artHost, title, statusLine, timeLabel, syncButton);
    applyStatusStyle();
  }

  /** Display an art image loaded from a local cached file. No-op when the image fails to decode. */
  public void setArt(Path file) {
    try {
      Image image = new Image(file.toUri().toString(), ART_WIDTH, ART_HEIGHT, false, true, true);
      image
          .errorProperty()
          .addListener(
              (obs, old, now) -> {
                if (Boolean.TRUE.equals(now)) {
                  artView.setImage(null);
                  artView.setVisible(false);
                }
              });
      artView.setImage(image);
      artView.setVisible(true);
    } catch (RuntimeException e) {
      artView.setImage(null);
      artView.setVisible(false);
    }
  }

  public void setOnSyncNow(Consumer<GameId> handler) {
    this.onSyncNow = handler;
  }

  public GameId gameId() {
    return gameId;
  }

  public CardStatus currentStatus() {
    return currentStatus;
  }

  public void update(CardStatus status, String timeText) {
    this.currentStatus = status;
    statusGlyph.setText(status.glyph());
    statusLabel.setText(status.label());
    timeLabel.setText(timeText);
    applyStatusStyle();
    boolean showSync = status == CardStatus.NEWER_PEER || status == CardStatus.CONFLICT;
    syncButton.setVisible(showSync);
    syncButton.setManaged(showSync);
  }

  private void applyStatusStyle() {
    statusLine.getStyleClass().removeAll("ok", "info", "bad", "muted", "loading");
    statusLine.getStyleClass().add(currentStatus.cssModifier());
  }
}
