package dev.decksync.cli;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Component
@Command(
    name = "decksync",
    mixinStandardHelpOptions = true,
    version = "decksync 0.1.0-SNAPSHOT",
    description = "LAN peer-to-peer game save sync for Windows and SteamOS.",
    subcommands = {ListGamesCommand.class, ScanCommand.class, ServeCommand.class})
public class DeckSyncCommand implements Runnable {

  @Spec private CommandSpec spec;

  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }
}
