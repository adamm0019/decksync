package dev.decksync.application;

/**
 * Base unchecked exception raised by {@link PeerClient} implementations when talking to a remote
 * peer fails in a way the caller might reasonably act on. Transport errors, timeouts, and protocol
 * surprises all funnel through this type; {@link PeerFileNotFoundException} is the one carved-out
 * subclass because callers often want to treat "not installed over there" as a skip rather than an
 * error.
 */
public class PeerException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public PeerException(String message) {
    super(message);
  }

  public PeerException(String message, Throwable cause) {
    super(message, cause);
  }
}
