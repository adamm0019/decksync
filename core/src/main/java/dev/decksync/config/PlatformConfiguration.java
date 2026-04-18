package dev.decksync.config;

import dev.decksync.application.Environment;
import dev.decksync.application.PlaceholderResolver;
import dev.decksync.domain.Platform;
import dev.decksync.infrastructure.platform.SystemEnvironment;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Host-detection beans. The {@link Platform} bean is a one-shot decision made at application
 * startup by inspecting {@code os.name}; everything downstream (placeholder resolver, Steam
 * locator, cross-play factory) depends on it rather than re-detecting, so a single test override
 * can flip the entire stack to the "other" platform.
 */
@Configuration
public class PlatformConfiguration {

  private static final Logger log = LoggerFactory.getLogger(PlatformConfiguration.class);

  @Bean
  public Platform platform() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    Platform detected = osName.contains("win") ? new Platform.Windows() : new Platform.Linux();
    log.info("Detected host platform: {} (os.name='{}')", detected, osName);
    return detected;
  }

  @Bean
  public Environment deckSyncEnvironment() {
    return new SystemEnvironment();
  }

  @Bean
  public PlaceholderResolver placeholderResolver(Platform platform, Environment env) {
    return switch (platform) {
      case Platform.Windows w -> new PlaceholderResolver.Windows(env);
      case Platform.Linux l -> new PlaceholderResolver.Linux(env);
    };
  }
}
