package dev.decksync.infrastructure.steam;

import dev.decksync.application.SteamLibrary;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Extracts {@link SteamLibrary} entries from a parsed {@code libraryfolders.vdf} document. Steam
 * writes this file at {@code <steamRoot>/steamapps/libraryfolders.vdf}; each numerically-keyed
 * section under the top-level {@code libraryfolders} map is one library.
 *
 * <p>Entries whose {@code path} is missing, not absolute on the current OS, or syntactically bad
 * are skipped rather than failing the read — the upstream file occasionally contains stale sections
 * after a user removes a library from Steam but before restarting it.
 */
public final class LibraryFoldersVdfReader {

  private final VdfParser parser;

  public LibraryFoldersVdfReader() {
    this(new VdfParser());
  }

  public LibraryFoldersVdfReader(VdfParser parser) {
    this.parser = Objects.requireNonNull(parser, "parser");
  }

  public List<SteamLibrary> read(Path vdfFile) throws IOException {
    Objects.requireNonNull(vdfFile, "vdfFile");
    String text = Files.readString(vdfFile, StandardCharsets.UTF_8);
    return extract(parser.parse(text));
  }

  public List<SteamLibrary> read(Reader reader) throws IOException {
    Objects.requireNonNull(reader, "reader");
    return extract(parser.parse(reader));
  }

  public List<SteamLibrary> read(String text) {
    Objects.requireNonNull(text, "text");
    return extract(parser.parse(text));
  }

  private static List<SteamLibrary> extract(VdfNode.Section root) {
    VdfNode.Section libraryFolders = root.section("libraryfolders").orElse(null);
    if (libraryFolders == null) {
      return List.of();
    }
    List<SteamLibrary> libraries = new ArrayList<>(libraryFolders.entries().size());
    for (Map.Entry<String, VdfNode> entry : libraryFolders.entries().entrySet()) {
      if (entry.getValue() instanceof VdfNode.Section section) {
        toLibrary(section).ifPresent(libraries::add);
      }
    }
    return List.copyOf(libraries);
  }

  private static java.util.Optional<SteamLibrary> toLibrary(VdfNode.Section section) {
    String raw = section.string("path").orElse(null);
    if (raw == null || raw.isBlank()) {
      return java.util.Optional.empty();
    }
    Path path;
    try {
      path = Path.of(raw);
    } catch (java.nio.file.InvalidPathException e) {
      return java.util.Optional.empty();
    }
    if (!path.isAbsolute()) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(new SteamLibrary(path, extractAppIds(section)));
  }

  private static Set<Long> extractAppIds(VdfNode.Section librarySection) {
    VdfNode.Section apps = librarySection.section("apps").orElse(null);
    if (apps == null) {
      return Set.of();
    }
    Set<Long> ids = new LinkedHashSet<>(apps.entries().size());
    for (String key : apps.entries().keySet()) {
      try {
        ids.add(Long.parseLong(key));
      } catch (NumberFormatException ignored) {
        // Steam only writes numeric app IDs here; skip anything else quietly.
      }
    }
    return ids;
  }
}
