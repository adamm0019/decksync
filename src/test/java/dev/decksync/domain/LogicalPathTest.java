package dev.decksync.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class LogicalPathTest {

  @Test
  void acceptsForwardSlashRelativePath() {
    LogicalPath p = new LogicalPath("saves/slot_0.sav");

    assertThat(p.path()).isEqualTo("saves/slot_0.sav");
  }

  @Test
  void acceptsTopLevelFileName() {
    assertThat(new LogicalPath("profile.xml").path()).isEqualTo("profile.xml");
  }

  @Test
  void preservesExactCase() {
    LogicalPath p = new LogicalPath("Saves/Slot_0.Sav");

    assertThat(p.path()).isEqualTo("Saves/Slot_0.Sav");
    assertThat(p).isNotEqualTo(new LogicalPath("saves/slot_0.sav"));
  }

  @Test
  void rejectsNull() {
    assertThatIllegalArgumentException().isThrownBy(() -> new LogicalPath(null));
  }

  @Test
  void rejectsEmpty() {
    assertThatIllegalArgumentException().isThrownBy(() -> new LogicalPath(""));
  }

  @Test
  void rejectsBackslashes() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LogicalPath("saves\\slot_0.sav"))
        .withMessageContaining("forward slashes");
  }

  @Test
  void rejectsAbsolutePaths() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LogicalPath("/saves/slot_0.sav"))
        .withMessageContaining("relative");
  }

  @Test
  void rejectsTrailingSlash() {
    assertThatIllegalArgumentException().isThrownBy(() -> new LogicalPath("saves/"));
  }

  @Test
  void rejectsDoubleSlash() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LogicalPath("saves//slot_0.sav"))
        .withMessageContaining("empty segments");
  }

  @Test
  void rejectsParentSegment() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LogicalPath("saves/../etc/passwd"))
        .withMessageContaining("'.' or '..'");
  }

  @Test
  void rejectsDotSegment() {
    assertThatIllegalArgumentException().isThrownBy(() -> new LogicalPath("saves/./slot_0.sav"));
  }
}
