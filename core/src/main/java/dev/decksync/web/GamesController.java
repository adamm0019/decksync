package dev.decksync.web;

import dev.decksync.application.GameCatalog;
import dev.decksync.domain.GameId;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /v1/games} — lists the games this peer has resolved on disk. The response carries
 * {@link GameId}s only; absolute paths stay on the host that owns them because the protocol treats
 * {@code (GameId, LogicalPath)} as the universal address for a save file.
 *
 * <p>Returned ids are sorted by their canonical string form so a peer's diffing logic doesn't have
 * to re-sort and responses are stable in tests and logs.
 */
@RestController
public class GamesController {

  private final GameCatalog catalog;

  public GamesController(GameCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping(path = "/v1/games", produces = MediaType.APPLICATION_JSON_VALUE)
  public GamesResponse listGames() {
    List<GameId> games =
        catalog.resolveInstalled().keySet().stream()
            .sorted(Comparator.comparing(GamesController::canonical))
            .toList();
    return new GamesResponse(games);
  }

  private static String canonical(GameId id) {
    return switch (id) {
      case GameId.SteamAppId s -> "steam:" + s.appid();
      case GameId.Slug s -> s.value();
    };
  }

  public record GamesResponse(List<GameId> games) {
    public GamesResponse {
      games = List.copyOf(games);
    }
  }
}
