package dev.decksync.application;

import dev.decksync.domain.GameId;
import dev.decksync.domain.Platform;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * User-provided path overrides keyed by {@link GameId}. Produced by {@code OverridesLoader} and
 * consumed by the game catalog to displace Ludusavi-derived resolutions when a player keeps their
 * saves somewhere other than the default.
 */
public record Overrides(Map<GameId, PlatformOverride> byGame) {

  public static final Overrides EMPTY = new Overrides(Map.of());

  public Overrides {
    Objects.requireNonNull(byGame, "byGame");
    byGame = Collections.unmodifiableMap(new LinkedHashMap<>(byGame));
  }

  public Optional<String> forGame(GameId id, Platform platform) {
    Objects.requireNonNull(id, "id");
    PlatformOverride entry = byGame.get(id);
    return entry == null ? Optional.empty() : entry.forPlatform(platform);
  }
}
