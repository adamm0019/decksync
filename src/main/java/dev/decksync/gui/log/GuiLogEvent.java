package dev.decksync.gui.log;

import java.time.Instant;

/**
 * A single log line captured by the in-process drawer appender. Logger name is shortened to its
 * final segment before it reaches this record — the drawer prefers {@code SyncService} over {@code
 * dev.decksync.application.SyncService} so the list stays readable.
 */
public record GuiLogEvent(Instant when, String level, String logger, String message) {}
