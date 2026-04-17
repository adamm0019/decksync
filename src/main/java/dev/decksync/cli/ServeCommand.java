package dev.decksync.cli;

import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

/**
 * {@code decksync serve} — starts the HTTP daemon and blocks until the JVM is interrupted. Picocli
 * subcommands normally return immediately, but Spring Boot's embedded Tomcat keeps the process
 * alive via non-daemon threads; we still block here so the {@link Runnable} doesn't complete and
 * trigger {@link org.springframework.boot.SpringApplication#exit} before anyone connects.
 *
 * <p>Phase 1 ships without TLS or authentication on purpose — see {@code docs/phase-1-plan.md}.
 * This command emits a loud WARN banner at startup so running it on anything other than a trusted
 * LAN is an obvious mistake, not a quiet one.
 */
@Component
@Command(
    name = "serve",
    mixinStandardHelpOptions = true,
    description = "Start the DeckSync HTTP daemon and block until interrupted.")
public class ServeCommand implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(ServeCommand.class);

  private final String bindAddress;
  private final int port;

  public ServeCommand(
      @Value("${server.address:0.0.0.0}") String bindAddress,
      @Value("${server.port:47824}") int port) {
    this.bindAddress = bindAddress;
    this.port = port;
  }

  @Override
  public void run() {
    log.warn("================================================================");
    log.warn("DeckSync daemon listening on http://{}:{}", bindAddress, port);
    log.warn("INSECURE: Phase 1 ships without TLS or auth.");
    log.warn("Only run this on a trusted LAN. Ctrl-C to stop.");
    log.warn("================================================================");

    CountDownLatch shutdown = new CountDownLatch(1);
    Runtime.getRuntime().addShutdownHook(new Thread(shutdown::countDown, "decksync-shutdown"));
    try {
      shutdown.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
