package dev.decksync.infrastructure.platform;

import dev.decksync.application.Environment;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Default {@link Environment} adapter backed by the JVM's process environment. Treats blank values
 * as unset — Windows sometimes exports empty {@code %VAR%} strings that are not meaningful for
 * placeholder expansion.
 */
public final class SystemEnvironment implements Environment {

  @Override
  public Optional<String> get(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value);
  }

  @Override
  public Path home() {
    return Paths.get(System.getProperty("user.home"));
  }

  @Override
  public String userName() {
    return System.getProperty("user.name");
  }
}
