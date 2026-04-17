package dev.decksync.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A complete listing of a single game's save files at a point in time. Always sorted by {@link
 * LogicalPath#path()} so equality is deterministic regardless of filesystem walk order — two peers
 * scanning the same folder must produce byte-identical protocol payloads.
 */
public record Manifest(GameId game, List<FileEntry> files, Instant generatedAt) {

  public Manifest {
    if (game == null) {
      throw new IllegalArgumentException("Manifest game must not be null");
    }
    if (files == null) {
      throw new IllegalArgumentException("Manifest files must not be null");
    }
    if (generatedAt == null) {
      throw new IllegalArgumentException("Manifest generatedAt must not be null");
    }
    Set<LogicalPath> seen = new HashSet<>();
    for (FileEntry file : files) {
      if (file == null) {
        throw new IllegalArgumentException("Manifest files must not contain null");
      }
      if (!seen.add(file.path())) {
        throw new IllegalArgumentException(
            "Manifest must not contain duplicate paths — got: " + file.path());
      }
    }
    List<FileEntry> sorted = new ArrayList<>(files);
    sorted.sort(Comparator.comparing(f -> f.path().path()));
    files = List.copyOf(sorted);
  }
}
