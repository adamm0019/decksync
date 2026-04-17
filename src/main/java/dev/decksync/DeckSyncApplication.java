package dev.decksync;

import dev.decksync.cli.DeckSyncCommand;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

@SpringBootApplication
public class DeckSyncApplication implements CommandLineRunner, ExitCodeGenerator {

  private final IFactory factory;
  private final DeckSyncCommand rootCommand;
  private int exitCode;

  public DeckSyncApplication(IFactory factory, DeckSyncCommand rootCommand) {
    this.factory = factory;
    this.rootCommand = rootCommand;
  }

  @Override
  public void run(String... args) {
    exitCode = new CommandLine(rootCommand, factory).execute(args);
  }

  @Override
  public int getExitCode() {
    return exitCode;
  }

  public static void main(String[] args) {
    SpringApplication app = new SpringApplication(DeckSyncApplication.class);
    // Only the `serve` subcommand wants Tomcat running — every other CLI invocation
    // (list-games, scan, …) should start, do its work, and exit. We peek at argv
    // ahead of Spring so the decision is made before the context refreshes and we
    // don't pay the Tomcat startup cost on every one-shot command.
    app.setWebApplicationType(
        isServeInvocation(args) ? WebApplicationType.SERVLET : WebApplicationType.NONE);
    app.setBannerMode(Banner.Mode.OFF);
    System.exit(SpringApplication.exit(app.run(args)));
  }

  private static boolean isServeInvocation(String[] args) {
    for (String arg : args) {
      if (arg == null || arg.isEmpty() || arg.startsWith("-")) {
        continue;
      }
      return "serve".equals(arg);
    }
    return false;
  }
}
