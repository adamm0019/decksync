package dev.decksync.config;

import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.Environment;
import dev.decksync.infrastructure.config.DeckSyncConfigLoader;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads {@code ~/.decksync/config.yml} at startup into a {@link DeckSyncConfig} bean. Missing file
 * yields defaults; malformed YAML or invalid values surface as a {@link
 * DeckSyncConfigLoader.ConfigParseException} and crash startup — per CLAUDE.md, the daemon fails
 * loud rather than drifting to defaults after a config typo.
 */
@Configuration
public class ConfigConfiguration {

  private static final Logger log = LoggerFactory.getLogger(ConfigConfiguration.class);

  private static final String CONFIG_RELATIVE = ".decksync/config.yml";

  @Bean
  public DeckSyncConfigLoader deckSyncConfigLoader() {
    return new DeckSyncConfigLoader();
  }

  @Bean
  public DeckSyncConfig deckSyncConfig(DeckSyncConfigLoader loader, Environment env) {
    Path file = env.home().resolve(CONFIG_RELATIVE);
    DeckSyncConfig config = loader.load(file);
    log.info(
        "Loaded DeckSync config: peer={} port={} retention={} watchedGames={}",
        config.peerUrl(),
        config.port(),
        config.retention(),
        config.watchedGames().isEmpty() ? "<all>" : config.watchedGames());
    return config;
  }
}
