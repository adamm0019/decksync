package dev.decksync.application;

import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.domain.Platform;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform-aware catalog. Walks every Steam library the locator reports, matches each installed
 * appid against the Ludusavi manifest, and resolves the game's save directory via:
 *
 * <ol>
 *   <li>a user override from {@code overrides.yml} if set for this platform — authoritative, no
 *       fall-through even when the override is unusable (logged and the game is dropped);
 *   <li>otherwise a native save-path rule that matches the host platform + Steam store;
 *   <li>otherwise — on Linux only — a Windows rule expanded inside the game's Proton prefix.
 * </ol>
 *
 * The cross-play branch (3) is what lets a save sync'd from a Windows PC land at the right place on
 * a SteamOS Deck without the user configuring anything per-machine. It only fires when the
 * per-appid Proton prefix actually exists — a never-launched game is silently dropped because
 * there's nowhere valid to place saves yet.
 */
public final class DefaultGameCatalog implements GameCatalog {

  private static final Logger log = LoggerFactory.getLogger(DefaultGameCatalog.class);

  private static final String STEAM_STORE = "steam";

  private final Platform platform;
  private final SteamLibraryLocator libraryLocator;
  private final ManifestIndex manifest;
  private final PlaceholderResolver nativeResolver;
  private final Overrides overrides;
  private final Optional<ProtonPrefixResolver> protonPrefixResolver;
  private final Optional<CrossPlayResolverFactory> crossPlayFactory;

  public DefaultGameCatalog(
      Platform platform,
      SteamLibraryLocator libraryLocator,
      ManifestIndex manifest,
      PlaceholderResolver nativeResolver,
      Overrides overrides,
      Optional<ProtonPrefixResolver> protonPrefixResolver,
      Optional<CrossPlayResolverFactory> crossPlayFactory) {
    this.platform = Objects.requireNonNull(platform, "platform");
    this.libraryLocator = Objects.requireNonNull(libraryLocator, "libraryLocator");
    this.manifest = Objects.requireNonNull(manifest, "manifest");
    this.nativeResolver = Objects.requireNonNull(nativeResolver, "nativeResolver");
    this.overrides = Objects.requireNonNull(overrides, "overrides");
    this.protonPrefixResolver =
        Objects.requireNonNull(protonPrefixResolver, "protonPrefixResolver");
    this.crossPlayFactory = Objects.requireNonNull(crossPlayFactory, "crossPlayFactory");
  }

  @Override
  public Map<GameId, AbsolutePath> resolveInstalled() {
    Map<GameId, AbsolutePath> out = new LinkedHashMap<>();
    for (SteamLibrary library : libraryLocator.locate()) {
      for (Long appId : library.appIds()) {
        resolveGame(library, appId).ifPresent(path -> out.put(new GameId.SteamAppId(appId), path));
      }
    }
    return Collections.unmodifiableMap(out);
  }

  private Optional<AbsolutePath> resolveGame(SteamLibrary library, long appId) {
    GameId id = new GameId.SteamAppId(appId);
    Optional<String> rawOverride = overrides.forGame(id, platform);
    if (rawOverride.isPresent()) {
      return parseOverride(id, rawOverride.get());
    }
    ManifestEntry entry = manifest.findBySteamAppId(appId).orElse(null);
    if (entry == null) {
      return Optional.empty();
    }
    Path installBase = findInstallBase(library, entry).orElse(null);
    if (installBase == null) {
      return Optional.empty();
    }
    ExpansionContext ctx =
        new ExpansionContext(entry.name(), Optional.empty(), library.path(), installBase);
    return resolveNative(entry, ctx).or(() -> resolveCrossPlay(entry, ctx, appId));
  }

  private Optional<AbsolutePath> parseOverride(GameId id, String raw) {
    try {
      Path p = Path.of(raw);
      if (!p.isAbsolute()) {
        log.warn("Override for {} ignored — not absolute on {}: {}", id, platform, raw);
        return Optional.empty();
      }
      return Optional.of(new AbsolutePath(p));
    } catch (InvalidPathException e) {
      log.warn("Override for {} ignored — unparseable path: {}", id, raw);
      return Optional.empty();
    }
  }

  private Optional<Path> findInstallBase(SteamLibrary library, ManifestEntry entry) {
    Path common = library.path().resolve("steamapps").resolve("common");
    for (String dir : entry.installDirs()) {
      Path candidate = common.resolve(dir);
      if (Files.isDirectory(candidate)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }

  private Optional<AbsolutePath> resolveNative(ManifestEntry entry, ExpansionContext ctx) {
    return pickRule(entry, platformId(platform))
        .flatMap(rule -> expandToAbsolute(nativeResolver, rule.template(), ctx));
  }

  private Optional<AbsolutePath> resolveCrossPlay(
      ManifestEntry entry, ExpansionContext ctx, long appId) {
    if (!(platform instanceof Platform.Linux)) {
      return Optional.empty();
    }
    if (protonPrefixResolver.isEmpty() || crossPlayFactory.isEmpty()) {
      return Optional.empty();
    }
    Optional<SavePathRule> windowsRule = pickRule(entry, "windows");
    if (windowsRule.isEmpty()) {
      return Optional.empty();
    }
    Optional<Path> prefix = protonPrefixResolver.get().resolve(appId);
    if (prefix.isEmpty()) {
      return Optional.empty();
    }
    PlaceholderResolver windowsResolver = crossPlayFactory.get().forProtonPrefix(prefix.get());
    return expandToAbsolute(windowsResolver, windowsRule.get().template(), ctx);
  }

  private static Optional<SavePathRule> pickRule(ManifestEntry entry, String osTag) {
    for (SavePathRule rule : entry.savePaths()) {
      if (ruleMatches(rule, osTag)) {
        return Optional.of(rule);
      }
    }
    return Optional.empty();
  }

  private static boolean ruleMatches(SavePathRule rule, String osTag) {
    if (rule.when().isEmpty()) {
      return true;
    }
    for (SavePathRule.WhenCondition clause : rule.when()) {
      boolean osOk = clause.os().map(osTag::equalsIgnoreCase).orElse(true);
      boolean storeOk = clause.store().map(STEAM_STORE::equalsIgnoreCase).orElse(true);
      if (osOk && storeOk) {
        return true;
      }
    }
    return false;
  }

  private static Optional<AbsolutePath> expandToAbsolute(
      PlaceholderResolver resolver, String template, ExpansionContext ctx) {
    try {
      Path expanded = Path.of(resolver.expand(template, ctx));
      if (!expanded.isAbsolute()) {
        return Optional.empty();
      }
      return Optional.of(new AbsolutePath(expanded));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static String platformId(Platform platform) {
    return switch (platform) {
      case Platform.Windows w -> "windows";
      case Platform.Linux l -> "linux";
    };
  }
}
