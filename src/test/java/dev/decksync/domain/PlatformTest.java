package dev.decksync.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlatformTest {

  @Test
  void windowsAndLinuxAreDistinctValues() {
    Platform windows = new Platform.Windows();
    Platform linux = new Platform.Linux();

    assertThat(windows).isNotEqualTo(linux);
    assertThat(windows).isEqualTo(new Platform.Windows());
    assertThat(linux).isEqualTo(new Platform.Linux());
  }

  @Test
  void exhaustiveSwitchRequiresNoDefaultBranch() {
    Platform p = new Platform.Windows();

    String tag =
        switch (p) {
          case Platform.Windows w -> "windows";
          case Platform.Linux l -> "linux";
        };

    assertThat(tag).isEqualTo("windows");
  }
}
