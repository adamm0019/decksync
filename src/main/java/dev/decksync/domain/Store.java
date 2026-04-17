package dev.decksync.domain;

import java.util.Locale;

/**
 * Which store a game came from. Phase 1 only resolves Steam entries; anything else is carried
 * through as {@link #OTHER} so manifest entries can be classified for logging but not resolved.
 */
public enum Store {
  STEAM,
  OTHER;

  /**
   * Maps a Ludusavi manifest store tag (e.g. {@code "steam"}, {@code "gog"}) to a {@link Store}.
   */
  public static Store fromLudusaviTag(String tag) {
    if (tag == null) {
      return OTHER;
    }
    return "steam".equals(tag.toLowerCase(Locale.ROOT)) ? STEAM : OTHER;
  }
}
