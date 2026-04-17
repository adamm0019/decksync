package dev.decksync.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.decksync.application.FileScanner;
import dev.decksync.application.GameCatalog;
import dev.decksync.config.JacksonConfiguration;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.FileEntry;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import dev.decksync.domain.Sha256;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManifestController.class)
@Import(JacksonConfiguration.class)
class ManifestControllerTest {

  private static final Instant NOW = Instant.parse("2026-04-17T20:00:00Z");
  private static final Sha256 HASH = Sha256.ofHex("ab".repeat(32));

  @Autowired private MockMvc mvc;

  @MockitoBean private GameCatalog catalog;
  @MockitoBean private FileScanner scanner;

  @Test
  void returnsManifestForInstalledSteamGame(@TempDir Path tmp) throws Exception {
    GameId.SteamAppId gameId = new GameId.SteamAppId(1245620L);
    AbsolutePath root = new AbsolutePath(tmp);
    FileEntry entry = new FileEntry(new LogicalPath("save.sav"), 3L, NOW, HASH);
    Manifest manifest = new Manifest(gameId, List.of(entry), NOW);

    when(catalog.resolveInstalled()).thenReturn(Map.of(gameId, root));
    when(scanner.scan(eq(gameId), any())).thenReturn(manifest);

    mvc.perform(get("/v1/games/steam:1245620/manifest"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.game.kind").value("steam"))
        .andExpect(jsonPath("$.game.appId").value(1245620))
        .andExpect(jsonPath("$.files[0].path").value("save.sav"))
        .andExpect(jsonPath("$.files[0].hash").value(HASH.hex()));
  }

  @Test
  void acceptsSlugAndBareAppId(@TempDir Path tmp) throws Exception {
    GameId.Slug slug = new GameId.Slug("stardew-valley");
    AbsolutePath root = new AbsolutePath(tmp);
    Manifest manifest = new Manifest(slug, List.of(), NOW);

    when(catalog.resolveInstalled()).thenReturn(Map.of(slug, root));
    when(scanner.scan(eq(slug), any())).thenReturn(manifest);

    mvc.perform(get("/v1/games/stardew-valley/manifest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.game.kind").value("slug"))
        .andExpect(jsonPath("$.game.value").value("stardew-valley"));
  }

  @Test
  void returnsNotFoundWhenGameIsNotInstalled() throws Exception {
    when(catalog.resolveInstalled()).thenReturn(Map.of());

    mvc.perform(get("/v1/games/steam:1245620/manifest")).andExpect(status().isNotFound());
  }

  @Test
  void returnsBadRequestWhenGameIdIsUnparseable() throws Exception {
    mvc.perform(get("/v1/games/steam:NOT-A-NUMBER/manifest")).andExpect(status().isBadRequest());
  }
}
