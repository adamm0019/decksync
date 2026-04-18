package dev.decksync.infrastructure.steam;

import dev.decksync.application.SteamLibrary;
import dev.decksync.application.SteamLibraryLocator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Windows adapter for {@link SteamLibraryLocator}. Tries the Steam install path advertised by the
 * registry first; if that yields no readable {@code libraryfolders.vdf}, falls back to the default
 * install location {@code C:\Program Files (x86)\Steam}. Returns an empty list rather than throwing
 * when neither candidate has a readable registry file — Steam-not-installed is a valid state the
 * rest of the pipeline must tolerate.
 */
public final class WindowsSteamLibraryLocator implements SteamLibraryLocator {

  static final Path DEFAULT_FALLBACK_ROOT = Path.of("C:\\Program Files (x86)\\Steam");

  private final SteamRootFinder registryFinder;
  private final LibraryFoldersVdfReader reader;
  private final Path fallbackRoot;

  public WindowsSteamLibraryLocator(
      SteamRootFinder registryFinder, LibraryFoldersVdfReader reader) {
    this(registryFinder, reader, DEFAULT_FALLBACK_ROOT);
  }

  WindowsSteamLibraryLocator(
      SteamRootFinder registryFinder, LibraryFoldersVdfReader reader, Path fallbackRoot) {
    this.registryFinder = Objects.requireNonNull(registryFinder, "registryFinder");
    this.reader = Objects.requireNonNull(reader, "reader");
    this.fallbackRoot = Objects.requireNonNull(fallbackRoot, "fallbackRoot");
  }

  @Override
  public List<SteamLibrary> locate() {
    for (Path root : candidateRoots()) {
      Path vdf = root.resolve("steamapps").resolve("libraryfolders.vdf");
      if (!Files.isRegularFile(vdf)) {
        continue;
      }
      try {
        return reader.read(vdf);
      } catch (IOException ignored) {
        // Try the next candidate if a later one might succeed.
      }
    }
    return List.of();
  }

  private List<Path> candidateRoots() {
    Optional<Path> fromRegistry = registryFinder.find();
    List<Path> roots = new ArrayList<>(2);
    fromRegistry.ifPresent(roots::add);
    if (!fromRegistry.map(fallbackRoot::equals).orElse(false)) {
      roots.add(fallbackRoot);
    }
    return roots;
  }
}
