package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManifestIndexTest {

  @Test
  void indexesEntriesWithSteamAppId() {
    ManifestEntry elden = entry("Elden Ring", Optional.of(1245620L));
    ManifestEntry drg = entry("Deep Rock Galactic", Optional.of(548430L));
    ManifestEntry noSteam = entry("Native Game", Optional.empty());

    ManifestIndex index = ManifestIndex.from(List.of(elden, drg, noSteam));

    assertThat(index.findBySteamAppId(1245620L)).contains(elden);
    assertThat(index.findBySteamAppId(548430L)).contains(drg);
    assertThat(index.findBySteamAppId(99999L)).isEmpty();
  }

  @Test
  void entriesPreservesDocumentOrder() {
    ManifestEntry a = entry("A", Optional.of(1L));
    ManifestEntry b = entry("B", Optional.empty());
    ManifestEntry c = entry("C", Optional.of(3L));

    ManifestIndex index = ManifestIndex.from(List.of(a, b, c));

    assertThat(index.entries()).containsExactly(a, b, c);
  }

  @Test
  void lastEntryWinsOnDuplicateAppId() {
    ManifestEntry first = entry("First Name", Optional.of(123L));
    ManifestEntry second = entry("Second Name", Optional.of(123L));

    ManifestIndex index = ManifestIndex.from(List.of(first, second));

    assertThat(index.findBySteamAppId(123L)).contains(second);
  }

  @Test
  void rejectsNullArgs() {
    assertThatNullPointerException().isThrownBy(() -> ManifestIndex.from(null));
    assertThatNullPointerException().isThrownBy(() -> new ManifestIndex(null, java.util.Map.of()));
    assertThatNullPointerException().isThrownBy(() -> new ManifestIndex(List.of(), null));
  }

  private static ManifestEntry entry(String name, Optional<Long> appId) {
    return new ManifestEntry(name, appId, Set.of(), List.of());
  }
}
