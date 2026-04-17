package dev.decksync.cli;

import dev.decksync.application.ConfigInitializer;
import dev.decksync.application.DeckSyncConfig;
import dev.decksync.application.Environment;
import dev.decksync.application.GameCatalog;
import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code decksync setup} — interactive first-run wizard. Prompts for peer URL, port, retention, and
 * the per-game watch list, writes {@code ~/.decksync/config.yml}, then runs OS-specific
 * configuration so the user doesn't have to paste firewall / scheduled-task / systemd snippets by
 * hand. Idempotent: re-running refuses to clobber an existing config without {@code --force}.
 */
@Component
@Command(
    name = "setup",
    mixinStandardHelpOptions = true,
    description = "Interactive first-run setup: config, firewall, autostart.")
public class SetupCommand implements Runnable {

  @Option(
      names = "--force",
      description = "Overwrite existing config.yml without prompting.",
      defaultValue = "false")
  boolean force;

  @Option(
      names = "--skip-os-setup",
      description = "Skip firewall and autostart registration — only write config.yml.",
      defaultValue = "false")
  boolean skipOsSetup;

  private final GameCatalog catalog;
  private final Environment env;
  private final BufferedReader in;
  private final PrintStream out;

  @Autowired
  public SetupCommand(GameCatalog catalog, Environment env) {
    this(
        catalog,
        env,
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
        System.out);
  }

  SetupCommand(GameCatalog catalog, Environment env, BufferedReader in, PrintStream out) {
    this.catalog = catalog;
    this.env = env;
    this.in = in;
    this.out = out;
  }

  @Override
  public void run() {
    Path configFile = env.home().resolve(".decksync/config.yml");
    if (Files.isRegularFile(configFile) && !force) {
      out.println(
          "Config already exists at " + configFile + " — re-run with --force to overwrite.");
      return;
    }

    out.println("DeckSync setup — press Enter to accept the default in brackets.");
    out.println();

    DeckSyncConfig config = promptConfig();
    writeConfig(configFile, config);
    out.println();
    out.println("Wrote " + configFile);

    if (!skipOsSetup) {
      runOsSetup(config);
    }

    out.println();
    out.println("Done. Try:  decksync status");
  }

  DeckSyncConfig promptConfig() {
    URI peerUrl = promptPeerUrl();
    int port = promptInt("Port", DeckSyncConfig.DEFAULT_PORT);
    int retention =
        promptInt("History snapshots to keep per game", DeckSyncConfig.DEFAULT_RETENTION);
    List<GameId> games = promptGames();
    return new DeckSyncConfig(peerUrl, games, port, retention);
  }

  private URI promptPeerUrl() {
    while (true) {
      out.print("Peer URL or IP (e.g. http://192.168.1.11:47824 or 192.168.1.11): ");
      out.flush();
      String line = readLine().trim();
      if (line.isBlank()) {
        out.println("  (required)");
        continue;
      }
      String candidate =
          line.matches("^https?://.*")
              ? line
              : "http://" + line + ":" + DeckSyncConfig.DEFAULT_PORT;
      try {
        URI uri = new URI(candidate);
        if (uri.getHost() == null || uri.getHost().isBlank()) {
          throw new URISyntaxException(candidate, "no host");
        }
        return uri;
      } catch (URISyntaxException e) {
        out.println("  not a valid URL — try again");
      }
    }
  }

  private int promptInt(String prompt, int defaultValue) {
    while (true) {
      out.print(prompt + " [" + defaultValue + "]: ");
      out.flush();
      String line = readLine().trim();
      if (line.isBlank()) {
        return defaultValue;
      }
      try {
        int n = Integer.parseInt(line);
        if (n < 1) {
          out.println("  must be positive");
          continue;
        }
        return n;
      } catch (NumberFormatException e) {
        out.println("  not a number");
      }
    }
  }

  private List<GameId> promptGames() {
    Map<GameId, AbsolutePath> resolved = catalog.resolveInstalled();
    if (resolved.isEmpty()) {
      out.println();
      out.println("No installed games resolved on this host — leaving games list empty.");
      return List.of();
    }
    List<GameId> sorted =
        resolved.keySet().stream().sorted(Comparator.comparing(SetupCommand::format)).toList();
    out.println();
    out.println("Resolved " + sorted.size() + " installed game(s):");
    for (int i = 0; i < sorted.size(); i++) {
      out.printf("  [%d] %s%n", i + 1, format(sorted.get(i)));
    }
    while (true) {
      out.print(
          "Games to sync — comma-separated numbers, 'all', or blank to sync everything both peers"
              + " have: ");
      out.flush();
      String line = readLine().trim();
      if (line.isBlank() || "all".equalsIgnoreCase(line)) {
        return List.of();
      }
      List<GameId> picked = new ArrayList<>();
      boolean ok = true;
      for (String part : line.split(",", -1)) {
        String token = part.trim();
        try {
          int idx = Integer.parseInt(token);
          if (idx < 1 || idx > sorted.size()) {
            out.println("  out-of-range selection: " + idx);
            ok = false;
            break;
          }
          picked.add(sorted.get(idx - 1));
        } catch (NumberFormatException e) {
          out.println("  not a number: " + token);
          ok = false;
          break;
        }
      }
      if (ok) {
        return List.copyOf(picked);
      }
    }
  }

  void writeConfig(Path file, DeckSyncConfig config) {
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(file, ConfigInitializer.render(config), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write " + file, e);
    }
  }

  private void runOsSetup(DeckSyncConfig config) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      runWindowsSetup(config);
    } else if (os.contains("linux")) {
      runLinuxSetup(config);
    } else {
      out.println();
      out.println("Skipping OS-specific setup — unsupported host OS: " + os);
    }
  }

  private void runWindowsSetup(DeckSyncConfig config) {
    out.println();
    out.println("Adding Windows firewall rule (TCP " + config.port() + ", private profile)...");
    int fw =
        exec(
            List.of(
                "netsh",
                "advfirewall",
                "firewall",
                "add",
                "rule",
                "name=DeckSync",
                "dir=in",
                "action=allow",
                "protocol=TCP",
                "localport=" + config.port(),
                "profile=private"));
    if (fw == 0) {
      out.println("  added");
    } else {
      out.println(
          "  failed (exit "
              + fw
              + ") — this usually means the shell isn't elevated. Run from an admin PowerShell:");
      out.println(
          "    netsh advfirewall firewall add rule name=\"DeckSync\" dir=in action=allow"
              + " protocol=TCP localport="
              + config.port()
              + " profile=private");
    }

    Optional<String> launcher = findLauncher();
    if (launcher.isEmpty()) {
      out.println(
          "Skipping scheduled task — daemon not launched from a packaged executable (dev run?)."
              + " Register by hand from the README once installed.");
      return;
    }
    String taskCmd = "\"" + launcher.get() + "\" serve --log.format=json";
    out.println("Registering scheduled task 'DeckSync' (runs at login)...");
    int tc =
        exec(
            List.of(
                "schtasks",
                "/Create",
                "/TN",
                "DeckSync",
                "/SC",
                "ONLOGON",
                "/RL",
                "LEAST",
                "/TR",
                taskCmd,
                "/F"));
    if (tc == 0) {
      out.println("  registered");
    } else {
      out.println(
          "  failed (exit " + tc + ") — see the scheduled-task XML in the README as a fallback.");
    }
  }

  private void runLinuxSetup(DeckSyncConfig config) {
    out.println();
    int firewalldActive = exec(List.of("systemctl", "is-active", "--quiet", "firewalld"));
    if (firewalldActive == 0) {
      out.println("Adding firewalld rule (TCP " + config.port() + ")...");
      int rc = exec(List.of("firewall-cmd", "--permanent", "--add-port=" + config.port() + "/tcp"));
      if (rc == 0) {
        exec(List.of("firewall-cmd", "--reload"));
        out.println("  added");
      } else {
        out.println(
            "  failed (exit "
                + rc
                + ") — try: sudo firewall-cmd --permanent --add-port="
                + config.port()
                + "/tcp && sudo firewall-cmd --reload");
      }
    } else {
      out.println("firewalld not active — no firewall rule needed (SteamOS default).");
    }

    Optional<String> launcher = findLauncher();
    if (launcher.isEmpty()) {
      out.println(
          "Skipping systemd service — daemon not launched from a packaged executable (dev run?)."
              + " Write the unit file by hand from the README once installed.");
      return;
    }
    Path serviceFile = env.home().resolve(".config/systemd/user/decksync.service");
    String unit =
        ("[Unit]\n"
            + "Description=DeckSync save sync daemon\n"
            + "After=network-online.target\n"
            + "Wants=network-online.target\n"
            + "\n"
            + "[Service]\n"
            + "Type=simple\n"
            + "ExecStart="
            + launcher.get()
            + " serve --log.format=json\n"
            + "Restart=on-failure\n"
            + "RestartSec=10s\n"
            + "\n"
            + "[Install]\n"
            + "WantedBy=default.target\n");
    try {
      Path parent = serviceFile.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(serviceFile, unit, StandardCharsets.UTF_8);
      out.println("Wrote " + serviceFile);
    } catch (IOException e) {
      out.println("Writing service file failed: " + e.getMessage());
      return;
    }
    exec(List.of("systemctl", "--user", "daemon-reload"));
    int en = exec(List.of("systemctl", "--user", "enable", "--now", "decksync"));
    if (en == 0) {
      out.println("Service enabled and started.");
    } else {
      out.println("  enable failed (exit " + en + ") — check: journalctl --user -u decksync -n 50");
    }
    out.println(
        "Keep it running through sleep / logout:  loginctl enable-linger " + env.userName());
  }

  // The scheduled task / systemd unit both need the *packaged launcher* path — when the daemon
  // runs via `./gradlew bootRun` or a raw `java -jar`, the reported command is `java` / `java.exe`,
  // which would make for a broken autostart. Return empty in that case so the caller can skip.
  private static Optional<String> findLauncher() {
    return ProcessHandle.current()
        .info()
        .command()
        .filter(
            path -> {
              String lower = path.toLowerCase(Locale.ROOT);
              return !lower.endsWith("java.exe")
                  && !lower.endsWith("/java")
                  && !lower.endsWith("\\java");
            });
  }

  private int exec(List<String> command) {
    try {
      Process p = new ProcessBuilder(command).inheritIO().start();
      return p.waitFor();
    } catch (IOException e) {
      return -1;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private String readLine() {
    try {
      String line = in.readLine();
      return line == null ? "" : line;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String format(GameId id) {
    return switch (id) {
      case GameId.SteamAppId s -> "steam:" + s.appid();
      case GameId.Slug s -> s.value();
    };
  }
}
