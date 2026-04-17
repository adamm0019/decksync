package dev.decksync.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.decksync.application.GameCatalog;
import dev.decksync.config.JacksonConfiguration;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GamesController.class)
@Import(JacksonConfiguration.class)
class GamesControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private GameCatalog catalog;

  @Test
  void returnsEmptyArrayWhenNoGamesResolved() throws Exception {
    when(catalog.resolveInstalled()).thenReturn(Map.of());

    mvc.perform(get("/v1/games"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.games").isArray())
        .andExpect(jsonPath("$.games.length()").value(0));
  }

  @Test
  void returnsSortedGameIdsWithoutAbsolutePaths(@TempDir Path tmp) throws Exception {
    Path steamRoot = tmp.resolve("steam-save");
    Path slugRoot = tmp.resolve("slug-save");
    when(catalog.resolveInstalled())
        .thenReturn(
            Map.of(
                new GameId.SteamAppId(1245620L), new AbsolutePath(steamRoot),
                new GameId.Slug("stardew-valley"), new AbsolutePath(slugRoot)));

    mvc.perform(get("/v1/games"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.games.length()").value(2))
        // "stardew-valley" sorts before "steam:1245620" canonically, so slug comes first.
        .andExpect(jsonPath("$.games[0].kind").value("slug"))
        .andExpect(jsonPath("$.games[0].value").value("stardew-valley"))
        .andExpect(jsonPath("$.games[1].kind").value("steam"))
        .andExpect(jsonPath("$.games[1].appId").value(1245620))
        // The host's absolute paths must never leak over the wire.
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(tmp.toString()))));
  }
}
