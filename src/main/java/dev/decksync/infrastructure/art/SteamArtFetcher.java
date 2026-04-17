package dev.decksync.infrastructure.art;

import dev.decksync.application.GameArt;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches Steam library header art from {@code cdn.cloudflare.steamstatic.com} and caches it under
 * {@code ~/.decksync/cache/art/}. Sized for the 460×215 {@code header.jpg} asset — it's reliably
 * available for any app on Steam and fits the 220×100 card art region with room to spare.
 *
 * <p>All network operations are synchronous and blocking; the GUI calls this from virtual threads
 * and marshals results back via {@code Platform.runLater}. Failures are swallowed to {@link
 * Optional#empty()} because missing art is a visual downgrade, not an error path worth surfacing.
 */
public final class SteamArtFetcher implements GameArt {

  private static final Logger log = LoggerFactory.getLogger(SteamArtFetcher.class);

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final String TMP_SUFFIX = ".decksync.tmp";

  private final Path cacheDir;
  private final HttpClient http;

  public SteamArtFetcher(Path cacheDir) {
    this(cacheDir, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
  }

  SteamArtFetcher(Path cacheDir, HttpClient http) {
    this.cacheDir = cacheDir;
    this.http = http;
  }

  /**
   * Returns the cached file path for {@code appid}'s header art, fetching it on a cache miss. Empty
   * when the asset is absent on the CDN or the network errors out.
   */
  @Override
  public Optional<Path> fetch(long steamAppId) {
    Path file = cacheDir.resolve(steamAppId + ".jpg");
    if (Files.isRegularFile(file)) {
      try {
        if (Files.size(file) > 0) {
          return Optional.of(file);
        }
      } catch (IOException e) {
        // fall through and re-fetch
      }
    }
    return download(steamAppId, file);
  }

  private Optional<Path> download(long appid, Path target) {
    URI uri =
        URI.create(
            String.format(
                "https://cdn.cloudflare.steamstatic.com/steam/apps/%d/header.jpg", appid));
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
    try {
      Files.createDirectories(cacheDir);
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        log.debug("art fetch for {} returned {}", appid, response.statusCode());
        return Optional.empty();
      }
      Path tmp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
      Files.write(tmp, response.body());
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      return Optional.of(target);
    } catch (IOException e) {
      log.debug("art fetch for {} failed: {}", appid, e.getMessage());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
