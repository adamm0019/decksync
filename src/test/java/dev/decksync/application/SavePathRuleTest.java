package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SavePathRuleTest {

  @Test
  void acceptsFullyPopulatedRule() {
    var rule =
        new SavePathRule(
            "<winAppData>/Game",
            Set.of("save", "config"),
            List.of(
                new SavePathRule.WhenCondition(Optional.of("windows"), Optional.empty()),
                new SavePathRule.WhenCondition(Optional.of("linux"), Optional.of("steam"))));

    assertThat(rule.template()).isEqualTo("<winAppData>/Game");
    assertThat(rule.tags()).containsExactlyInAnyOrder("save", "config");
    assertThat(rule.when()).hasSize(2);
  }

  @Test
  void acceptsUnconditionalRule() {
    var rule = new SavePathRule("<base>/save", Set.of(), List.of());

    assertThat(rule.when()).isEmpty();
    assertThat(rule.tags()).isEmpty();
  }

  @Test
  void rejectsBlankTemplate() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new SavePathRule("  ", Set.of(), List.of()))
        .withMessageContaining("template");
  }

  @Test
  void rejectsNullFields() {
    assertThatNullPointerException().isThrownBy(() -> new SavePathRule(null, Set.of(), List.of()));
    assertThatNullPointerException().isThrownBy(() -> new SavePathRule("<base>", null, List.of()));
    assertThatNullPointerException().isThrownBy(() -> new SavePathRule("<base>", Set.of(), null));
  }

  @Test
  void whenConditionRejectsNullFields() {
    assertThatNullPointerException()
        .isThrownBy(() -> new SavePathRule.WhenCondition(null, Optional.empty()));
    assertThatNullPointerException()
        .isThrownBy(() -> new SavePathRule.WhenCondition(Optional.empty(), null));
  }

  @Test
  void tagsAndWhenAreDefensivelyCopied() {
    var mutableTags = new java.util.HashSet<>(Set.of("save"));
    var mutableWhen = new java.util.ArrayList<SavePathRule.WhenCondition>();

    var rule = new SavePathRule("<base>", mutableTags, mutableWhen);
    mutableTags.add("extra");
    mutableWhen.add(new SavePathRule.WhenCondition(Optional.empty(), Optional.empty()));

    assertThat(rule.tags()).containsExactly("save");
    assertThat(rule.when()).isEmpty();
  }
}
