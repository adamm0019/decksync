package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManifestEntryTest {

  @Test
  void acceptsFullyPopulatedEntry() {
    ManifestEntry e =
        new ManifestEntry(
            "Elden Ring",
            Optional.of(1245620L),
            Set.of("Elden Ring", "ELDEN RING"),
            List.of(
                new SavePathRule(
                    "<base>/save",
                    Set.of("save"),
                    List.of(
                        new SavePathRule.WhenCondition(
                            Optional.of("windows"), Optional.of("steam"))))));

    assertThat(e.name()).isEqualTo("Elden Ring");
    assertThat(e.steamAppId()).contains(1245620L);
    assertThat(e.installDirs()).containsExactlyInAnyOrder("Elden Ring", "ELDEN RING");
    assertThat(e.savePaths()).hasSize(1);
  }

  @Test
  void acceptsMinimalEntryWithoutSteamOrSaves() {
    ManifestEntry e = new ManifestEntry("Free Game", Optional.empty(), Set.of(), List.of());

    assertThat(e.steamAppId()).isEmpty();
    assertThat(e.installDirs()).isEmpty();
    assertThat(e.savePaths()).isEmpty();
  }

  @Test
  void installDirsAndSavePathsAreDefensivelyCopied() {
    var mutableDirs = new java.util.HashSet<>(Set.of("A"));
    var mutablePaths = new java.util.ArrayList<SavePathRule>();

    ManifestEntry e = new ManifestEntry("Game", Optional.empty(), mutableDirs, mutablePaths);
    mutableDirs.add("B");
    mutablePaths.add(new SavePathRule("<base>", Set.of(), List.of()));

    assertThat(e.installDirs()).containsExactly("A");
    assertThat(e.savePaths()).isEmpty();
  }

  @Test
  void rejectsBlankName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ManifestEntry("   ", Optional.empty(), Set.of(), List.of()))
        .withMessageContaining("name");
  }

  @Test
  void rejectsNullFields() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ManifestEntry(null, Optional.empty(), Set.of(), List.of()));
    assertThatNullPointerException()
        .isThrownBy(() -> new ManifestEntry("Game", null, Set.of(), List.of()));
    assertThatNullPointerException()
        .isThrownBy(() -> new ManifestEntry("Game", Optional.empty(), null, List.of()));
    assertThatNullPointerException()
        .isThrownBy(() -> new ManifestEntry("Game", Optional.empty(), Set.of(), null));
  }
}
