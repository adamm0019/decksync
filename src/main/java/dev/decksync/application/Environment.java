package dev.decksync.application;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Read-only view of host environment variables and a handful of common locations. Placeholder
 * resolvers depend on this port so they stay deterministic under test — the real adapter in {@code
 * infrastructure} reads {@link System#getenv} and {@link System#getProperty}.
 */
public interface Environment {

  /** Returns the value of the named environment variable, or empty if unset or blank. */
  Optional<String> get(String name);

  /** Absolute path to the current user's home directory. */
  Path home();

  /** Current user name. Used by Linux Proton prefix expansion to reconstruct Windows paths. */
  String userName();
}
