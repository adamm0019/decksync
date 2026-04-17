package dev.decksync.application;

/**
 * Port for probing whether the configured peer is currently answering HTTP requests. Separate from
 * {@link PeerClient} so a failed probe doesn't share exception plumbing with routine manifest
 * fetches — {@code status} should never throw, it should just report.
 */
public interface PeerReachability {

  PeerStatus probe();
}
