package dev.decksync.infrastructure.steam;

import dev.decksync.application.Environment;
import dev.decksync.application.SteamLibrary;
import dev.decksync.application.SteamLibraryLocator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Linux adapter for {@link SteamLibraryLocator}. Probes the two supported Steam install layouts in
 * preference order — native first, Flatpak second — and reads {@code libraryfolders.vdf} from the
 * first candidate that has one. The native path is tried first because it's the canonical install
 * when both exist (a user might have both the package and the Flatpak installed; syncing the native
 * install is almost always what they want). The chosen candidate is logged so users can diagnose
 * "wrong Steam picked up" issues without attaching a debugger.
 */
public final class LinuxSteamLibraryLocator implements SteamLibraryLocator {

  private static final Logger log = LoggerFactory.getLogger(LinuxSteamLibraryLocator.class);

  private static final String NATIVE_RELATIVE = ".steam/steam";
  private static final String FLATPAK_RELATIVE = ".var/app/com.valvesoftware.Steam/.steam/steam";

  private final Environment env;
  private final LibraryFoldersVdfReader reader;

  public LinuxSteamLibraryLocator(Environment env, LibraryFoldersVdfReader reader) {
    this.env = Objects.requireNonNull(env, "env");
    this.reader = Objects.requireNonNull(reader, "reader");
  }

  @Override
  public List<SteamLibrary> locate() {
    Path home = env.home();
    for (Candidate candidate :
        List.of(
            new Candidate("native", home.resolve(NATIVE_RELATIVE)),
            new Candidate("flatpak", home.resolve(FLATPAK_RELATIVE)))) {
      Path vdf = candidate.root.resolve("steamapps").resolve("libraryfolders.vdf");
      if (!Files.isRegularFile(vdf)) {
        continue;
      }
      try {
        List<SteamLibrary> libs = reader.read(vdf);
        log.info("Steam libraries sourced from {} install at {}", candidate.label, candidate.root);
        return libs;
      } catch (IOException e) {
        log.warn("Failed to read Steam libraryfolders.vdf at {}: {}", vdf, e.getMessage());
      }
    }
    return List.of();
  }

  private record Candidate(String label, Path root) {}
}
