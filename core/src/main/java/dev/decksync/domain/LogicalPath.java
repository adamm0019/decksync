package dev.decksync.domain;

/**
 * Game-relative save path with forward slashes, e.g. {@code saves/slot_0.sav}. The canonical form
 * used in protocol messages and equality comparisons. Case is preserved exactly.
 *
 * <p>Callers are responsible for converting native separators ({@code \} on Windows) to {@code /}
 * before constructing — this record rejects backslashes rather than silently rewriting them so path
 * provenance stays explicit.
 */
public record LogicalPath(String path) {

  public LogicalPath {
    if (path == null) {
      throw new IllegalArgumentException("LogicalPath must not be null");
    }
    if (path.isEmpty()) {
      throw new IllegalArgumentException("LogicalPath must not be empty");
    }
    if (path.indexOf('\\') >= 0) {
      throw new IllegalArgumentException("LogicalPath must use forward slashes — got: " + path);
    }
    if (path.startsWith("/")) {
      throw new IllegalArgumentException("LogicalPath must be relative — got: " + path);
    }
    if (path.endsWith("/")) {
      throw new IllegalArgumentException("LogicalPath must not end with a slash — got: " + path);
    }
    if (path.contains("//")) {
      throw new IllegalArgumentException(
          "LogicalPath must not contain empty segments — got: " + path);
    }
    for (String segment : path.split("/", -1)) {
      if (segment.equals("..") || segment.equals(".")) {
        throw new IllegalArgumentException(
            "LogicalPath must not contain '.' or '..' segments — got: " + path);
      }
    }
  }
}
