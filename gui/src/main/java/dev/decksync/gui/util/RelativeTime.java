package dev.decksync.gui.util;

import java.time.Duration;
import java.time.Instant;

/**
 * Renders an {@link Instant} relative to now in the "4m ago" / "3h ago" / "2d ago" form used on
 * dashboard cards and history rows. Deliberately imprecise — a single token per timestamp is all
 * the UI has room for and all the user wants to read.
 */
public final class RelativeTime {

  private RelativeTime() {}

  public static String format(Instant when, Instant now) {
    if (when == null) {
      return "Never synced";
    }
    Duration delta = Duration.between(when, now);
    if (delta.isNegative()) {
      // Clock skew between peers or a write from the future — don't lie to the user.
      return "Just now";
    }
    long seconds = delta.toSeconds();
    if (seconds < 45) {
      return "Just now";
    }
    long minutes = delta.toMinutes();
    if (minutes < 60) {
      return minutes + "m ago";
    }
    long hours = delta.toHours();
    if (hours < 48) {
      return hours + "h ago";
    }
    long days = delta.toDays();
    if (days < 30) {
      return days + "d ago";
    }
    long months = days / 30;
    if (months < 12) {
      return months + "mo ago";
    }
    long years = days / 365;
    return years + "y ago";
  }
}
