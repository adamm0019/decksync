package dev.decksync.application;

import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import java.util.List;

/**
 * Application-layer port for talking to another DeckSync peer. Mirrors the three endpoints the web
 * layer exposes, so the adapter is a thin RestClient-shaped implementation and this interface is
 * all the sync service needs to know.
 *
 * <p>Implementations throw {@link PeerFileNotFoundException} when the remote reports the game or
 * file is not installed; any other failure surfaces as an unchecked {@link PeerException} so the
 * sync driver can decide whether to log-and-continue or abort the run.
 */
public interface PeerClient {

  List<GameId> listGames();

  Manifest fetchManifest(GameId game);

  /**
   * Downloads the file identified by {@code (game, path)} and returns the raw bytes. Phase 1 keeps
   * this in memory because individual save files are small (typically &lt; 50 MB); streaming would
   * complicate the atomic-write path for no meaningful win at this size.
   */
  byte[] downloadFile(GameId game, LogicalPath path);
}
