package dev.decksync.domain;

/**
 * Runtime operating system. Sealed so the {@code *Resolver} classes can exhaustively pattern-match
 * on it at startup. Extensible to {@code Mac} in a future phase — Phase 1 ships Windows and Linux.
 */
public sealed interface Platform permits Platform.Windows, Platform.Linux {

  record Windows() implements Platform {}

  record Linux() implements Platform {}
}
