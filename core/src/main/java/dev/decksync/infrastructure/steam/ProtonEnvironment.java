package dev.decksync.infrastructure.steam;

import dev.decksync.application.Environment;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link Environment} whose "Windows" env-var lookups resolve to directories inside a Proton Wine
 * prefix. Plugged into {@code PlaceholderResolver.Windows} on Linux, this lets the same manifest
 * rule that resolves to {@code C:\Users\<user>\AppData\Roaming\<Game>} on a Windows host resolve to
 * {@code <prefix>/drive_c/users/steamuser/AppData/Roaming/<Game>} on a SteamOS Deck.
 *
 * <p>Wine canonicalises the in-prefix user name to {@code steamuser} regardless of the host Linux
 * user — that's deliberate; the same prefix directory layout is expected across machines.
 */
public final class ProtonEnvironment implements Environment {

  private static final String STEAMUSER = "steamuser";

  private final Path prefixHome;
  private final Map<String, String> vars;

  public ProtonEnvironment(Path prefixUserDir) {
    Objects.requireNonNull(prefixUserDir, "prefixUserDir");
    if (!prefixUserDir.isAbsolute()) {
      throw new IllegalArgumentException(
          "Proton prefix user dir must be absolute — got: " + prefixUserDir);
    }
    this.prefixHome = prefixUserDir;
    Path driveC = prefixUserDir.getParent().getParent();
    this.vars =
        Map.of(
            "APPDATA", prefixUserDir.resolve("AppData/Roaming").toString(),
            "LOCALAPPDATA", prefixUserDir.resolve("AppData/Local").toString(),
            "PUBLIC", driveC.resolve("users/Public").toString(),
            "WINDIR", driveC.resolve("windows").toString(),
            "SYSTEMROOT", driveC.resolve("windows").toString());
  }

  @Override
  public Optional<String> get(String name) {
    Objects.requireNonNull(name, "name");
    return Optional.ofNullable(vars.get(name));
  }

  @Override
  public Path home() {
    return prefixHome;
  }

  @Override
  public String userName() {
    return STEAMUSER;
  }
}
