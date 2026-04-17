package dev.decksync.infrastructure.net;

import dev.decksync.application.PeerClient;
import dev.decksync.application.PeerException;
import dev.decksync.application.PeerFileNotFoundException;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link PeerClient} backed by Spring's {@link RestClient}. Maps the three HTTP endpoints to their
 * {@link PeerClient} counterparts and funnels errors through the {@link PeerException} / {@link
 * PeerFileNotFoundException} hierarchy so the sync driver doesn't have to reason about HTTP status
 * codes directly.
 *
 * <p>Gameids are serialized into the URL as {@code steam:<appid>} or as the slug string — the
 * server's {@code GameIdParser} accepts either form, and Steam appids encoded as a bare number
 * round-trip cleanly without URL-encoding surprises.
 */
public final class HttpPeerClient implements PeerClient {

  private final RestClient http;

  public HttpPeerClient(RestClient http) {
    this.http = http;
  }

  @Override
  public List<GameId> listGames() {
    try {
      GamesResponseDto response =
          http.get().uri("/v1/games").retrieve().body(GamesResponseDto.class);
      if (response == null || response.games() == null) {
        throw new PeerException("Peer returned an empty /v1/games response");
      }
      return response.games();
    } catch (RestClientResponseException e) {
      throw mapError("list games", e);
    }
  }

  @Override
  public Manifest fetchManifest(GameId game) {
    String id = canonical(game);
    try {
      Manifest manifest =
          http.get().uri("/v1/games/{gameId}/manifest", id).retrieve().body(Manifest.class);
      if (manifest == null) {
        throw new PeerException("Peer returned empty manifest for " + id);
      }
      return manifest;
    } catch (HttpClientErrorException.NotFound e) {
      throw new PeerFileNotFoundException("Peer has no manifest for " + id);
    } catch (RestClientResponseException e) {
      throw mapError("fetch manifest for " + id, e);
    }
  }

  @Override
  public byte[] downloadFile(GameId game, LogicalPath path) {
    String id = canonical(game);
    try {
      byte[] body =
          http.get()
              .uri(
                  builder ->
                      builder
                          .path("/v1/games/{gameId}/files")
                          .queryParam("path", path.path())
                          .build(id))
              .retrieve()
              .body(byte[].class);
      if (body == null) {
        throw new PeerException("Peer returned empty body for " + id + "!" + path.path());
      }
      return body;
    } catch (HttpClientErrorException.NotFound e) {
      throw new PeerFileNotFoundException("Peer has no file " + path.path() + " for game " + id);
    } catch (RestClientResponseException e) {
      throw mapError("download " + path.path() + " for " + id, e);
    }
  }

  private static String canonical(GameId game) {
    // Steam appids are emitted as bare numerics rather than `steam:<id>` so Spring's URI
    // template expansion doesn't percent-encode the colon — the server's GameIdParser treats
    // any all-digit path segment as a Steam appid, and Ludusavi slugs are always kebab-case
    // so there's no collision.
    return switch (game) {
      case GameId.SteamAppId s -> Long.toString(s.appid());
      case GameId.Slug s -> s.value();
    };
  }

  private static PeerException mapError(String op, RestClientResponseException e) {
    HttpStatusCode status = e.getStatusCode();
    return new PeerException(
        "Peer " + op + " failed: HTTP " + status.value() + " " + e.getStatusText(), e);
  }

  record GamesResponseDto(List<GameId> games) {}

  /**
   * Static factory so config code can build a pre-baseUrl'd instance without peeking at internals.
   */
  public static HttpPeerClient create(RestClient.Builder builder, URI baseUrl) {
    RestClient client = builder.baseUrl(baseUrl.toString()).build();
    return new HttpPeerClient(client);
  }
}
