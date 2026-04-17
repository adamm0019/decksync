package dev.decksync.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class SystemEnvironmentTest {

  @Test
  void homeMirrorsUserHomeSystemProperty() {
    SystemEnvironment env = new SystemEnvironment();

    assertThat(env.home()).isEqualTo(Paths.get(System.getProperty("user.home")));
  }

  @Test
  void userNameMirrorsUserNameSystemProperty() {
    SystemEnvironment env = new SystemEnvironment();

    assertThat(env.userName()).isEqualTo(System.getProperty("user.name"));
  }

  @Test
  void getReturnsEmptyForDefinitelyUnsetVar() {
    SystemEnvironment env = new SystemEnvironment();

    assertThat(env.get("DECKSYNC_DEFINITELY_UNSET_" + System.nanoTime())).isEmpty();
  }
}
