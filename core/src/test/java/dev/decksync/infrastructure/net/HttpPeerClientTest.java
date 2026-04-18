package dev.decksync.infrastructure.net;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.decksync.application.PeerException;
import dev.decksync.application.PeerFileNotFoundException;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import dev.decksync.infrastructure.json.ManifestJsonModule;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

class HttpPeerClientTest {

  private WireMockServer wm;
  private HttpPeerClient client;

  @BeforeEach
  void start() {
    wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wm.start();
    ObjectMapper mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ManifestJsonModule());
    RestClient rest =
        RestClient.builder()
            .baseUrl(URI.create("http://localhost:" + wm.port()).toString())
            .messageConverters(
                converters -> {
                  converters.clear();
                  converters.add(new ByteArrayHttpMessageConverter());
                  converters.add(new MappingJackson2HttpMessageConverter(mapper));
                })
            .build();
    client = new HttpPeerClient(rest);
  }

  @AfterEach
  void stop() {
    wm.stop();
  }

  @Test
  void listGamesParsesDiscriminatedGameIds() {
    wm.stubFor(
        get(urlPathEqualTo("/v1/games"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"games\":[{\"kind\":\"steam\",\"appId\":1245620},"
                            + "{\"kind\":\"slug\",\"value\":\"stardew-valley\"}]}")));

    assertThat(client.listGames())
        .containsExactly(new GameId.SteamAppId(1245620L), new GameId.Slug("stardew-valley"));
  }

  @Test
  void fetchManifestRoundtripsViaJackson() {
    wm.stubFor(
        get(urlPathEqualTo("/v1/games/1245620/manifest"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"game\":{\"kind\":\"steam\",\"appId\":1245620},"
                            + "\"files\":[{\"path\":\"save.sav\",\"size\":3,"
                            + "\"mtime\":\"2026-04-17T12:00:00Z\","
                            + "\"hash\":\""
                            + "ab".repeat(32)
                            + "\"}],"
                            + "\"generatedAt\":\"2026-04-17T12:00:05Z\"}")));

    Manifest manifest = client.fetchManifest(new GameId.SteamAppId(1245620L));

    assertThat(manifest.game()).isEqualTo(new GameId.SteamAppId(1245620L));
    assertThat(manifest.files()).hasSize(1);
    assertThat(manifest.files().get(0).path()).isEqualTo(new LogicalPath("save.sav"));
    assertThat(manifest.files().get(0).size()).isEqualTo(3L);
  }

  @Test
  void fetchManifestMapsNotFoundToTypedException() {
    wm.stubFor(
        get(urlPathEqualTo("/v1/games/404/manifest"))
            .willReturn(aResponse().withStatus(404).withBody("not installed")));

    assertThatThrownBy(() -> client.fetchManifest(new GameId.SteamAppId(404L)))
        .isInstanceOf(PeerFileNotFoundException.class);
  }

  @Test
  void downloadFileReturnsRawBytes() {
    byte[] payload = "DeckSync payload".getBytes(StandardCharsets.UTF_8);
    wm.stubFor(
        get(urlPathEqualTo("/v1/games/1245620/files"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/octet-stream")
                    .withBody(payload)));

    byte[] body = client.downloadFile(new GameId.SteamAppId(1245620L), new LogicalPath("save.sav"));
    assertThat(body).isEqualTo(payload);
  }

  @Test
  void downloadFileMapsNotFoundToTypedException() {
    wm.stubFor(
        get(urlPathEqualTo("/v1/games/1245620/files")).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(
            () ->
                client.downloadFile(
                    new GameId.SteamAppId(1245620L), new LogicalPath("missing.sav")))
        .isInstanceOf(PeerFileNotFoundException.class);
  }

  @Test
  void serverErrorMapsToPlainPeerException() {
    wm.stubFor(
        get(urlPathEqualTo("/v1/games")).willReturn(aResponse().withStatus(500).withBody("boom")));

    assertThatThrownBy(() -> client.listGames())
        .isInstanceOf(PeerException.class)
        .hasMessageContaining("500");
  }
}
