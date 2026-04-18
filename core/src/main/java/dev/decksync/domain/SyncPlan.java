package dev.decksync.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The ordered set of {@link SyncAction}s the planner produced for one game, ready for the executor
 * to walk top-to-bottom. Actions are sorted by logical path so two independent runs against
 * identical inputs produce byte-identical plans — useful for {@code --dry-run} diffs and
 * deterministic logging.
 */
public record SyncPlan(GameId game, List<SyncAction> actions) {

  public SyncPlan {
    if (game == null) {
      throw new IllegalArgumentException("SyncPlan game must not be null");
    }
    if (actions == null) {
      throw new IllegalArgumentException("SyncPlan actions must not be null");
    }
    for (SyncAction action : actions) {
      if (action == null) {
        throw new IllegalArgumentException("SyncPlan actions must not contain null");
      }
    }
    List<SyncAction> sorted = new ArrayList<>(actions);
    sorted.sort(Comparator.comparing(a -> a.path().path()));
    actions = List.copyOf(sorted);
  }
}
