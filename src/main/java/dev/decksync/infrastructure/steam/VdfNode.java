package dev.decksync.infrastructure.steam;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A node in a parsed Valve KeyValues (VDF) document. Sealed: every node is either a leaf string
 * {@link Value} or a nested {@link Section} whose insertion order is preserved for deterministic
 * iteration (Steam's {@code libraryfolders.vdf} cares about index-keyed sub-sections).
 */
public sealed interface VdfNode permits VdfNode.Value, VdfNode.Section {

  /** A leaf value. Empty strings are legal — Steam emits them for optional fields. */
  record Value(String value) implements VdfNode {
    public Value {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * A nested section. Entry order matches document order so callers can rely on the first entry
   * being the first-declared child.
   */
  record Section(Map<String, VdfNode> entries) implements VdfNode {
    public Section {
      Objects.requireNonNull(entries, "entries");
      entries = Map.copyOf(new LinkedHashMap<>(entries));
    }

    /** Convenience lookup for a direct child section. */
    public Optional<Section> section(String key) {
      VdfNode node = entries.get(key);
      return node instanceof Section s ? Optional.of(s) : Optional.empty();
    }

    /** Convenience lookup for a direct child string value. */
    public Optional<String> string(String key) {
      VdfNode node = entries.get(key);
      return node instanceof Value v ? Optional.of(v.value()) : Optional.empty();
    }
  }
}
