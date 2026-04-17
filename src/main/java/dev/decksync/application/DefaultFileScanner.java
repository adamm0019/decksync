package dev.decksync.application;

import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.FileEntry;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import dev.decksync.domain.Sha256;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks every regular file under the game's resolved save directory, skipping any file whose
 * modification time is younger than the stability window (default 3 seconds) or that cannot be
 * briefly exclusive-locked — games routinely hold save files open mid-write, and hashing a partial
 * flush would poison the sync diff.
 *
 * <p>{@code tryLock} is mandatory on Windows (the OS enforces a hard exclusive lock while a process
 * has the handle open); on Linux advisory locks are rarely set by games, so the mtime-stability
 * check is the primary signal on that side. Files we skip are logged at DEBUG and omitted from the
 * manifest — the next scan will pick them up.
 *
 * <p>Case collisions (two sibling files differing only in case, impossible on Windows but legal on
 * Linux) are logged at WARN because they sync-fragilely: one side's filesystem coalesces them and
 * the other side doesn't.
 */
public final class DefaultFileScanner implements FileScanner {

  private static final Logger log = LoggerFactory.getLogger(DefaultFileScanner.class);

  private static final Duration DEFAULT_STABILITY_WINDOW = Duration.ofSeconds(3);
  private static final int BUFFER_SIZE = 64 * 1024;

  private final Clock clock;
  private final Duration stabilityWindow;

  public DefaultFileScanner() {
    this(Clock.systemUTC(), DEFAULT_STABILITY_WINDOW);
  }

  public DefaultFileScanner(Clock clock, Duration stabilityWindow) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.stabilityWindow = Objects.requireNonNull(stabilityWindow, "stabilityWindow");
  }

  @Override
  public Manifest scan(GameId game, AbsolutePath root) {
    Objects.requireNonNull(game, "game");
    Objects.requireNonNull(root, "root");
    Path rootPath = root.path();
    if (!Files.isDirectory(rootPath)) {
      return new Manifest(game, List.of(), clock.instant());
    }
    Instant cutoff = clock.instant().minus(stabilityWindow);
    List<FileEntry> entries = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(rootPath)) {
      walk.filter(Files::isRegularFile)
          .sorted(Comparator.naturalOrder())
          .forEach(file -> scanFile(rootPath, file, cutoff).ifPresent(entries::add));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk " + rootPath, e);
    }
    warnOnCaseCollisions(entries);
    return new Manifest(game, entries, clock.instant());
  }

  private java.util.Optional<FileEntry> scanFile(Path root, Path file, Instant cutoff) {
    try {
      Instant mtime = Files.getLastModifiedTime(file).toInstant();
      if (mtime.isAfter(cutoff)) {
        log.debug("Skipping {} — modified within stability window", file);
        return java.util.Optional.empty();
      }
      long size = Files.size(file);
      LogicalPath logicalPath = toLogical(root, file);
      Sha256 hash = hashWithProbeLock(file);
      return java.util.Optional.of(new FileEntry(logicalPath, size, mtime, hash));
    } catch (FileBusyException e) {
      log.debug("Skipping {} — held open by another process", file);
      return java.util.Optional.empty();
    } catch (IOException e) {
      log.debug("Skipping {} — IO error: {}", file, e.toString());
      return java.util.Optional.empty();
    }
  }

  private static LogicalPath toLogical(Path root, Path file) {
    String relative = root.relativize(file).toString().replace('\\', '/');
    return new LogicalPath(relative);
  }

  private Sha256 hashWithProbeLock(Path file) throws IOException {
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      try (var lock = channel.tryLock(0L, Long.MAX_VALUE, true)) {
        if (lock == null) {
          throw new FileBusyException();
        }
        return hash(channel);
      } catch (OverlappingFileLockException e) {
        throw new FileBusyException();
      }
    }
  }

  private static Sha256 hash(FileChannel channel) throws IOException {
    MessageDigest digest = newSha256();
    ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    channel.position(0);
    while (channel.read(buffer) != -1) {
      buffer.flip();
      digest.update(buffer);
      buffer.clear();
    }
    return new Sha256(digest.digest());
  }

  private static MessageDigest newSha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private static void warnOnCaseCollisions(List<FileEntry> entries) {
    Map<String, LogicalPath> seen = new HashMap<>();
    for (FileEntry entry : entries) {
      String key = entry.path().path().toLowerCase(Locale.ROOT);
      LogicalPath prior = seen.put(key, entry.path());
      if (prior != null && !prior.equals(entry.path())) {
        log.warn(
            "Case-only filename collision: {} and {} — saves may not round-trip to a"
                + " case-insensitive filesystem (e.g. Windows).",
            prior,
            entry.path());
      }
    }
  }

  private static final class FileBusyException extends IOException {
    private static final long serialVersionUID = 1L;
  }
}
