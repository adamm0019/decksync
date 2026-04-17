package dev.decksync.config;

import dev.decksync.application.Environment;
import dev.decksync.application.GameArt;
import dev.decksync.infrastructure.art.SteamArtFetcher;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GUI-only beans. Kept separate from {@link SyncConfiguration} so the CLI-only daemon path doesn't
 * pull in JavaFX or network dependencies it doesn't need.
 */
@Configuration
public class GuiConfiguration {

  private static final String ART_CACHE_RELATIVE = ".decksync/cache/art";

  @Bean
  public GameArt gameArt(Environment env) {
    Path cacheDir = env.home().resolve(ART_CACHE_RELATIVE);
    return new SteamArtFetcher(cacheDir);
  }
}
