package dev.decksync.domain;

/**
 * Stable logical identity for a game. Sealed: either a Steam appid when the game is a Steam title,
 * or a slug for everything else. The same game resolves to the same {@code GameId} on every peer,
 * which is what makes the save-comparison protocol OS- and path-independent.
 */
public sealed interface GameId permits GameId.SteamAppId, GameId.Slug {

  /**
   * Steam application id. A positive integer assigned by Valve — e.g. Elden Ring is {@code
   * 1245620}.
   */
  record SteamAppId(long appid) implements GameId {
    public SteamAppId {
      if (appid <= 0) {
        throw new IllegalArgumentException("Steam appid must be positive — got: " + appid);
      }
    }
  }

  /**
   * Kebab-case slug for games without a Steam appid. Lowercase ASCII letters, digits, and hyphens;
   * must start with a letter or digit.
   */
  record Slug(String value) implements GameId {
    public Slug {
      if (value == null || !value.matches("[a-z0-9][a-z0-9-]*")) {
        throw new IllegalArgumentException("Slug must be lowercase kebab-case — got: " + value);
      }
    }
  }
}
