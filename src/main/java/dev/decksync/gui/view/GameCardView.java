package dev.decksync.gui.view;

import dev.decksync.domain.GameId;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
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

  private final GameId gameId;
  private final Label statusGlyph = new Label();
  private final Label statusLabel = new Label();
  private final Label timeLabel = new Label();
  private final HBox statusLine;
  private CardStatus currentStatus = CardStatus.LOADING;

  public GameCardView(GameId gameId, String displayName) {
    this.gameId = gameId;

    getStyleClass().add("game-card");
    setPrefWidth(220);

    Region art = new Region();
    art.getStyleClass().add("card-art");
    art.setPrefHeight(100);

    Label title = new Label(displayName);
    title.getStyleClass().add("card-title");
    title.setWrapText(true);

    statusGlyph.getStyleClass().add("status-glyph");
    statusLabel.getStyleClass().add("status-label");
    statusLine = new HBox(6, statusGlyph, statusLabel);
    statusLine.getStyleClass().add("status-line");

    timeLabel.getStyleClass().add("card-time");

    getChildren().addAll(art, title, statusLine, timeLabel);
    applyStatusStyle();
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
  }

  private void applyStatusStyle() {
    statusLine.getStyleClass().removeAll("ok", "info", "bad", "muted", "loading");
    statusLine.getStyleClass().add(currentStatus.cssModifier());
  }
}
