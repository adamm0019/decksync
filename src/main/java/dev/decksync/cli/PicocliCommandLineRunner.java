package dev.decksync.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

/**
 * Drives Picocli from Spring's {@link CommandLineRunner} lifecycle. Kept out of the
 * {@code @SpringBootApplication} class so slice tests like {@code @WebMvcTest} don't have to
 * satisfy Picocli's {@link IFactory} dependency just to exercise a controller.
 */
@Component
public class PicocliCommandLineRunner implements CommandLineRunner, ExitCodeGenerator {

  private final IFactory factory;
  private final DeckSyncCommand rootCommand;
  private int exitCode;

  public PicocliCommandLineRunner(IFactory factory, DeckSyncCommand rootCommand) {
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
}
