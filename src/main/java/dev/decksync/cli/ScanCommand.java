package dev.decksync.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dev.decksync.application.FileScanner;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.HashCache;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.domain.Manifest;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code decksync scan <gameId>} — resolves the game's save directory via the catalog, walks it
 * with the {@link FileScanner}, and prints the resulting {@link Manifest} as pretty JSON. Primary
 * M3 sanity check: scanning the same logical folder on Windows and on a Deck under Proton should
 * produce byte-identical manifests for files Proton hasn't touched.
 *
 * <p>Exits with a non-zero status when the game isn't installed or the id is unparseable, so this
 * composes cleanly with shell pipelines and CI checks.
 */
@Component
@Command(
    name = "scan",
    mixinStandardHelpOptions = true,
    description = "Scan an installed game's save directory and print its manifest as JSON.")
public class ScanCommand implements Callable<Integer> {

  private final GameCatalog catalog;
  private final FileScanner scanner;
  private final HashCache hashCache;
  private final ObjectMapper objectMapper;
  private final PrintStream out;
  private final PrintStream err;

  @Parameters(index = "0", paramLabel = "<gameId>", description = "Steam appid or slug")
  private String gameIdRaw;

  @Autowired
  public ScanCommand(
      GameCatalog catalog, FileScanner scanner, HashCache hashCache, ObjectMapper objectMapper) {
    this(catalog, scanner, hashCache, objectMapper, System.out, System.err);
  }

  ScanCommand(
      GameCatalog catalog,
      FileScanner scanner,
      HashCache hashCache,
      ObjectMapper objectMapper,
      PrintStream out,
      PrintStream err) {
    this.catalog = catalog;
    this.scanner = scanner;
    this.hashCache = hashCache;
    this.objectMapper = objectMapper;
    this.out = out;
    this.err = err;
  }

  void setGameIdRaw(String raw) {
    this.gameIdRaw = raw;
  }

  @Override
  public Integer call() {
    GameId gameId;
    try {
      gameId = GameIdParser.parse(gameIdRaw);
    } catch (IllegalArgumentException e) {
      err.println("error: " + e.getMessage());
      return 2;
    }
    Map<GameId, AbsolutePath> games = catalog.resolveInstalled();
    AbsolutePath root = games.get(gameId);
    if (root == null) {
      err.println("error: game not installed or not resolvable: " + gameIdRaw);
      return 1;
    }
    Manifest manifest = scanner.scan(gameId, root);
    hashCache.persist();
    ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();
    try {
      out.println(writer.writeValueAsString(manifest));
    } catch (JsonProcessingException e) {
      err.println("error: failed to serialize manifest: " + e.getMessage());
      return 1;
    }
    return 0;
  }
}
