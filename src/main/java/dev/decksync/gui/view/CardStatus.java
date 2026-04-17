package dev.decksync.gui.view;

/**
 * The small finite set of states a game card can display. Drives both the chip text/glyph and the
 * CSS modifier class on the card. Playing-on-peer is deliberately absent until the scanner surfaces
 * lock state to the GUI layer.
 */
public enum CardStatus {
  LOADING("Checking…", "•", "loading"),
  IN_SYNC("In sync", "●", "ok"),
  NEWER_PEER("Newer on peer", "▲", "info"),
  NEWER_HERE("Newer here", "▼", "info"),
  CONFLICT("Conflict", "⚠", "bad"),
  NOT_INSTALLED("Not installed here", "○", "muted"),
  OFFLINE("Peer offline", "✕", "muted"),
  ERROR("Error", "!", "bad");

  private final String label;
  private final String glyph;
  private final String cssModifier;

  CardStatus(String label, String glyph, String cssModifier) {
    this.label = label;
    this.glyph = glyph;
    this.cssModifier = cssModifier;
  }

  public String label() {
    return label;
  }

  public String glyph() {
    return glyph;
  }

  public String cssModifier() {
    return cssModifier;
  }
}
