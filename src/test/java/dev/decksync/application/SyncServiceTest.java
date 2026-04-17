package dev.decksync.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.decksync.domain.AbsolutePath;
import dev.decksync.domain.FileEntry;
import dev.decksync.domain.GameId;
import dev.decksync.domain.LogicalPath;
import dev.decksync.domain.Manifest;
import dev.decksync.domain.Sha256;
import dev.decksync.domain.SyncAction;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncServiceTest {

  private static final GameId GAME = new GameId.SteamAppId(1245620L);
  private static final Instant NOW = Instant.parse("2026-04-17T20:00:00Z");
  private static final Instant EARLIER = Instant.parse("2026-04-17T19:00:00Z");

  @Test
  void pullsNewerRemoteFilesAndBacksUpExistingLocal(@TempDir Path tmp) throws Exception {
    Path saveRoot = tmp.resolve("game");
    Files.createDirectories(saveRoot);
    Path target = saveRoot.resolve("saves/slot_0.sav");
    Files.createDirectories(target.getParent());
    byte[] oldBytes = "OLD".getBytes(StandardCharsets.UTF_8);
    Files.write(target, oldBytes);
    Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.from(EARLIER));

    byte[] newBytes = "NEW-BYTES".getBytes(StandardCharsets.UTF_8);
    LogicalPath logical = new LogicalPath("saves/slot_0.sav");
    FileEntry remoteEntry = new FileEntry(logical, newBytes.length, NOW, sha256(newBytes));
    Manifest remote = new Manifest(GAME, List.of(remoteEntry), NOW);

    RecordingPeerClient peer = new RecordingPeerClient();
    peer.manifests.put(GAME, remote);
    peer.files.put(logical, newBytes);

    Path historyRoot = tmp.resolve("history");
    SyncService service =
        new SyncService(
            peer,
            stubScanner(GAME, saveRoot, List.of(fileEntry(target, logical, EARLIER))),
            new SyncPlanner(),
            new BackupService(historyRoot, Clock.fixed(NOW, ZoneOffset.UTC)),
            new FileApplier(),
            singletonCatalog(GAME, saveRoot),
            20);

    SyncOutcome outcome = service.syncGame(GAME, false);

    assertThat(outcome.appliedPaths()).containsExactly("saves/slot_0.sav");
    assertThat(Files.readAllBytes(target)).isEqualTo(newBytes);
    assertThat(historyRoot.resolve("steam-1245620/2026-04-17T20-00-00Z/saves/slot_0.sav"))
        .exists()
        .hasBinaryContent(oldBytes);
  }

  @Test
  void dryRunComputesPlanButMakesNoFilesystemChanges(@TempDir Path tmp) throws Exception {
    Path saveRoot = tmp.resolve("game");
    Files.createDirectories(saveRoot);
    Path target = saveRoot.resolve("save.sav");
    byte[] oldBytes = "OLD".getBytes(StandardCharsets.UTF_8);
    Files.write(target, oldBytes);
    Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.from(EARLIER));

    byte[] newBytes = "NEW".getBytes(StandardCharsets.UTF_8);
    LogicalPath logical = new LogicalPath("save.sav");
    Manifest remote =
        new Manifest(
            GAME, List.of(new FileEntry(logical, newBytes.length, NOW, sha256(newBytes))), NOW);

    RecordingPeerClient peer = new RecordingPeerClient();
    peer.manifests.put(GAME, remote);
    peer.files.put(logical, newBytes);

    Path historyRoot = tmp.resolve("history");
    SyncService service =
        new SyncService(
            peer,
            stubScanner(GAME, saveRoot, List.of(fileEntry(target, logical, EARLIER))),
            new SyncPlanner(),
            new BackupService(historyRoot, Clock.fixed(NOW, ZoneOffset.UTC)),
            new FileApplier(),
            singletonCatalog(GAME, saveRoot),
            20);

    SyncOutcome outcome = service.syncGame(GAME, true);

    assertThat(outcome.dryRun()).isTrue();
    assertThat(outcome.appliedPaths()).isEmpty();
    assertThat(outcome.plan().actions()).hasSize(1);
    assertThat(outcome.plan().actions().get(0)).isInstanceOf(SyncAction.Pull.class);
    assertThat(Files.readAllBytes(target)).isEqualTo(oldBytes);
    assertThat(historyRoot).doesNotExist();
    assertThat(peer.downloadedPaths).isEmpty();
  }

  @Test
  void skipsWhenHashesMatchAndDoesNotCallDownload(@TempDir Path tmp) throws Exception {
    Path saveRoot = tmp.resolve("game");
    Files.createDirectories(saveRoot);
    Path target = saveRoot.resolve("save.sav");
    byte[] bytes = "SAME".getBytes(StandardCharsets.UTF_8);
    Files.write(target, bytes);
    Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.from(NOW));

    LogicalPath logical = new LogicalPath("save.sav");
    FileEntry entry = new FileEntry(logical, bytes.length, NOW, sha256(bytes));
    Manifest remote = new Manifest(GAME, List.of(entry), NOW);

    RecordingPeerClient peer = new RecordingPeerClient();
    peer.manifests.put(GAME, remote);

    SyncService service =
        new SyncService(
            peer,
            stubScanner(GAME, saveRoot, List.of(entry)),
            new SyncPlanner(),
            new BackupService(tmp.resolve("history"), Clock.fixed(NOW, ZoneOffset.UTC)),
            new FileApplier(),
            singletonCatalog(GAME, saveRoot),
            20);

    SyncOutcome outcome = service.syncGame(GAME, false);

    assertThat(outcome.appliedPaths()).isEmpty();
    assertThat(peer.downloadedPaths).isEmpty();
  }

  private static FileEntry fileEntry(Path file, LogicalPath logical, Instant mtime)
      throws IOException {
    return new FileEntry(logical, Files.size(file), mtime, sha256(Files.readAllBytes(file)));
  }

  private static Sha256 sha256(byte[] bytes) {
    try {
      return new Sha256(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static GameCatalog singletonCatalog(GameId game, Path root) {
    Map<GameId, AbsolutePath> map = Map.of(game, new AbsolutePath(root.toAbsolutePath()));
    return () -> map;
  }

  private static FileScanner stubScanner(GameId game, Path root, List<FileEntry> entries) {
    AbsolutePath expectedRoot = new AbsolutePath(root.toAbsolutePath());
    return (g, r) -> {
      if (!g.equals(game)) {
        throw new IllegalStateException("Unexpected game: " + g);
      }
      if (!r.equals(expectedRoot)) {
        throw new IllegalStateException("Unexpected root: " + r);
      }
      return new Manifest(g, entries, Instant.parse("2026-04-17T19:59:59Z"));
    };
  }

  private static final class RecordingPeerClient implements PeerClient {
    final Map<GameId, Manifest> manifests = new HashMap<>();
    final Map<LogicalPath, byte[]> files = new HashMap<>();
    final List<LogicalPath> downloadedPaths = new java.util.ArrayList<>();

    @Override
    public List<GameId> listGames() {
      return List.copyOf(manifests.keySet());
    }

    @Override
    public Manifest fetchManifest(GameId game) {
      Manifest m = manifests.get(game);
      if (m == null) {
        throw new PeerFileNotFoundException("no manifest for " + game);
      }
      return m;
    }

    @Override
    public byte[] downloadFile(GameId game, LogicalPath path) {
      downloadedPaths.add(path);
      byte[] body = files.get(path);
      if (body == null) {
        throw new PeerFileNotFoundException("no file for " + path);
      }
      return body;
    }
  }
}
