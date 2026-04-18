package dev.decksync.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.decksync.application.GameCatalog;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.domain.Sha256;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(FileDownloadController.class)
class FileDownloadControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private GameCatalog catalog;

  @Test
  void streamsFileBytesIdenticallyAndAdvertisesSha256Etag(@TempDir Path tmp) throws Exception {
    GameId gameId = new GameId.SteamAppId(1245620L);
    Path saveFile = tmp.resolve("saves/slot_0.sav");
    Files.createDirectories(saveFile.getParent());
    byte[] payload = "DeckSync payload — not quite random.".getBytes(StandardCharsets.UTF_8);
    Files.write(saveFile, payload);

    when(catalog.resolveInstalled()).thenReturn(Map.of(gameId, new AbsolutePath(tmp)));

    MvcResult result =
        mvc.perform(get("/v1/games/1245620/files").param("path", "saves/slot_0.sav"))
            .andExpect(status().isOk())
            .andExpect(header().longValue("Content-Length", payload.length))
            .andReturn();

    byte[] body = result.getResponse().getContentAsByteArray();
    assertThat(body).isEqualTo(payload);

    String expected = expectedEtag(payload);
    assertThat(result.getResponse().getHeader("ETag")).isEqualTo(expected);
  }

  @Test
  void returnsNotFoundWhenGameIsNotInstalled() throws Exception {
    when(catalog.resolveInstalled()).thenReturn(Map.of());

    mvc.perform(get("/v1/games/1245620/files").param("path", "save.sav"))
        .andExpect(status().isNotFound());
  }

  @Test
  void returnsNotFoundWhenRequestedFileDoesNotExist(@TempDir Path tmp) throws Exception {
    GameId gameId = new GameId.SteamAppId(1245620L);
    when(catalog.resolveInstalled()).thenReturn(Map.of(gameId, new AbsolutePath(tmp)));

    mvc.perform(get("/v1/games/1245620/files").param("path", "no-such.sav"))
        .andExpect(status().isNotFound());
  }

  @Test
  void returnsBadRequestOnTraversalAttempt(@TempDir Path tmp) throws Exception {
    GameId gameId = new GameId.SteamAppId(1245620L);
    when(catalog.resolveInstalled()).thenReturn(Map.of(gameId, new AbsolutePath(tmp)));

    // LogicalPath itself rejects '..' — verify the controller surfaces that as 400
    // rather than letting the exception leak.
    mvc.perform(get("/v1/games/1245620/files").param("path", "../../etc/shadow"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void returnsBadRequestOnUnparseableGameId() throws Exception {
    mvc.perform(get("/v1/games/steam:NOT-A-NUMBER/files").param("path", "save.sav"))
        .andExpect(status().isBadRequest());
  }

  private static String expectedEtag(byte[] payload) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(payload);
    Sha256 sha = new Sha256(hash);
    return "\"" + HexFormat.of().formatHex(sha.bytes()) + "\"";
  }
}
