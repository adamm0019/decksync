package dev.decksync.web;

import dev.decksync.application.FileHashing;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.GameIdParser;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Sha256;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code GET /v1/games/{gameId}/files?path=<logical>} — streams one file's bytes from the game's
 * resolved save directory. The response's {@code ETag} is the hex SHA-256 of the payload so a peer
 * can validate the download against a manifest it already holds.
 *
 * <p>Absolute paths never appear on the wire — peers address files by {@code (GameId, LogicalPath)}
 * only and this controller is responsible for turning that into an on-host absolute path behind the
 * boundary. {@link LogicalPath}'s own validation rejects {@code ..}, absolute paths, and
 * backslash-bearing inputs, but we also verify the resolved file stays under the game root in case
 * a symlinked save directory introduces unexpected escape.
 */
@RestController
public class FileDownloadController {

  private final GameCatalog catalog;

  public FileDownloadController(GameCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping(path = "/v1/games/{gameId}/files")
  public ResponseEntity<FileSystemResource> download(
      @PathVariable("gameId") String gameIdRaw, @RequestParam("path") String logicalRaw) {
    GameId gameId = parseGameId(gameIdRaw);
    LogicalPath logical = parseLogicalPath(logicalRaw);

    Map<GameId, AbsolutePath> installed = catalog.resolveInstalled();
    AbsolutePath root = installed.get(gameId);
    if (root == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "game not installed or not resolvable: " + gameIdRaw);
    }

    Path resolved = resolveUnderRoot(root.path(), logical);
    if (!Files.isRegularFile(resolved)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found: " + logicalRaw);
    }

    Sha256 hash = hashOrThrow(resolved);

    HttpHeaders headers = new HttpHeaders();
    headers.setETag("\"" + hash.hex() + "\"");
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    return ResponseEntity.ok().headers(headers).body(new FileSystemResource(resolved));
  }

  private static GameId parseGameId(String raw) {
    try {
      return GameIdParser.parse(raw);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }

  private static LogicalPath parseLogicalPath(String raw) {
    try {
      return new LogicalPath(raw);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    }
  }

  private static Path resolveUnderRoot(Path root, LogicalPath logical) {
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Path resolved = normalizedRoot.resolve(logical.path()).normalize();
    if (!resolved.startsWith(normalizedRoot)) {
      // LogicalPath already rejects '..' segments so this should be unreachable,
      // but keep the guard — defence in depth costs nothing here.
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "path escapes game root");
    }
    return resolved;
  }

  private static Sha256 hashOrThrow(Path path) {
    try {
      return FileHashing.hash(path);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to hash " + path, e);
    }
  }
}
