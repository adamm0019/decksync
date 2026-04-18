package dev.decksync.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One save-path rule from a Ludusavi {@link ManifestEntry}: a path template containing placeholders
 * plus the filters that decide when the rule applies.
 *
 * <p>Each element of {@link #when} is an alternative: the rule applies if the current host matches
 * any entry. An empty {@code when} list means the rule is unconditional.
 *
 * @param template raw path template from the manifest, placeholders not yet expanded
 * @param tags categorisation tags from the manifest (e.g. {@code save}, {@code config})
 * @param when alternatives that gate when this rule applies
 */
public record SavePathRule(String template, Set<String> tags, List<WhenCondition> when) {

  public SavePathRule {
    Objects.requireNonNull(template, "template");
    Objects.requireNonNull(tags, "tags");
    Objects.requireNonNull(when, "when");
    if (template.isBlank()) {
      throw new IllegalArgumentException("template must not be blank");
    }
    tags = Set.copyOf(tags);
    when = List.copyOf(when);
  }

  /**
   * One alternative within a rule's {@code when} clause. Both fields are optional — a condition
   * with neither is a wildcard; a condition with only {@code os} matches any store on that OS, and
   * vice-versa.
   *
   * @param os manifest OS tag (e.g. {@code windows}, {@code linux}, {@code mac}) or empty
   * @param store manifest store tag (e.g. {@code steam}, {@code gog}) or empty
   */
  public record WhenCondition(Optional<String> os, Optional<String> store) {
    public WhenCondition {
      Objects.requireNonNull(os, "os");
      Objects.requireNonNull(store, "store");
    }
  }
}
