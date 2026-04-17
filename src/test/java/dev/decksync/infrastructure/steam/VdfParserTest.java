package dev.decksync.infrastructure.steam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import org.junit.jupiter.api.Test;

class VdfParserTest {

  private final VdfParser parser = new VdfParser();

  @Test
  void parsesEmptyDocument() {
    VdfNode.Section root = parser.parse("");

    assertThat(root.entries()).isEmpty();
  }

  @Test
  void parsesSingleQuotedKeyValue() {
    VdfNode.Section root = parser.parse("\"key\" \"value\"");

    assertThat(root.string("key")).contains("value");
  }

  @Test
  void parsesNestedSection() {
    VdfNode.Section root =
        parser.parse(
            """
            "outer"
            {
              "inner"
              {
                "leaf" "42"
              }
            }
            """);

    assertThat(
            root.section("outer").flatMap(s -> s.section("inner")).flatMap(s -> s.string("leaf")))
        .contains("42");
  }

  @Test
  void preservesEntryOrder() {
    VdfNode.Section root =
        parser.parse(
            """
            "libraryfolders"
            {
              "0" { "path" "A" }
              "1" { "path" "B" }
              "2" { "path" "C" }
            }
            """);

    assertThat(root.section("libraryfolders").get().entries().keySet())
        .containsExactly("0", "1", "2");
  }

  @Test
  void unescapesBackslashAndQuote() {
    VdfNode.Section root = parser.parse("\"path\" \"C:\\\\Games\\\\foo\"");

    assertThat(root.string("path")).contains("C:\\Games\\foo");
  }

  @Test
  void unescapesCommonControlSequences() {
    VdfNode.Section root = parser.parse("\"k\" \"a\\nb\\tc\\rd\"");

    assertThat(root.string("k")).contains("a\nb\tc\rd");
  }

  @Test
  void acceptsBarewordKeysAndValues() {
    VdfNode.Section root = parser.parse("foo bar");

    assertThat(root.string("foo")).contains("bar");
  }

  @Test
  void skipsLineComments() {
    VdfNode.Section root =
        parser.parse(
            """
            // a top-level comment
            "k1" "v1"
            "outer" // trailing comment
            {
              // inside comment
              "k2" "v2"
            }
            """);

    assertThat(root.string("k1")).contains("v1");
    assertThat(root.section("outer").flatMap(s -> s.string("k2"))).contains("v2");
  }

  @Test
  void parsesRealisticLibraryFoldersSnippet() {
    String doc =
        """
        "libraryfolders"
        {
          "0"
          {
            "path"        "C:\\\\Program Files (x86)\\\\Steam"
            "label"       ""
            "apps"
            {
              "440"   "12345"
              "730"   "67890"
            }
          }
          "1"
          {
            "path"  "D:\\\\SteamLibrary"
            "apps"
            {
              "1245620"  "0"
            }
          }
        }
        """;

    VdfNode.Section libs = parser.parse(doc).section("libraryfolders").orElseThrow();
    VdfNode.Section zero = libs.section("0").orElseThrow();

    assertThat(zero.string("path")).contains("C:\\Program Files (x86)\\Steam");
    assertThat(zero.section("apps").orElseThrow().entries().keySet())
        .containsExactlyInAnyOrder("440", "730");
    assertThat(libs.section("1").flatMap(s -> s.string("path"))).contains("D:\\SteamLibrary");
  }

  @Test
  void throwsOnUnterminatedQuote() {
    assertThatThrownBy(() -> parser.parse("\"unterminated"))
        .isInstanceOf(VdfParser.VdfParseException.class)
        .hasMessageContaining("Unterminated");
  }

  @Test
  void throwsOnUnclosedSection() {
    assertThatThrownBy(() -> parser.parse("\"k\" {"))
        .isInstanceOf(VdfParser.VdfParseException.class);
  }

  @Test
  void throwsOnStrayCloseBrace() {
    assertThatThrownBy(() -> parser.parse("} ")).isInstanceOf(VdfParser.VdfParseException.class);
  }

  @Test
  void parsesFromReader() throws Exception {
    try (var reader = new StringReader("\"k\" \"v\"")) {
      VdfNode.Section root = parser.parse(reader);

      assertThat(root.string("k")).contains("v");
    }
  }

  @Test
  void rejectsNullArgs() {
    assertThatNullPointerException().isThrownBy(() -> parser.parse((String) null));
    assertThatNullPointerException().isThrownBy(() -> parser.parse((java.io.Reader) null));
  }
}
