package dev.decksync.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The parsed Ludusavi manifest plus a lookup index. The raw list is kept alongside the index so
 * callers that need to enumerate all entries (e.g. to scan for slug-keyed games) don't have to
 * reload the YAML.
 *
 * <p>When multiple entries share a Steam AppID — rare but possible when the same title ships under
 * several Ludusavi names — the last one wins. Downstream resolution only needs one entry and
 * Ludusavi's own tooling treats these as equivalent.
 */
public record ManifestIndex(List<ManifestEntry> entries, Map<Long, ManifestEntry> bySteamAppId) {

  public ManifestIndex {
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(bySteamAppId, "bySteamAppId");
    entries = Collections.unmodifiableList(new ArrayList<>(entries));
    bySteamAppId = Collections.unmodifiableMap(new HashMap<>(bySteamAppId));
  }

  public static ManifestIndex from(List<ManifestEntry> entries) {
    Objects.requireNonNull(entries, "entries");
    Map<Long, ManifestEntry> index = new HashMap<>();
    for (ManifestEntry entry : entries) {
      entry.steamAppId().ifPresent(id -> index.put(id, entry));
    }
    return new ManifestIndex(entries, index);
  }

  public Optional<ManifestEntry> findBySteamAppId(long appId) {
    return Optional.ofNullable(bySteamAppId.get(appId));
  }
}
