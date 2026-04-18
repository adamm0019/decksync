package dev.decksync.application;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Port for resolving a local filesystem path to a game's header art. Implementations are free to
 * fetch from a remote source, render from a bundled asset, or return empty — callers only need a
 * {@link Path} they can hand to an {@code ImageView}. The method is allowed to block; the GUI
 * invokes it from virtual threads.
 */
public interface GameArt {

  Optional<Path> fetch(long steamAppId);
}
