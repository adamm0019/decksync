package dev.decksync.infrastructure.net;

import dev.decksync.application.PeerReachability;
import dev.decksync.application.PeerStatus;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;

/**
 * Probes the peer with a short-timeout GET against {@code /v1/games} — JDK's HttpClient doesn't
 * support HEAD without a body handler, but GET of the games list is small enough (a few dozen bytes
 * in the worst case) that the distinction doesn't matter for a reachability probe. Anything other
 * than a 2xx response, or any timeout / IO failure, is reported as unreachable with a
 * human-readable reason.
 */
public final class HttpPeerReachability implements PeerReachability {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  private final HttpClient client;
  private final URI target;
  private final Duration timeout;

  public HttpPeerReachability(URI peerBaseUrl) {
    this(peerBaseUrl, DEFAULT_TIMEOUT);
  }

  public HttpPeerReachability(URI peerBaseUrl, Duration timeout) {
    if (peerBaseUrl == null) {
      throw new IllegalArgumentException("peerBaseUrl must not be null");
    }
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive — got: " + timeout);
    }
    this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    this.target = peerBaseUrl.resolve("/v1/games");
    this.timeout = timeout;
  }

  @Override
  public PeerStatus probe() {
    HttpRequest request =
        HttpRequest.newBuilder(target).timeout(timeout).GET().header("Accept", "*/*").build();
    Instant start = Instant.now();
    try {
      HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
      Duration rtt = Duration.between(start, Instant.now());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return new PeerStatus.Reachable(rtt);
      }
      return new PeerStatus.Unreachable("HTTP " + response.statusCode());
    } catch (java.net.http.HttpTimeoutException e) {
      return new PeerStatus.Unreachable("timeout after " + timeout);
    } catch (IOException e) {
      return new PeerStatus.Unreachable(e.getClass().getSimpleName() + ": " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new PeerStatus.Unreachable("interrupted");
    }
  }
}
