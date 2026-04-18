package dev.decksync.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.decksync.application.FileScanner;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.NoopHashCache;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.FileEntry;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import dev.decksync.domain.Sha256;
import dev.decksync.infrastructure.json.ManifestJsonModule;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanCommandTest {

  private static final Instant NOW = Instant.parse("2026-04-17T20:00:00Z");
  private static final Sha256 HASH = Sha256.ofHex("ab".repeat(32));

  @Test
  void scansKnownGameAndPrintsManifestJson(@TempDir Path tmp) throws Exception {
    GameId gameId = new GameId.SteamAppId(1245620L);
    AbsolutePath root = new AbsolutePath(tmp);
    FileEntry entry = new FileEntry(new LogicalPath("save.sav"), 3L, NOW, HASH);
    Manifest manifest = new Manifest(gameId, List.of(entry), NOW);

    Capture capture = new Capture();
    ScanCommand command =
        new ScanCommand(
            () -> Map.of(gameId, root),
            (g, r) -> manifest,
            NoopHashCache.INSTANCE,
            mapper(),
            capture.out,
            capture.err);
    command.setGameIdRaw("1245620");

    int exit = command.call();
    assertThat(exit).isZero();

    JsonNode node = new ObjectMapper().readTree(capture.stdout());
    assertThat(node.get("game").get("kind").asText()).isEqualTo("steam");
    assertThat(node.get("game").get("appId").asLong()).isEqualTo(1245620L);
    assertThat(node.get("files").get(0).get("path").asText()).isEqualTo("save.sav");
    assertThat(node.get("files").get(0).get("hash").asText()).isEqualTo(HASH.hex());
    assertThat(node.get("files").get(0).get("size").asLong()).isEqualTo(3L);
  }

  @Test
  void exitsOneWhenGameNotInstalled() {
    Capture capture = new Capture();
    ScanCommand command =
        new ScanCommand(
            Map::of,
            (g, r) -> {
              throw new AssertionError("scanner should not be invoked");
            },
            NoopHashCache.INSTANCE,
            mapper(),
            capture.out,
            capture.err);
    command.setGameIdRaw("1245620");

    int exit = command.call();

    assertThat(exit).isEqualTo(1);
    assertThat(capture.stderr()).contains("not installed");
  }

  @Test
  void exitsTwoOnUnparseableId() {
    Capture capture = new Capture();
    ScanCommand command =
        new ScanCommand(
            Map::of,
            (g, r) -> {
              throw new AssertionError("should not reach scanner");
            },
            NoopHashCache.INSTANCE,
            mapper(),
            capture.out,
            capture.err);
    command.setGameIdRaw("steam:NOT-A-NUMBER");

    int exit = command.call();

    assertThat(exit).isEqualTo(2);
    assertThat(capture.stderr()).contains("Unparseable");
  }

  @Test
  void acceptsSlugId(@TempDir Path tmp) {
    GameId slug = new GameId.Slug("stardew-valley");
    AbsolutePath root = new AbsolutePath(tmp);
    Manifest manifest = new Manifest(slug, List.of(), NOW);

    Capture capture = new Capture();
    GameCatalog catalog = () -> Map.of(slug, root);
    FileScanner scanner = (g, r) -> manifest;
    ScanCommand command =
        new ScanCommand(
            catalog, scanner, NoopHashCache.INSTANCE, mapper(), capture.out, capture.err);
    command.setGameIdRaw("stardew-valley");

    assertThat(command.call()).isZero();
    assertThat(capture.stdout()).contains("\"kind\" : \"slug\"").contains("stardew-valley");
  }

  private static ObjectMapper mapper() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new ManifestJsonModule());
  }

  private static final class Capture {
    final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
    final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
    final PrintStream out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
    final PrintStream err = new PrintStream(errBuf, true, StandardCharsets.UTF_8);

    String stdout() {
      return outBuf.toString(StandardCharsets.UTF_8);
    }

    String stderr() {
      return errBuf.toString(StandardCharsets.UTF_8);
    }
  }
}
