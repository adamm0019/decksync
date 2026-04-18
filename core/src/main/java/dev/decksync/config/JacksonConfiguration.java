package dev.decksync.config;

import dev.decksync.infrastructure.json.ManifestJsonModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the DeckSync-specific Jackson module with Spring Boot's autoconfigured ObjectMapper, so
 * {@link dev.decksync.domain.Manifest} and friends serialize identically everywhere they leak into
 * I/O (CLI output now, HTTP responses in M4). {@code JavaTimeModule} is registered automatically by
 * Boot because {@code jackson-datatype-jsr310} is on the classpath.
 */
@Configuration
public class JacksonConfiguration {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer manifestModuleCustomizer() {
    return builder -> builder.modulesToInstall(new ManifestJsonModule());
  }
}
