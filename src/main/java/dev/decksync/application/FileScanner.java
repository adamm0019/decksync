package dev.decksync.application;

import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.GameId;
import dev.decksync.domain.Manifest;

/**
 * Walks a resolved game save directory and produces a {@link Manifest}. Skips files that appear
 * still-being-written (recently modified or held open by the game) so we never hash partially
 * flushed bytes — the next scan will pick them up.
 */
public interface FileScanner {

  Manifest scan(GameId game, AbsolutePath root);
}
