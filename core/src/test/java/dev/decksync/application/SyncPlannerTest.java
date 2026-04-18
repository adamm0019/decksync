package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.domain.FileEntry;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import dev.decksync.domain.Sha256;
import dev.decksync.domain.SyncAction;
import dev.decksync.domain.SyncPlan;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncPlannerTest {

  private static final GameId GAME = new GameId.SteamAppId(1245620L);
  private static final Instant T0 = Instant.parse("2026-04-17T12:00:00Z");
  private static final Instant T1 = T0.plusSeconds(60);
  private static final Sha256 HASH_A = Sha256.ofHex("aa".repeat(32));
  private static final Sha256 HASH_B = Sha256.ofHex("bb".repeat(32));

  private final SyncPlanner planner = new SyncPlanner();

  @Test
  void sameHash_skipsWithAlreadyInSync() {
    FileEntry local = new FileEntry(new LogicalPath("save.sav"), 3L, T0, HASH_A);
    FileEntry remote = new FileEntry(new LogicalPath("save.sav"), 3L, T1, HASH_A);

    SyncPlan plan = planner.plan(manifest(List.of(local)), manifest(List.of(remote)));

    assertThat(plan.actions())
        .singleElement()
        .isInstanceOfSatisfying(
            SyncAction.Skip.class,
            skip -> assertThat(skip.reason()).isEqualTo(SyncAction.Skip.Reason.ALREADY_IN_SYNC));
  }

  @Test
  void remoteNewerHashDiffers_pulls() {
    FileEntry local = new FileEntry(new LogicalPath("save.sav"), 3L, T0, HASH_A);
    FileEntry remote = new FileEntry(new LogicalPath("save.sav"), 4L, T1, HASH_B);

    SyncPlan plan = planner.plan(manifest(List.of(local)), manifest(List.of(remote)));

    assertThat(plan.actions())
        .singleElement()
        .isInstanceOfSatisfying(
            SyncAction.Pull.class, pull -> assertThat(pull.remote()).isEqualTo(remote));
  }

  @Test
  void localNewerHashDiffers_skipsWithLocalNewer() {
    FileEntry local = new FileEntry(new LogicalPath("save.sav"), 3L, T1, HASH_A);
    FileEntry remote = new FileEntry(new LogicalPath("save.sav"), 4L, T0, HASH_B);

    SyncPlan plan = planner.plan(manifest(List.of(local)), manifest(List.of(remote)));

    assertThat(plan.actions())
        .singleElement()
        .isInstanceOfSatisfying(
            SyncAction.Skip.class,
            skip -> assertThat(skip.reason()).isEqualTo(SyncAction.Skip.Reason.LOCAL_NEWER));
  }

  @Test
  void missingLocally_pulls() {
    FileEntry remote = new FileEntry(new LogicalPath("new.sav"), 3L, T1, HASH_B);

    SyncPlan plan = planner.plan(manifest(List.of()), manifest(List.of(remote)));

    assertThat(plan.actions())
        .singleElement()
        .isInstanceOfSatisfying(
            SyncAction.Pull.class, pull -> assertThat(pull.remote()).isEqualTo(remote));
  }

  @Test
  void missingRemotely_skipsWithLocalOnly() {
    FileEntry local = new FileEntry(new LogicalPath("save.sav"), 3L, T0, HASH_A);

    SyncPlan plan = planner.plan(manifest(List.of(local)), manifest(List.of()));

    assertThat(plan.actions())
        .singleElement()
        .isInstanceOfSatisfying(
            SyncAction.Skip.class,
            skip -> assertThat(skip.reason()).isEqualTo(SyncAction.Skip.Reason.LOCAL_ONLY));
  }

  @Test
  void equalMtimeDifferentHash_isConflict() {
    FileEntry local = new FileEntry(new LogicalPath("save.sav"), 3L, T0, HASH_A);
    FileEntry remote = new FileEntry(new LogicalPath("save.sav"), 4L, T0, HASH_B);

    SyncPlan plan = planner.plan(manifest(List.of(local)), manifest(List.of(remote)));

    assertThat(plan.actions())
        .singleElement()
        .isInstanceOfSatisfying(
            SyncAction.Conflict.class,
            conflict -> {
              assertThat(conflict.local()).isEqualTo(local);
              assertThat(conflict.remote()).isEqualTo(remote);
            });
  }

  @Test
  void mixedPlan_sortsActionsByLogicalPath() {
    FileEntry localZ = new FileEntry(new LogicalPath("z.sav"), 3L, T0, HASH_A);
    FileEntry remoteA = new FileEntry(new LogicalPath("a.sav"), 3L, T1, HASH_B);
    FileEntry remoteM = new FileEntry(new LogicalPath("m.sav"), 3L, T1, HASH_A);
    FileEntry localM = new FileEntry(new LogicalPath("m.sav"), 3L, T0, HASH_A);

    SyncPlan plan =
        planner.plan(manifest(List.of(localM, localZ)), manifest(List.of(remoteA, remoteM)));

    assertThat(plan.actions())
        .extracting(a -> a.path().path())
        .containsExactly("a.sav", "m.sav", "z.sav");
  }

  @Test
  void planCarriesGameIdFromLocalManifest() {
    SyncPlan plan = planner.plan(manifest(List.of()), manifest(List.of()));
    assertThat(plan.game()).isEqualTo(GAME);
  }

  private static Manifest manifest(List<FileEntry> files) {
    return new Manifest(GAME, files, T0);
  }
}
