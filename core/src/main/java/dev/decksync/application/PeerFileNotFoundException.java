package dev.decksync.application;

/**
 * The peer responded 404 for a {@link PeerClient} lookup — either the game isn't installed on that
 * side or the requested logical path doesn't exist in its save directory. Callers typically treat
 * this as a skip rather than a fatal sync failure.
 */
public class PeerFileNotFoundException extends PeerException {

  private static final long serialVersionUID = 1L;

  public PeerFileNotFoundException(String message) {
    super(message);
  }
}
