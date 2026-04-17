package dev.decksync.infrastructure.steam;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Hand-written parser for Valve KeyValues (VDF) text. DeckSync only consumes a handful of VDF files
 * (most importantly {@code libraryfolders.vdf}) and none of them are large, so this keeps the whole
 * document in memory and treats it as a recursive-descent grammar over a char array.
 *
 * <p>Supported features: quoted and unquoted string tokens, nested {@code { }} sections, {@code //}
 * line comments, and the escape sequences Steam actually emits in Windows paths ({@code \\}, {@code
 * \"}, {@code \n}, {@code \t}, {@code \r}).
 */
public final class VdfParser {

  public VdfNode.Section parse(String text) {
    Objects.requireNonNull(text, "text");
    return new Cursor(text).parseTopLevel();
  }

  public VdfNode.Section parse(Reader reader) throws IOException {
    Objects.requireNonNull(reader, "reader");
    StringBuilder sb = new StringBuilder();
    char[] buf = new char[4096];
    int read;
    while ((read = reader.read(buf)) != -1) {
      sb.append(buf, 0, read);
    }
    return parse(sb.toString());
  }

  /** Recoverable parse error — callers decide whether to surface or fall back. */
  public static final class VdfParseException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public VdfParseException(String message) {
      super(message);
    }
  }

  private enum TokenKind {
    STRING,
    OPEN,
    CLOSE,
    EOF
  }

  private record Token(TokenKind kind, String value) {}

  private static final class Cursor {
    private final String src;
    private int pos;
    private Token lookahead;

    Cursor(String src) {
      this.src = src;
      this.pos = 0;
      this.lookahead = nextToken();
    }

    VdfNode.Section parseTopLevel() {
      VdfNode.Section section = parseEntries(false);
      if (lookahead.kind() != TokenKind.EOF) {
        throw new VdfParseException("Trailing tokens after top-level section");
      }
      return section;
    }

    private VdfNode.Section parseEntries(boolean braced) {
      Map<String, VdfNode> entries = new LinkedHashMap<>();
      while (lookahead.kind() == TokenKind.STRING) {
        String key = lookahead.value();
        lookahead = nextToken();
        VdfNode value;
        switch (lookahead.kind()) {
          case OPEN -> {
            lookahead = nextToken();
            value = parseEntries(true);
          }
          case STRING -> {
            value = new VdfNode.Value(lookahead.value());
            lookahead = nextToken();
          }
          default ->
              throw new VdfParseException(
                  "Expected value or '{' after key '" + key + "', got " + lookahead.kind());
        }
        entries.put(key, value);
      }
      if (braced) {
        if (lookahead.kind() != TokenKind.CLOSE) {
          throw new VdfParseException("Expected '}' to close section, got " + lookahead.kind());
        }
        lookahead = nextToken();
      }
      return new VdfNode.Section(entries);
    }

    private Token nextToken() {
      skipWhitespaceAndComments();
      if (pos >= src.length()) {
        return new Token(TokenKind.EOF, "");
      }
      char c = src.charAt(pos);
      if (c == '{') {
        pos++;
        return new Token(TokenKind.OPEN, "{");
      }
      if (c == '}') {
        pos++;
        return new Token(TokenKind.CLOSE, "}");
      }
      if (c == '"') {
        return readQuoted();
      }
      return readBareword();
    }

    private Token readQuoted() {
      pos++;
      StringBuilder sb = new StringBuilder();
      while (pos < src.length()) {
        char c = src.charAt(pos);
        if (c == '"') {
          pos++;
          return new Token(TokenKind.STRING, sb.toString());
        }
        if (c == '\\' && pos + 1 < src.length()) {
          char next = src.charAt(pos + 1);
          switch (next) {
            case '\\' -> sb.append('\\');
            case '"' -> sb.append('"');
            case 'n' -> sb.append('\n');
            case 't' -> sb.append('\t');
            case 'r' -> sb.append('\r');
            default -> {
              sb.append(c);
              sb.append(next);
            }
          }
          pos += 2;
        } else {
          sb.append(c);
          pos++;
        }
      }
      throw new VdfParseException("Unterminated quoted string");
    }

    private Token readBareword() {
      int start = pos;
      while (pos < src.length() && !isDelimiter(src.charAt(pos))) {
        pos++;
      }
      return new Token(TokenKind.STRING, src.substring(start, pos));
    }

    private void skipWhitespaceAndComments() {
      while (pos < src.length()) {
        char c = src.charAt(pos);
        if (Character.isWhitespace(c)) {
          pos++;
          continue;
        }
        if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
          while (pos < src.length() && src.charAt(pos) != '\n') {
            pos++;
          }
          continue;
        }
        break;
      }
    }

    private static boolean isDelimiter(char c) {
      return Character.isWhitespace(c) || c == '{' || c == '}' || c == '"';
    }
  }
}
