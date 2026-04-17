package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FakeEnvironmentTest {

  @Test
  void getReturnsSetValue(@TempDir Path tempDir) {
    FakeEnvironment env = new FakeEnvironment(tempDir, "alice").set("APPDATA", "X");

    assertThat(env.get("APPDATA")).contains("X");
  }

  @Test
  void getReturnsEmptyWhenUnset(@TempDir Path tempDir) {
    FakeEnvironment env = new FakeEnvironment(tempDir, "alice");

    assertThat(env.get("MISSING")).isEmpty();
  }

  @Test
  void getReturnsEmptyWhenBlank(@TempDir Path tempDir) {
    FakeEnvironment env = new FakeEnvironment(tempDir, "alice").set("EMPTY", "   ");

    assertThat(env.get("EMPTY")).isEmpty();
  }

  @Test
  void homeAndUserNameReturnConstructorValues(@TempDir Path tempDir) {
    FakeEnvironment env = new FakeEnvironment(tempDir, "alice");

    assertThat(env.home()).isEqualTo(tempDir);
    assertThat(env.userName()).isEqualTo("alice");
  }
}
