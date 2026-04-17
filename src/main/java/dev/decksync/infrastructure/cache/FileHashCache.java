package dev.decksync.infrastructure.cache;

import dev.decksync.application.HashCache;
import dev.decksync.domain.Sha256;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File-backed {@link HashCache}. The on-disk representation is a line-oriented TSV file with one
 * row per cached file: {@code <hex>\t<size>\t<mtime-epoch-millis>\t<absolute-path>}. Lines that
 * fail to parse are dropped at load with a DEBUG log — corruption shouldn't prevent the daemon from
 * starting, only cost re-hashes.
 *
 * <p>Entries are keyed by the string form of the absolute path, and a lookup is only a hit when the
 * cached {@code size} and {@code mtime} both still match the live file. Any change to either
 * invalidates the entry — even if the bytes are coincidentally the same, we re-hash rather than
 * trust the cache.
 *
 * <p>{@link #persist()} atomically rewrites the whole file; append-only would be fragile across the
 * sleep/wake cycle on a Steam Deck where crashes can interleave with writes. Persistence is skipped
 * entirely when no entries have changed since load.
 */
public final class FileHashCache implements HashCache {

  private static final Logger log = LoggerFactory.getLogger(FileHashCache.class);

  private final Path file;
  private final Map<String, Entry> entries = new HashMap<>();
  private boolean dirty;

  public FileHashCache(Path file) {
    this.file = Objects.requireNonNull(file, "file");
    load();
  }

  @Override
  public synchronized Optional<Sha256> lookup(Path path, long size, Instant mtime) {
    Entry entry = entries.get(path.toString());
    if (entry == null) {
      return Optional.empty();
    }
    if (entry.size != size || !entry.mtime.equals(mtime)) {
      return Optional.empty();
    }
    return Optional.of(entry.hash);
  }

  @Override
  public synchronized void store(Path path, long size, Instant mtime, Sha256 hash) {
    String key = path.toString();
    if (key.indexOf('\t') >= 0 || key.indexOf('\n') >= 0) {
      log.debug("Skipping cache store for unusable path (contains tab or newline): {}", key);
      return;
    }
    Entry prior = entries.put(key, new Entry(size, mtime, hash));
    if (prior == null
        || !prior.hash.equals(hash)
        || prior.size != size
        || !prior.mtime.equals(mtime)) {
      dirty = true;
    }
  }

  public synchronized void persist() {
    if (!dirty) {
      return;
    }
    try {
      if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
      }
      Path tmp = file.resolveSibling(file.getFileName() + ".decksync.tmp");
      List<String> lines = new ArrayList<>(entries.size());
      for (Map.Entry<String, Entry> row : entries.entrySet()) {
        Entry e = row.getValue();
        lines.add(
            e.hash.hex() + '\t' + e.size + '\t' + e.mtime.toEpochMilli() + '\t' + row.getKey());
      }
      Files.write(tmp, lines, StandardCharsets.UTF_8);
      Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      dirty = false;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to persist hash cache to " + file, e);
    }
  }

  synchronized int sizeForTest() {
    return entries.size();
  }

  private void load() {
    if (!Files.isRegularFile(file)) {
      return;
    }
    try {
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      int kept = 0;
      int dropped = 0;
      for (String line : lines) {
        if (line.isEmpty()) {
          continue;
        }
        Parsed parsed = parseLine(line);
        if (parsed == null) {
          dropped++;
          continue;
        }
        entries.put(parsed.path, new Entry(parsed.size, parsed.mtime, parsed.hash));
        kept++;
      }
      log.debug("Loaded hash cache from {} — {} entries, {} dropped", file, kept, dropped);
    } catch (IOException e) {
      log.debug("Failed to read hash cache at {}, starting empty: {}", file, e.toString());
    }
  }

  private static Parsed parseLine(String line) {
    String[] parts = line.split("\t", 4);
    if (parts.length != 4) {
      return null;
    }
    try {
      Sha256 hash = Sha256.ofHex(parts[0]);
      long size = Long.parseLong(parts[1]);
      if (size < 0) {
        return null;
      }
      long epochMillis = Long.parseLong(parts[2]);
      Instant mtime = Instant.ofEpochMilli(epochMillis);
      String path = parts[3];
      if (path.isEmpty()) {
        return null;
      }
      return new Parsed(hash, size, mtime, path);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private record Entry(long size, Instant mtime, Sha256 hash) {}

  private record Parsed(Sha256 hash, long size, Instant mtime, String path) {}
}
