package dev.decksync.cli;

import dev.decksync.domain.GameId;

/**
 * Parses the free-form {@code <gameId>} token accepted by CLI subcommands. A bare positive integer
 * or a {@code steam:} prefix means a Steam appid; anything else is parsed as a kebab-case slug.
 * Kept off the picocli command classes so scan and sync can share it.
 */
final class GameIdParser {

  private static final String STEAM_PREFIX = "steam:";

  private GameIdParser() {}

  static GameId parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("gameId must not be blank");
    }
    String trimmed = raw.trim();
    String numeric;
    if (trimmed.startsWith(STEAM_PREFIX)) {
      numeric = trimmed.substring(STEAM_PREFIX.length());
    } else if (trimmed.chars().allMatch(Character::isDigit)) {
      numeric = trimmed;
    } else {
      return new GameId.Slug(trimmed);
    }
    try {
      return new GameId.SteamAppId(Long.parseLong(numeric));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Unparseable steam appid: " + raw, e);
    }
  }
}
