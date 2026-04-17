package dev.decksync.web;

import dev.decksync.application.FileScanner;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.GameIdParser;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.domain.Manifest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /v1/games/{gameId}/manifest} — resolves the game's save directory on this host and
 * returns a freshly-walked {@link Manifest} as JSON. The body mirrors what {@code decksync scan}
 * prints, so a peer can fetch a manifest directly instead of shelling out to the CLI.
 *
 * <p>404 when the game isn't installed or isn't resolvable on this host; 400 when the path
 * parameter can't be parsed as a Steam appid or slug. Any consumer tolerating slow responses should
 * use this endpoint — large save directories mean scans can take seconds.
 */
@RestController
public class ManifestController {

  private final GameCatalog catalog;
  private final FileScanner scanner;

  public ManifestController(GameCatalog catalog, FileScanner scanner) {
    this.catalog = catalog;
    this.scanner = scanner;
  }

  @GetMapping(path = "/v1/games/{gameId}/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
  public Manifest manifest(@PathVariable("gameId") String gameIdRaw) {
    GameId gameId;
    try {
      gameId = GameIdParser.parse(gameIdRaw);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
    Map<GameId, AbsolutePath> installed = catalog.resolveInstalled();
    AbsolutePath root = installed.get(gameId);
    if (root == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "game not installed or not resolvable: " + gameIdRaw);
    }
    return scanner.scan(gameId, root);
  }
}
