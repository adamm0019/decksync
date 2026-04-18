package dev.decksync.application;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link Environment} for tests. Mutable via {@link #set} so tests can stage values. */
public final class FakeEnvironment implements Environment {

  private final Map<String, String> vars = new HashMap<>();
  private Path home;
  private String userName;

  public FakeEnvironment(Path home, String userName) {
    this.home = home;
    this.userName = userName;
  }

  public FakeEnvironment set(String name, String value) {
    vars.put(name, value);
    return this;
  }

  public FakeEnvironment unset(String name) {
    vars.remove(name);
    return this;
  }

  @Override
  public Optional<String> get(String name) {
    String value = vars.get(name);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value);
  }

  @Override
  public Path home() {
    return home;
  }

  @Override
  public String userName() {
    return userName;
  }
}
