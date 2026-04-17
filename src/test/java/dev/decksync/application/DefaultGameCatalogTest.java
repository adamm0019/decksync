package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.domain.Platform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultGameCatalogTest {

  @Test
  void resolvesNativeWindowsGame(@TempDir Path tempDir) throws IOException {
    Path library = tempDir.resolve("library");
    Path installDir = library.resolve("steamapps/common/EldenRing");
    Files.createDirectories(installDir);
    Path home = tempDir.resolve("home");
    Files.createDirectories(home);

    FakeEnvironment env = new FakeEnvironment(home, "alice");
    env.set("APPDATA", home.resolve("AppData/Roaming").toString());

    ManifestEntry elden =
        new ManifestEntry(
            "Elden Ring",
            Optional.of(1245620L),
            Set.of("EldenRing"),
            List.of(
                new SavePathRule(
                    "<winAppData>/EldenRing",
                    Set.of("save"),
                    List.of(
                        new SavePathRule.WhenCondition(
                            Optional.of("windows"), Optional.empty())))));

    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Windows(),
            new PlaceholderResolver.Windows(env),
            List.of(new SteamLibrary(library, Set.of(1245620L))),
            ManifestIndex.from(List.of(elden)),
            Overrides.EMPTY,
            Optional.empty(),
            Optional.empty());

    Map<GameId, AbsolutePath> result = catalog.resolveInstalled();

    assertThat(result)
        .containsEntry(
            new GameId.SteamAppId(1245620L),
            new AbsolutePath(home.resolve("AppData/Roaming/EldenRing")));
  }

  @Test
  void resolvesNativeLinuxGame(@TempDir Path tempDir) throws IOException {
    Path library = tempDir.resolve("library");
    Path installDir = library.resolve("steamapps/common/DRG");
    Files.createDirectories(installDir);
    Path home = tempDir.resolve("home");
    Files.createDirectories(home);

    FakeEnvironment env = new FakeEnvironment(home, "deck");

    ManifestEntry drg =
        new ManifestEntry(
            "Deep Rock Galactic",
            Optional.of(548430L),
            Set.of("DRG"),
            List.of(
                new SavePathRule(
                    "<xdgData>/DRG/Saves",
                    Set.of(),
                    List.of(
                        new SavePathRule.WhenCondition(Optional.of("linux"), Optional.empty())))));

    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Linux(),
            new PlaceholderResolver.Linux(env),
            List.of(new SteamLibrary(library, Set.of(548430L))),
            ManifestIndex.from(List.of(drg)),
            Overrides.EMPTY,
            Optional.empty(),
            Optional.empty());

    Map<GameId, AbsolutePath> result = catalog.resolveInstalled();

    assertThat(result)
        .containsEntry(
            new GameId.SteamAppId(548430L),
            new AbsolutePath(home.resolve(".local/share/DRG/Saves")));
  }

  @Test
  void resolvesCrossPlayWindowsGameOnLinuxViaProtonPrefix(@TempDir Path tempDir)
      throws IOException {
    Path library = tempDir.resolve("library");
    Files.createDirectories(library.resolve("steamapps/common/EldenRing"));
    Path prefixUser = tempDir.resolve("prefix/drive_c/users/steamuser");
    Files.createDirectories(prefixUser);
    Path home = tempDir.resolve("home");
    Files.createDirectories(home);

    FakeEnvironment linuxEnv = new FakeEnvironment(home, "deck");
    FakeEnvironment winEnv = new FakeEnvironment(prefixUser, "steamuser");
    winEnv.set("APPDATA", prefixUser.resolve("AppData/Roaming").toString());

    ManifestEntry elden =
        new ManifestEntry(
            "Elden Ring",
            Optional.of(1245620L),
            Set.of("EldenRing"),
            List.of(
                new SavePathRule(
                    "<winAppData>/EldenRing",
                    Set.of(),
                    List.of(
                        new SavePathRule.WhenCondition(
                            Optional.of("windows"), Optional.empty())))));

    ProtonPrefixResolver prefixResolver = appId -> Optional.of(prefixUser);
    CrossPlayResolverFactory factory = pfx -> new PlaceholderResolver.Windows(winEnv);

    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Linux(),
            new PlaceholderResolver.Linux(linuxEnv),
            List.of(new SteamLibrary(library, Set.of(1245620L))),
            ManifestIndex.from(List.of(elden)),
            Overrides.EMPTY,
            Optional.of(prefixResolver),
            Optional.of(factory));

    Map<GameId, AbsolutePath> result = catalog.resolveInstalled();

    assertThat(result)
        .containsEntry(
            new GameId.SteamAppId(1245620L),
            new AbsolutePath(prefixUser.resolve("AppData/Roaming/EldenRing")));
  }

  @Test
  void skipsCrossPlayGameWhenProtonPrefixMissing(@TempDir Path tempDir) throws IOException {
    Path library = tempDir.resolve("library");
    Files.createDirectories(library.resolve("steamapps/common/EldenRing"));

    ManifestEntry elden =
        new ManifestEntry(
            "Elden Ring",
            Optional.of(1245620L),
            Set.of("EldenRing"),
            List.of(
                new SavePathRule(
                    "<winAppData>/EldenRing",
                    Set.of(),
                    List.of(
                        new SavePathRule.WhenCondition(
                            Optional.of("windows"), Optional.empty())))));

    ProtonPrefixResolver prefixResolver = appId -> Optional.empty();
    CrossPlayResolverFactory factory =
        pfx -> new PlaceholderResolver.Windows(new FakeEnvironment(pfx, "steamuser"));

    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Linux(),
            new PlaceholderResolver.Linux(new FakeEnvironment(tempDir.resolve("home"), "deck")),
            List.of(new SteamLibrary(library, Set.of(1245620L))),
            ManifestIndex.from(List.of(elden)),
            Overrides.EMPTY,
            Optional.of(prefixResolver),
            Optional.of(factory));

    assertThat(catalog.resolveInstalled()).isEmpty();
  }

  @Test
  void overrideWinsOverManifest(@TempDir Path tempDir) throws IOException {
    Path library = tempDir.resolve("library");
    Files.createDirectories(library.resolve("steamapps/common/EldenRing"));
    Path customSaves = tempDir.resolve("CustomSaves/EldenRing");

    ManifestEntry elden =
        new ManifestEntry(
            "Elden Ring",
            Optional.of(1245620L),
            Set.of("EldenRing"),
            List.of(new SavePathRule("<base>/default", Set.of(), List.of())));

    Overrides overrides =
        new Overrides(
            Map.of(
                new GameId.SteamAppId(1245620L),
                new PlatformOverride(Optional.of(customSaves.toString()), Optional.empty())));

    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Windows(),
            new PlaceholderResolver.Windows(new FakeEnvironment(tempDir, "alice")),
            List.of(new SteamLibrary(library, Set.of(1245620L))),
            ManifestIndex.from(List.of(elden)),
            overrides,
            Optional.empty(),
            Optional.empty());

    assertThat(catalog.resolveInstalled())
        .containsEntry(new GameId.SteamAppId(1245620L), new AbsolutePath(customSaves));
  }

  @Test
  void skipsGameWithoutManifestEntry(@TempDir Path tempDir) {
    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Windows(),
            new PlaceholderResolver.Windows(new FakeEnvironment(tempDir, "alice")),
            List.of(new SteamLibrary(tempDir, Set.of(9999L))),
            ManifestIndex.from(List.of()),
            Overrides.EMPTY,
            Optional.empty(),
            Optional.empty());

    assertThat(catalog.resolveInstalled()).isEmpty();
  }

  @Test
  void skipsGameWhenInstallDirMissing(@TempDir Path tempDir) throws IOException {
    Path library = tempDir.resolve("library");
    Files.createDirectories(library.resolve("steamapps/common"));

    ManifestEntry elden =
        new ManifestEntry(
            "Elden Ring",
            Optional.of(1245620L),
            Set.of("EldenRing"),
            List.of(new SavePathRule("<base>/default", Set.of(), List.of())));

    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Windows(),
            new PlaceholderResolver.Windows(new FakeEnvironment(tempDir, "alice")),
            List.of(new SteamLibrary(library, Set.of(1245620L))),
            ManifestIndex.from(List.of(elden)),
            Overrides.EMPTY,
            Optional.empty(),
            Optional.empty());

    assertThat(catalog.resolveInstalled()).isEmpty();
  }

  @Test
  void skipsGameWithNoMatchingRule(@TempDir Path tempDir) throws IOException {
    Path library = tempDir.resolve("library");
    Files.createDirectories(library.resolve("steamapps/common/NativeGame"));

    ManifestEntry game =
        new ManifestEntry(
            "Native Game",
            Optional.of(4242L),
            Set.of("NativeGame"),
            List.of(
                new SavePathRule(
                    "<base>/saves",
                    Set.of(),
                    List.of(
                        new SavePathRule.WhenCondition(Optional.of("mac"), Optional.empty())))));

    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Windows(),
            new PlaceholderResolver.Windows(new FakeEnvironment(tempDir, "alice")),
            List.of(new SteamLibrary(library, Set.of(4242L))),
            ManifestIndex.from(List.of(game)),
            Overrides.EMPTY,
            Optional.empty(),
            Optional.empty());

    assertThat(catalog.resolveInstalled()).isEmpty();
  }

  @Test
  void skipsNonAbsoluteOverridePath(@TempDir Path tempDir) throws IOException {
    Path library = tempDir.resolve("library");
    Files.createDirectories(library.resolve("steamapps/common/EldenRing"));

    ManifestEntry elden =
        new ManifestEntry(
            "Elden Ring",
            Optional.of(1245620L),
            Set.of("EldenRing"),
            List.of(new SavePathRule("<base>/saves", Set.of(), List.of())));

    Overrides overrides =
        new Overrides(
            Map.of(
                new GameId.SteamAppId(1245620L),
                new PlatformOverride(Optional.of("relative/path"), Optional.empty())));

    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Windows(),
            new PlaceholderResolver.Windows(new FakeEnvironment(tempDir, "alice")),
            List.of(new SteamLibrary(library, Set.of(1245620L))),
            ManifestIndex.from(List.of(elden)),
            overrides,
            Optional.empty(),
            Optional.empty());

    assertThat(catalog.resolveInstalled()).isEmpty();
  }

  @Test
  void iteratesMultipleLibrariesAndGames(@TempDir Path tempDir) throws IOException {
    Path libA = tempDir.resolve("libA");
    Path libB = tempDir.resolve("libB");
    Files.createDirectories(libA.resolve("steamapps/common/One"));
    Files.createDirectories(libB.resolve("steamapps/common/Two"));

    ManifestEntry one =
        new ManifestEntry(
            "One",
            Optional.of(100L),
            Set.of("One"),
            List.of(new SavePathRule("<base>/saves", Set.of(), List.of())));
    ManifestEntry two =
        new ManifestEntry(
            "Two",
            Optional.of(200L),
            Set.of("Two"),
            List.of(new SavePathRule("<base>/saves", Set.of(), List.of())));

    DefaultGameCatalog catalog =
        newCatalog(
            new Platform.Windows(),
            new PlaceholderResolver.Windows(new FakeEnvironment(tempDir, "alice")),
            List.of(new SteamLibrary(libA, Set.of(100L)), new SteamLibrary(libB, Set.of(200L))),
            ManifestIndex.from(List.of(one, two)),
            Overrides.EMPTY,
            Optional.empty(),
            Optional.empty());

    assertThat(catalog.resolveInstalled().keySet())
        .containsExactlyInAnyOrder(new GameId.SteamAppId(100L), new GameId.SteamAppId(200L));
  }

  private static DefaultGameCatalog newCatalog(
      Platform platform,
      PlaceholderResolver resolver,
      List<SteamLibrary> libraries,
      ManifestIndex manifest,
      Overrides overrides,
      Optional<ProtonPrefixResolver> protonResolver,
      Optional<CrossPlayResolverFactory> crossPlay) {
    return new DefaultGameCatalog(
        platform, () -> libraries, manifest, resolver, overrides, protonResolver, crossPlay);
  }
}
