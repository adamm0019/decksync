package dev.decksync.config;

import dev.decksync.application.CrossPlayResolverFactory;
import dev.decksync.application.Environment;
import dev.decksync.application.PlaceholderResolver;
import dev.decksync.application.ProtonPrefixResolver;
import dev.decksync.application.SteamLibraryLocator;
import dev.decksync.domain.Platform;
import dev.decksync.infrastructure.steam.LibraryFoldersVdfReader;
import dev.decksync.infrastructure.steam.LinuxProtonPrefixResolver;
import dev.decksync.infrastructure.steam.LinuxSteamLibraryLocator;
import dev.decksync.infrastructure.steam.ProtonEnvironment;
import dev.decksync.infrastructure.steam.SteamRootFinder;
import dev.decksync.infrastructure.steam.VdfParser;
import dev.decksync.infrastructure.steam.WindowsRegistrySteamRootFinder;
import dev.decksync.infrastructure.steam.WindowsSteamLibraryLocator;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Steam-side wiring. Builds the locator chain (VDF parser → library reader → OS-specific locator)
 * and the Proton-specific beans consumed by the cross-play branch of the catalog. Proton beans are
 * wrapped in {@link Optional} so the same configuration class is safe to load on both Windows and
 * Linux — Spring doesn't need conditional bean declarations.
 */
@Configuration
public class SteamConfiguration {

  @Bean
  public VdfParser vdfParser() {
    return new VdfParser();
  }

  @Bean
  public LibraryFoldersVdfReader libraryFoldersVdfReader(VdfParser parser) {
    return new LibraryFoldersVdfReader(parser);
  }

  @Bean
  public SteamLibraryLocator steamLibraryLocator(
      Platform platform, LibraryFoldersVdfReader reader, Environment env) {
    return switch (platform) {
      case Platform.Windows w -> {
        SteamRootFinder finder = new WindowsRegistrySteamRootFinder();
        yield new WindowsSteamLibraryLocator(finder, reader);
      }
      case Platform.Linux l -> new LinuxSteamLibraryLocator(env, reader);
    };
  }

  @Bean
  public Optional<ProtonPrefixResolver> protonPrefixResolver(
      Platform platform, SteamLibraryLocator locator) {
    return switch (platform) {
      case Platform.Linux l -> Optional.of(new LinuxProtonPrefixResolver(locator));
      case Platform.Windows w -> Optional.empty();
    };
  }

  @Bean
  public Optional<CrossPlayResolverFactory> crossPlayResolverFactory(Platform platform) {
    return switch (platform) {
      case Platform.Linux l ->
          Optional.of(
              prefixUserDir ->
                  new PlaceholderResolver.Windows(new ProtonEnvironment(prefixUserDir)));
      case Platform.Windows w -> Optional.empty();
    };
  }
}
