# DeckSync

## What this is

A LAN peer-to-peer save file sync daemon for **Windows 11 and SteamOS / Linux**. Runs on any pair of machines (Windows ↔ Windows, Windows ↔ SteamOS, SteamOS ↔ SteamOS), watches configured game save folders, and keeps them in sync without any cloud service.

Game-aware: understands that "Elden Ring save" is the same logical entity despite living at completely different absolute paths on each machine — including when the same Windows game runs natively on a Windows PC and under Proton on a SteamOS Deck. Protocol ships only `(GameId, logical-path)`, never absolute paths.

**Cross-platform model**: Proton runs Windows binaries unmodified, so a Windows-native game writes bit-identical save bytes whether it's running on Windows or under Proton on SteamOS. DeckSync only needs to find the right absolute path on each side — the payload itself never needs translation. Native-Linux-build ↔ native-Windows-build pairs are rare for actual save data (more common for config files) and out of scope for Phase 1; the conservative rule is "if both peers see the game as the same Steam appid, they assume save format compatibility".

## Tech stack

- **Java 21** (LTS — records, sealed types, pattern matching, virtual threads)
- **Spring Boot 3.x** — web layer, DI, config, testing
- **Gradle (Kotlin DSL)**
- **Jackson** for JSON, **SnakeYAML** for config + Ludusavi manifest
- **Picocli** for CLI
- **JUnit 5 + AssertJ + WireMock** for tests
- **SLF4J + Logback** for logging
- **JavaFX 21 + FXML + Scene Builder** — GUI, Phase 2 onwards
- **jpackage** (Windows MSI) + **appimagetool** (Linux AppImage) — packaging

Keep the dependency list small and boring. Do not add new libraries without explicit discussion.

## Build / test / run

```bash
./gradlew build              # full build + tests + spotless
./gradlew test               # unit tests only
./gradlew integrationTest    # two-JVM end-to-end sync test
./gradlew bootRun --args='--spring.profiles.active=peerA'
./gradlew bootRun --args='--spring.profiles.active=peerB'
./gradlew spotlessApply      # autoformat
./gradlew check              # test + static analysis
./gradlew packageMsi         # Windows MSI via jpackage
./gradlew packageAppImage    # Linux AppImage via appimagetool
```

CLI (after install):

```bash
decksync list-games          # resolved save paths per configured game (per platform)
decksync scan <gameId>       # generate and print local manifest
decksync sync [--game <id>] [--dry-run]
decksync status              # last sync time per game, peer reachability
```

## Architecture

Hexagonal / ports-and-adapters. Strict dependency rule: `domain` imports nothing from `infrastructure`, `web`, or Spring.

```
dev.decksync
├── domain           // pure model: records, sealed types, no frameworks
├── application      // use cases, orchestration, port interfaces
├── infrastructure   // adapters: filesystem, Ludusavi parser, HTTP client, Steam locators
├── web              // Spring MVC controllers, DTOs
├── cli              // Picocli commands
└── config           // Spring @Configuration classes
```

*(Package root `dev.decksync` is a placeholder — swap for your own namespace.)*

### Key domain types (all records / sealed)

- `GameId` — stable logical id, derived from Steam appid when available, otherwise a slug
- `LogicalPath` — game-relative path with forward slashes, e.g. `saves/slot_0.sav`
- `Sha256` — value wrapper around a 32-byte hash
- `FileEntry(LogicalPath path, long size, Instant mtime, Sha256 hash)`
- `Manifest(GameId game, List<FileEntry> files, Instant generatedAt)`
- `Peer(String name, InetSocketAddress endpoint, PeerId id)`
- `Platform` — sealed: `Windows | Linux` (extensible to `Mac` later)
- `SyncAction` — sealed: `Pull | Skip | Conflict` (no `Delete` in Phase 1)
- `SyncPlan(GameId game, List<SyncAction> actions)`

## Save path resolution

**Rule: the protocol never carries absolute paths.**

- Bundled `src/main/resources/ludusavi-manifest.yaml` is the source of truth for where games store saves. Ludusavi entries are tagged with `os` (windows/linux/mac) and `store` (steam/gog/etc).
- `PlaceholderResolver` is a **sealed interface** with two implementations chosen at startup from `System.getProperty("os.name")`:
  - `WindowsPlaceholderResolver` — expands `<winAppData>`, `<winLocalAppData>`, `<winDocuments>`, `<winPublic>`, `<winDir>`
  - `LinuxPlaceholderResolver` — expands `<xdgData>`, `<xdgConfig>`, `<home>`
- Both resolvers handle OS-agnostic placeholders: `<game>`, `<storeUserId>`, `<root>`, `<base>`.
- The resolver picks the manifest entry matching `(currentPlatform, currentStore)` for each game.
- **Proton games on Linux**: Windows-native games running under Proton on SteamOS store saves inside per-game Wine prefixes. `ProtonPrefixResolver` (Linux only) takes a Steam appid and returns `<steamRoot>/steamapps/compatdata/<appid>/pfx/drive_c/users/steamuser/`. The Linux resolver layers Windows placeholders (e.g. `<winAppData>`) on top of this prefix path when the manifest entry is tagged `os: windows` but the runtime is Linux. **This is the cross-play magic** — same `LogicalPath` either side, different absolute resolution.
- `SteamLibraryLocator` finds Steam libraries by parsing `libraryfolders.vdf`:
  - **Windows**: registry key `HKCU\Software\Valve\Steam\SteamPath`, fallback `C:\Program Files (x86)\Steam`
  - **Linux native**: `~/.steam/steam/`
  - **Linux Flatpak**: `~/.var/app/com.valvesoftware.Steam/.steam/steam/`
  - On Linux, probe both, prefer native, log which was chosen
- User overrides in `~/.decksync/overrides.yml` support per-platform overrides:
  ```yaml
  elden-ring:
    windows: 'D:\Saves\EldenRing'
    linux:   '/home/deck/custom/eldenring'
  ```
- `GameCatalog.resolveInstalled()` returns `Map<GameId, AbsolutePath>` for games resolvable on this machine.

## Coding conventions

- **Java 21 idioms**: records for data, sealed types for alternatives, pattern matching in switch, `var` where it aids readability.
- **No null** returned from any public method. `Optional` only at module boundaries. Prefer sealed types over optionals for binary alternatives.
- **Constructor injection only.** Never `@Autowired` fields. Never field injection in tests.
- **One adapter per port.** Don't pre-generalise — if there's one implementation, there's one implementation. (Exception: the platform resolvers, which are sealed precisely because both impls exist from day one.)
- **Value equality everywhere** in the domain. Records give this for free.
- **Time**: `Instant` internally, `ZonedDateTime` only at UI/log boundaries. Never `Date`, never `LocalDateTime` for anything persisted or transmitted.
- **IO**: NIO2 (`java.nio.file`). Always `Files.move(src, tmp, ATOMIC_MOVE)` for writes.
- **Hashing**: `MessageDigest` SHA-256, always wrapped in the `Sha256` record. Never pass raw byte arrays around.
- **Tests**: TDD preferred. One behaviour per test. Descriptive names: `syncPlanner_whenRemoteNewerAndHashDiffers_producesPullAction`. AAA layout.
- **Logging**: structured, one line per sync action at INFO. DEBUG for per-file scan detail. No stack traces at WARN — promote to ERROR or swallow with context.

## Known gotchas

1. **Open file handles.** Games hold save files open while running. Before reading, require mtime stable for ≥ 3s AND probe with `FileChannel.tryLock()`. Skip and log if locked. Note: `tryLock` is **mandatory on Windows but advisory on Linux** — the mtime-stability check is the primary signal everywhere.
2. **Atomic writes.** Write to `<target>.decksync.tmp` in the same directory, then `ATOMIC_MOVE`. Cross-volume moves are not atomic — refuse to sync across volumes.
3. **Forward slashes in LogicalPath.** Both Windows and Linux native paths get normalised to `/` in protocol messages and equality comparisons.
4. **Case sensitivity.** Linux filesystems are case-sensitive (`Save.sav` ≠ `save.sav`); Windows is not. The scanner preserves exact casing in `LogicalPath`. When syncing Linux → Windows, log a WARN if two files differ only by case (rare but possible for native Linux games).
5. **Ludusavi manifest is ~5MB of YAML.** Cache the parsed form to a binary file in `~/.decksync/cache/` on first load.
6. **Hash cost on large saves.** Cache `(path, size, mtime) -> Sha256`. Only rehash when size or mtime change.
7. **Firewall on first run.** Windows Defender Firewall blocks inbound by default — document the `netsh advfirewall` snippet in README. SteamOS has no firewall by default but if `firewalld` is installed, document `firewall-cmd --add-port=47824/tcp`. mDNS multicast blocking becomes a Phase 2 problem.
8. **Steam Deck sleep.** The Deck sleeps aggressively regardless of OS. Daemon must tolerate cold starts on wake. Don't assume long-lived connections.
9. **Proton prefix may not exist yet.** A game's `compatdata/<appid>/pfx/` is created on first Proton launch. If a user adds a game to DeckSync before launching it once on the Deck, the resolver returns "not yet installed" and skips gracefully — never errors.
10. **SteamOS read-only root.** `/usr` is immutable on SteamOS by default. Install to `~/.local/bin/` and use a systemd user service for autostart. AppImage handles this naturally — drops anywhere the user has write access.

## Things NOT to do

- Don't add new dependencies without asking. Stack is deliberately small.
- Don't use Lombok. Records cover 95% of cases; write the rest by hand.
- Don't let Spring controllers touch domain directly — go through an application service.
- Don't introduce async/reactive code in Phase 1. Blocking IO on 2-node LAN is fine and simpler to reason about.
- Don't persist or transmit absolute filesystem paths. Ever.
- Don't write "defensive" null checks in domain code — the types guarantee non-null.
- Don't invent crypto. TLS is deferred to Phase 2 and will use standard JSSE with self-signed certs.
- **Don't hardcode platform paths anywhere outside the `*Resolver` classes.** If domain or application code touches `\`, `C:\`, or `/home/`, it's a bug.
- **Don't assume the user is `deck` on SteamOS.** Use `System.getProperty("user.name")` and `user.home`.

## Current phase

See [`docs/phase-1-plan.md`](docs/phase-1-plan.md).

**In scope for Phase 1:** CLI + engine, HTTP (no TLS), pull-based sync, versioned backups, Ludusavi-driven path resolution, periodic poll, **cross-platform Windows + SteamOS/Linux**, Proton prefix support, MSI + AppImage packaging.

**Out of scope for Phase 1:** JavaFX GUI, mDNS discovery, TLS/auth, filesystem watcher, Steam process integration, deletes, conflict resolution beyond last-writer-wins, macOS, native-Linux-build ↔ native-Windows-build save translation.

## Runtime layout

`~/.decksync/` on both Windows and Linux. We deliberately ignore XDG conventions on Linux for cross-platform parity — `System.getProperty("user.home")` resolves correctly on both, and config files live in the same relative location regardless of OS.

```
~/.decksync/
├── config.yml          # peer address, watched games, port, backup retention
├── overrides.yml       # per-game, per-platform path overrides
├── cache/              # parsed ludusavi manifest, hash cache
└── history/            # versioned backups
    └── <gameId>/
        └── <ISO-timestamp>/
            └── <logicalPath>
```
