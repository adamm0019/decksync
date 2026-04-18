package dev.decksync.config;

import dev.decksync.application.DefaultFileScanner;
import dev.decksync.application.Environment;
import dev.decksync.application.FileScanner;
import dev.decksync.application.HashCache;
import dev.decksync.infrastructure.cache.FileHashCache;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the file-scanning side of the pipeline. Uses a persistent {@link FileHashCache} so large
 * save directories (tens-of-GB JRPG saves, Minecraft worlds) don't re-hash unchanged files on every
 * scan — this is the single biggest perf lever in the daemon.
 */
@Configuration
public class ScannerConfiguration {

  private static final String CACHE_RELATIVE = ".decksync/cache/hashes.tsv";
  private static final Duration STABILITY_WINDOW = Duration.ofSeconds(3);

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public FileHashCache fileHashCache(Environment env) {
    Path cacheFile = env.home().resolve(CACHE_RELATIVE);
    return new FileHashCache(cacheFile);
  }

  @Bean
  public FileScanner fileScanner(Clock clock, HashCache hashCache) {
    return new DefaultFileScanner(clock, STABILITY_WINDOW, hashCache);
  }
}
