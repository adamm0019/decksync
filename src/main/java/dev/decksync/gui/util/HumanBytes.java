package dev.decksync.gui.util;

/** Byte-count formatter tuned for file lists: "14 B" / "820 KB" / "2.4 MB". */
public final class HumanBytes {

  private HumanBytes() {}

  public static String format(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    }
    if (bytes < 1024L * 1024L) {
      return (bytes / 1024L) + " KB";
    }
    if (bytes < 1024L * 1024L * 1024L) {
      double mb = bytes / 1024.0 / 1024.0;
      return String.format(mb >= 10 ? "%.0f MB" : "%.1f MB", mb);
    }
    double gb = bytes / 1024.0 / 1024.0 / 1024.0;
    return String.format(gb >= 10 ? "%.0f GB" : "%.1f GB", gb);
  }
}
