package dev.decksync.application;

import dev.decksync.domain.GameId;
import java.util.Objects;

/**
 * Renders a {@link DeckSyncConfig} as the YAML text {@code decksync setup} writes to {@code
 * ~/.decksync/config.yml}. Hand-rolled rather than driven by SnakeYAML's dumper so the output
 * carries explanatory comments and a stable field order — the file is meant to be edited by users,
 * not just round-tripped by the loader.
 */
public final class ConfigInitializer {

  private ConfigInitializer() {}

  public static String render(DeckSyncConfig config) {
    Objects.requireNonNull(config, "config");
    StringBuilder sb = new StringBuilder(512);
    sb.append("# DeckSync config. Regenerate with 'decksync setup' or hand-edit.\n");
    sb.append("\n");
    sb.append("peer:\n");
    sb.append("  url: ").append(config.peerUrl()).append("\n");
    sb.append("\n");
    sb.append("# TCP port this machine listens on with 'decksync serve'.\n");
    sb.append("port: ").append(config.port()).append("\n");
    sb.append("\n");
    sb.append("# Number of timestamped backups to keep per game in ~/.decksync/history/.\n");
    sb.append("retention: ").append(config.retention()).append("\n");
    sb.append("\n");
    sb.append("# Games to sync. Leave empty to sync every game both peers have installed.\n");
    sb.append("games:");
    if (config.watchedGames().isEmpty()) {
      sb.append(" []\n");
    } else {
      sb.append("\n");
      for (GameId game : config.watchedGames()) {
        sb.append("  - ").append(format(game)).append("\n");
      }
    }
    return sb.toString();
  }

  private static String format(GameId id) {
    return switch (id) {
      case GameId.SteamAppId s -> "steam:" + s.appid();
      case GameId.Slug s -> s.value();
    };
  }
}
