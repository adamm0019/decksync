package dev.decksync.application;

import dev.decksync.domain.SyncPlan;
import java.util.List;

/**
 * What a single {@link SyncService#syncGame} invocation produced: the plan the engine computed, and
 * the subset of pull actions it actually executed. On {@code --dry-run} the plan is non-empty but
 * {@code appliedPaths} is always empty — the CLI prints both so an operator can see the diff
 * without any filesystem changes having occurred.
 */
public record SyncOutcome(SyncPlan plan, List<String> appliedPaths, boolean dryRun) {

  public SyncOutcome {
    if (plan == null) {
      throw new IllegalArgumentException("SyncOutcome plan must not be null");
    }
    if (appliedPaths == null) {
      throw new IllegalArgumentException("SyncOutcome appliedPaths must not be null");
    }
    appliedPaths = List.copyOf(appliedPaths);
  }
}
