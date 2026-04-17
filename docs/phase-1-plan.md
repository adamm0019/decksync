# DeckSync — Phase 1 Plan

## Goal

A working save-sync MVP usable from the command line, running on **both Windows 11 and SteamOS / Linux**. The daemon runs on both machines, they find each other via a hardcoded peer address in config, and triggering a sync reliably moves game saves across — including the cross-platform case where the same Windows game runs natively on a PC and under Proton on a SteamOS Deck.

No GUI yet. No discovery yet. No encryption yet. Prove the core sync loop first, then layer on.

## Success criteria

Phase 1 is done when Adam can:

1. Run `decksync sync` on a Windows PC and have it pull newer saves from a SteamOS Deck.
2. Play Elden Ring on the Deck under Proton, quit, sync on the PC, and find the save where he expects it — with the PC's previous save preserved as a timestamped backup in `~/.decksync/history/`.
3. Run `decksync list-games` on both machines and see correct absolute paths resolved per platform — Windows paths on the PC, Proton prefix paths on the Deck for the same game.
4. Run `decksync sync --dry-run` and see exactly what would happen before it happens.
5. Run the two-node integration test locally on each platform and have it pass reliably.

## Out of scope

Deferred so Phase 1 stays shippable:

- JavaFX GUI and Scene Builder layouts (Phase 2)
- mDNS / zeroconf discovery (Phase 2)
- TLS, mutual auth, pairing flow (Phase 2 — **required before any real non-dev use**)
- Filesystem watcher for real-time sync (Phase 2)
- Delete propagation and tombstones (Phase 2)
- macOS support (`PlaceholderResolver` is sealed but `MacPlaceholderResolver` is unimplemented)
- Native-Linux-build ↔ native-Windows-build save format translation (rare; documented as a limitation)
- Timeline scrubber UI (Phase 3)
- Play-state lock via Steam process monitoring (Phase 3)
- QR pairing, screenshot attachments, save introspection (Phase 3)

## Architecture recap

Hexagonal. Pull-based sync: the initiating peer asks the other for its manifest, diffs against local, fetches what's newer. Last-writer-wins by mtime with hash check to avoid redundant transfers. Versioned backups protect against surprise divergence. **Platform-aware resolvers chosen at startup mean the protocol and domain stay fully OS-agnostic** — only the `infrastructure` layer knows which OS it's running on.

## Milestones

Each milestone is a shippable increment. Don't move to the next until the previous one's success criterion holds.

### M1 — Skeleton

- Gradle Kotlin-DSL build, Java 21 toolchain, Spring Boot 3.x
- Spotless (Google Java Format) + ErrorProne wired into `check`
- Package structure matching CLAUDE.md
- **GitHub Actions matrix: build + test on `windows-latest` AND `ubuntu-latest`**
- `CLAUDE.md`, `README.md`, `LICENSE` (MIT)
- Picocli entry point, Spring Boot entry point, both run and do nothing

**Done when:** `./gradlew build` passes on a clean clone on both Windows and Linux, CI is green for both runners.

### M2 — Cross-platform game catalog

This milestone grows most under the cross-platform requirement. Treat it as the project's hardest infrastructure work.

- Bundle Ludusavi manifest as classpath resource
- `LudusaviManifestLoader` parses YAML into domain types preserving `os` and `store` tags; caches parsed binary to `~/.decksync/cache/`
- `Platform` sealed type detected at startup from `System.getProperty("os.name")`
- `PlaceholderResolver` sealed interface with two implementations:
  - `WindowsPlaceholderResolver` — `<winAppData>`, `<winLocalAppData>`, `<winDocuments>`, `<winPublic>`, `<winDir>`
  - `LinuxPlaceholderResolver` — `<xdgData>`, `<xdgConfig>`, `<home>`
  - Both: `<game>`, `<storeUserId>`, `<root>`, `<base>`
- `SteamLibraryLocator`:
  - **Windows**: read registry key `HKCU\Software\Valve\Steam\SteamPath`
  - **Linux**: probe `~/.steam/steam/` and `~/.var/app/com.valvesoftware.Steam/.steam/steam/`, prefer native, log selection
  - Parse `libraryfolders.vdf` to find all library roots (users routinely install games on secondary drives)
- `ProtonPrefixResolver` (Linux only): given a Steam appid, returns the Wine prefix path; returns empty if `compatdata/<appid>/` doesn't exist yet
- **Resolver selection logic**: for each watched game, pick the manifest entry matching `(currentPlatform, store)`. On Linux, if no native Linux entry exists for a game but a Windows entry does AND the appid has a Proton prefix, use the Windows entry rooted at the prefix. This is the cross-play branch.
- `GameCatalog.resolveInstalled()` returns `Map<GameId, AbsolutePath>`
- `overrides.yml` parser supporting per-platform overrides
- CLI: `decksync list-games`

**Done when:** on Adam's Windows PC, `list-games` shows Windows paths for ≥3 installed games. On a SteamOS Deck, the same command shows **Proton prefix paths for the same Windows games**, plus correct native paths for any native Linux games. At least one game must be verified end-to-end as resolvable on both machines with matching `LogicalPath` semantics.

### M3 — Local manifest generation

- `FileScanner` walks a resolved game directory using `Files.walk`, producing `FileEntry` per file
- Hashing via SHA-256, streamed (no loading whole files into memory)
- Skip logic: file locked (`tryLock` fails) OR mtime within last 3 seconds
- Hash cache keyed by `(absolutePath, size, mtime)` so unchanged files don't rehash
- `ManifestBuilder` assembles per-game `Manifest`
- Cross-platform path normalisation in `LogicalPath` — backslashes converted to forward slashes on Windows
- WARN log when scanner detects two files differing only by case (Linux only — on Windows it can't happen)
- CLI: `decksync scan <gameId>` prints manifest as pretty JSON

**Done when:** scanning the same logical save folder on Windows and on a Proton prefix on Linux produces manifests with **identical `LogicalPath` values and identical hashes** for any file Proton hasn't modified.

### M4 — HTTP server

- `GET /v1/games` → list of known `GameId`s
- `GET /v1/games/{gameId}/manifest` → current manifest as JSON
- `GET /v1/games/{gameId}/files?path={logicalPath}` → streams file bytes, ETag = hex sha256, `Content-Length` set
- Binds `0.0.0.0` on configurable port (default `47824`)
- `@WebMvcTest` for controllers, `@SpringBootTest` with random port for an end-to-end call
- No TLS, no auth — explicitly insecure, logged loudly on startup

**Done when:** curl hits all three endpoints from a second machine on the LAN (in either OS direction) and retrieves a save file byte-for-byte identical to source.

### M5 — Sync engine

- `HttpSyncClient` using Spring `RestClient` with a typed API mirroring the endpoints
- `SyncPlanner` (pure domain) diffs local vs remote manifest per game, produces `SyncPlan`:
  - remote hash == local hash → `Skip`
  - remote mtime > local mtime AND hash differs → `Pull`
  - local mtime ≥ remote mtime → `Skip` (last-writer-wins)
  - missing locally → `Pull`
  - missing remotely but present locally → `Skip` (no deletes in Phase 1)
- `BackupService`: before any overwrite, copy existing file to `~/.decksync/history/<gameId>/<ISO-timestamp>/<logicalPath>` preserving directory structure
- `FileApplier`: atomic write via `.decksync.tmp` + `ATOMIC_MOVE`, preserves source mtime
- Retention: keep last 20 history snapshots per game, delete older ones on successful sync
- CLI: `decksync sync [--game <id>] [--dry-run]`

**Done when:** `TwoNodeSyncIT` passes — two Spring contexts on different ports with different save roots, edit on peer A, run sync on peer B, assert file copied AND backup created with correct previous contents. Test runs green on both Windows and Linux CI runners.

### M6 — Polish, packaging, and first real cross-platform sync

- Config validation at startup with clear, actionable errors (bad port, unknown game id, unreadable folder, malformed overrides)
- Structured JSON logging option (`--log.format=json`)
- `decksync status` — last sync time per game, peer reachability (HEAD `/v1/games` with 2s timeout)
- README with quickstart for **both Windows and SteamOS**, firewall snippets for both, screenshots
- **Packaging**:
  - `./gradlew packageMsi` — Windows MSI via jpackage (bundles JRE)
  - `./gradlew packageAppImage` — Linux AppImage via appimagetool wrapper (bundles Adoptium JRE)
  - Both built in CI on every release tag, attached to GitHub release
- SteamOS install guide using AppImage (drops in `~/Applications/`, sidesteps SteamOS read-only root)
- Systemd user service template for Linux autostart
- Windows scheduled task XML for autostart
- **First real cross-platform sync**: an actual game save synced between Adam's Windows PC and his SteamOS Deck, end-to-end

**Done when:** Adam has done a real-world Windows ↔ SteamOS sync of a save, it worked, and he trusts it for an actual playthrough.

## Test strategy

- **Unit**: all pure domain logic (planner, resolver expansion, hashing, manifest building). Fast, no Spring.
- **Slice**: `@WebMvcTest` for controllers, `@JsonTest` for DTO round-trips.
- **Integration**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` for the HTTP layer end to end.
- **Two-node**: `TwoNodeSyncIT` boots two Spring contexts with different profiles pointing at different temp save roots.
- **Cross-platform resolver tests**: golden-file tests for placeholder expansion on each platform, fed a fixture environment (`HOME`, `APPDATA`, etc).
- **Proton prefix simulation**: integration test creates a fake `compatdata/<appid>/pfx/drive_c/users/steamuser/AppData/Roaming/<game>/` structure under a temp dir, points the resolver at it, asserts correct path resolution.
- **CrossPlatformSyncIT**: simulates Windows-style absolute paths on one peer and Linux-style on the other within a single JVM (since CI can't easily run two OSes in one job), asserts that a `LogicalPath` round-trips correctly across the divide.
- **CI matrix**: every test suite runs on both `windows-latest` and `ubuntu-latest`.
- **Golden-file**: Ludusavi manifest parsing against a pinned manifest version.
- **Property**: hash stability — scanning identical folders yields identical manifests.

Target: 80% line coverage on `domain` and `application`, 50% overall. Coverage is a smell detector, not a goal.

## Dev loop

1. TDD the domain and application layers first — they have no Spring, run in milliseconds.
2. Wire up the Spring layer last per milestone; keep it thin.
3. Local two-node testing via `--spring.profiles.active=peerA` / `peerB` with different `config.yml` files under `src/test/resources/`.
4. Sample save fixtures in `sample-saves/` committed to the repo — small, fake, deterministic.
5. Adam develops primarily on his Windows PC. SteamOS validation happens at the end of each milestone via the actual Deck (or a Linux VM as a stand-in for fast iteration).

## Time estimate

Cross-platform adds roughly 30% to M2 (resolver work + Steam library locator + Proton prefix) and a couple of evenings to M6 (AppImage packaging + SteamOS install testing).

- M1: one evening
- M2: **two evenings** (was one)
- M3: one evening
- M4: one evening
- M5: one long weekend
- M6: **two evenings** plus a real-world cross-platform test session

Roughly **2–3 focused weekends** to a working cross-platform MVP, leaning on Claude Code for scaffolding.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Ludusavi manifest parsing slow on startup | Cache parsed binary on first load |
| SHA-256 on large save folders dominates runtime | Hash cache keyed by `(path, size, mtime)` |
| Java 21 on SteamOS | Bundle Adoptium JRE inside the AppImage; never rely on system Java |
| Steam Deck sleep/wake kills connections | Short timeouts, no connection pooling assumptions |
| Games lock files mid-sync | mtime-stable + `tryLock` probe (mtime is primary on Linux) |
| Windows Firewall block on first run | Document `netsh advfirewall` rule in README |
| Flatpak vs native Steam on SteamOS | Locator probes both, prefers native, logs choice |
| Proton prefix doesn't exist for never-launched game | Resolver returns empty; game listed as "not installed", skipped silently |
| SteamOS root read-only blocks install | AppImage runs from `~/Applications/`, no install required |
| Save format incompatibility (native Linux build vs native Windows build) | Out of scope for Phase 1; document limitation; trust Steam appid identity |
| Divergence (edits on both sides) | Versioned backup on every overwrite — no data loss, just history browse |
| Path separator confusion | All `LogicalPath` use forward slashes; only `*Resolver` classes touch native separators |

## Definition of "Phase 1 complete"

Adam runs DeckSync on his Windows PC and his SteamOS Deck for one full evening of Elden Ring split across both machines, and his save is where he expects it every time he switches, and he didn't lose progress. If that holds, ship it to GitHub, tag `v0.1.0`, and start Phase 2.
