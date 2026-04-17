package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import dev.decksync.domain.Platform;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformOverrideTest {

  @Test
  void returnsWindowsValueForWindowsPlatform() {
    PlatformOverride override =
        new PlatformOverride(Optional.of("D:\\Saves"), Optional.of("/home/deck/saves"));

    assertThat(override.forPlatform(new Platform.Windows())).contains("D:\\Saves");
  }

  @Test
  void returnsLinuxValueForLinuxPlatform() {
    PlatformOverride override =
        new PlatformOverride(Optional.of("D:\\Saves"), Optional.of("/home/deck/saves"));

    assertThat(override.forPlatform(new Platform.Linux())).contains("/home/deck/saves");
  }

  @Test
  void returnsEmptyWhenRequestedPlatformNotSet() {
    PlatformOverride onlyWindows = new PlatformOverride(Optional.of("D:\\Saves"), Optional.empty());

    assertThat(onlyWindows.forPlatform(new Platform.Linux())).isEmpty();
  }

  @Test
  void rejectsNullArgs() {
    assertThatNullPointerException().isThrownBy(() -> new PlatformOverride(null, Optional.empty()));
    assertThatNullPointerException().isThrownBy(() -> new PlatformOverride(Optional.empty(), null));
  }
}
