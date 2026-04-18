package dev.decksync;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DeckSyncApplication {

  public static void main(String[] args) {
    String[] translated = translateLogFormat(args);
    SpringApplication app = new SpringApplication(DeckSyncApplication.class);
    // Only the `serve` subcommand wants Tomcat running — every other CLI invocation
    // (list-games, scan, …) should start, do its work, and exit. We peek at argv
    // ahead of Spring so the decision is made before the context refreshes and we
    // don't pay the Tomcat startup cost on every one-shot command.
    app.setWebApplicationType(
        isServeInvocation(translated) ? WebApplicationType.SERVLET : WebApplicationType.NONE);
    // The gui subcommand is long-running and wants mDNS discovery running so
    // the setup wizard can populate its "Find peer" step, but it doesn't serve
    // HTTP — only listens. Activate a dedicated profile so GuiDiscoveryConfiguration
    // picks it up without piggybacking on WebApplicationType (which would boot Tomcat).
    if (isGuiInvocation(translated)) {
      app.setAdditionalProfiles("gui");
    }
    app.setBannerMode(Banner.Mode.OFF);
    System.exit(SpringApplication.exit(app.run(translated)));
  }

  private static boolean isServeInvocation(String[] args) {
    return firstSubcommand(args, "serve");
  }

  private static boolean isGuiInvocation(String[] args) {
    return firstSubcommand(args, "gui");
  }

  private static boolean firstSubcommand(String[] args, String name) {
    for (String arg : args) {
      if (arg == null || arg.isEmpty() || arg.startsWith("-")) {
        continue;
      }
      return name.equals(arg);
    }
    return false;
  }

  /**
   * Expand the user-facing {@code --log.format=json} shorthand into Spring Boot 3.4's built-in
   * structured-logging property. Keeping this translation in {@code main} means picocli doesn't
   * have to model the flag — it's a process-level concern, not a subcommand option. Any arg we
   * don't recognise is passed through unchanged.
   */
  private static String[] translateLogFormat(String[] args) {
    List<String> out = new ArrayList<>(args.length);
    for (String arg : args) {
      if (arg == null) {
        continue;
      }
      if ("--log.format=json".equals(arg)) {
        out.add("--logging.structured.format.console=ecs");
      } else {
        out.add(arg);
      }
    }
    return out.toArray(new String[0]);
  }
}
