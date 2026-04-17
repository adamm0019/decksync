package dev.decksync.application;

import dev.decksync.domain.Platform;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-platform path override for a single game. Each entry stores the raw string the user wrote in
 * {@code overrides.yml} — absoluteness isn't validated here because a Windows path isn't absolute
 * when parsed on Linux and vice versa. The consumer on the running platform picks its own value via
 * {@link #forPlatform(Platform)}.
 */
public record PlatformOverride(Optional<String> windows, Optional<String> linux) {

  public PlatformOverride {
    Objects.requireNonNull(windows, "windows");
    Objects.requireNonNull(linux, "linux");
  }

  public Optional<String> forPlatform(Platform platform) {
    Objects.requireNonNull(platform, "platform");
    return switch (platform) {
      case Platform.Windows w -> windows;
      case Platform.Linux l -> linux;
    };
  }
}
