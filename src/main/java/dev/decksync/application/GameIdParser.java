package dev.decksync.application;

import dev.decksync.domain.GameId;

/**
 * Parses the free-form {@code <gameId>} token accepted by both CLI subcommands and HTTP path
 * parameters. A bare positive integer or a {@code steam:} prefix means a Steam appid; anything else
 * is parsed as a kebab-case slug. Lives in the application layer so any adapter (cli, web) can
 * reuse it without reinventing the grammar.
 */
public final class GameIdParser {

  private static final String STEAM_PREFIX = "steam:";

  private GameIdParser() {}

  public static GameId parse(String raw) {
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
