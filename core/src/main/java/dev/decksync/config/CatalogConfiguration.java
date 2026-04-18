package dev.decksync.config;

import dev.decksync.application.CrossPlayResolverFactory;
import dev.decksync.application.DefaultGameCatalog;
import dev.decksync.application.Environment;
import dev.decksync.application.GameCatalog;
import dev.decksync.application.ManifestEntry;
import dev.decksync.application.ManifestIndex;
import dev.decksync.application.Overrides;
import dev.decksync.application.PlaceholderResolver;
import dev.decksync.application.ProtonPrefixResolver;
import dev.decksync.application.SteamLibraryLocator;
import dev.decksync.domain.Platform;
import dev.decksync.infrastructure.config.OverridesLoader;
import dev.decksync.infrastructure.manifest.LudusaviManifestLoader;
import dev.decksync.infrastructure.manifest.LudusaviManifestParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link GameCatalog} and its YAML-backed collaborators. The manifest is parsed once at
 * startup — it's ~10 MB of YAML that we don't want to re-read on every list-games invocation — and
 * indexed for fast Steam-appid lookup. User overrides are loaded the same way; if the file doesn't
 * exist we wire an empty {@link Overrides} so callers don't need to null-check.
 */
@Configuration
public class CatalogConfiguration {

  private static final Logger log = LoggerFactory.getLogger(CatalogConfiguration.class);

  private static final String OVERRIDES_RELATIVE = ".decksync/overrides.yml";

  @Bean
  public LudusaviManifestParser ludusaviManifestParser() {
    return new LudusaviManifestParser();
  }

  @Bean
  public LudusaviManifestLoader ludusaviManifestLoader(LudusaviManifestParser parser) {
    return new LudusaviManifestLoader(parser);
  }

  @Bean
  public ManifestIndex manifestIndex(LudusaviManifestLoader loader) {
    List<ManifestEntry> entries = loader.load();
    log.info("Loaded Ludusavi manifest with {} entries", entries.size());
    return ManifestIndex.from(entries);
  }

  @Bean
  public OverridesLoader overridesLoader() {
    return new OverridesLoader();
  }

  @Bean
  public Overrides overrides(OverridesLoader loader, Environment env) {
    Path file = env.home().resolve(OVERRIDES_RELATIVE);
    Overrides loaded = loader.load(file);
    if (!loaded.byGame().isEmpty()) {
      log.info("Loaded {} override(s) from {}", loaded.byGame().size(), file);
    }
    return loaded;
  }

  @Bean
  public GameCatalog gameCatalog(
      Platform platform,
      SteamLibraryLocator libraryLocator,
      ManifestIndex manifest,
      PlaceholderResolver placeholderResolver,
      Overrides overrides,
      Optional<ProtonPrefixResolver> protonPrefixResolver,
      Optional<CrossPlayResolverFactory> crossPlayFactory) {
    return new DefaultGameCatalog(
        platform,
        libraryLocator,
        manifest,
        placeholderResolver,
        overrides,
        protonPrefixResolver,
        crossPlayFactory);
  }
}
