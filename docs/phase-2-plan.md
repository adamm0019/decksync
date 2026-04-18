# DeckSync — Phase 2 Plan

## Goal

Take DeckSync from "works for Adam on his LAN" to "trustworthy enough for a friend to install." Phase 1 proved the sync engine; Phase 2 makes it secure, automatic, and safe to leave running unattended.

The GUI already exists (shipped end of Phase 1). Phase 2 is mostly engine work, with the GUI gaining new surfaces — most notably the conflict resolution sheet — wired to new engine capabilities.

## Theme

**Trust and automation.** Three shifts:

1. **Trust**: TLS + mutual auth means the daemon can be on a LAN without being a trivial attack surface. Discovery replaces IP typing.
2. **Automation**: filesystem watcher replaces periodic poll. Autostart is installed by the app, not documented in a README.
3. **Safety**: deletes propagate with tombstones but require confirmation; genuine conflicts get a proper UI instead of "last writer wins."

## Success criteria

Phase 2 is done when Adam can:

1. Install DeckSync on a fresh Windows machine and a fresh SteamOS Deck, and they find each other during setup with no IP typed.
2. Leave both machines running for a week, play normally, and find every save where he expects it without ever opening the GUI.
3. Intentionally edit a save on both sides between syncs, open the GUI, and resolve the conflict via the review sheet with no data loss.
4. Delete a save slot on one side and have the other side honour the deletion after a confirmation click.
5. Hand the MSI to a friend, have them install, pair, and sync their own Deck to their own PC without Adam on a call.

## Out of scope

Deferred to Phase 3:

- Save-file introspection ("Hornet boss defeated, 14.2h") — the history timeline scrubber already shipped in Phase 1 without this and stays unchanged in P2.
- Play-state lock via Steam process monitoring.
- QR pairing (P2 uses fingerprint confirmation over the discovered LAN connection).
- Screenshot attachments to sync events.
- Light theme.
- N-way sync (>2 peers). Still a 2-peer system in P2.
- macOS support.
- Internet/Tailscale reachability.

Already shipped in Phase 1 (do not re-scope into P2): JavaFX dashboard, per-game cards with **Steam header art** fetched from the Steam CDN with on-disk cache, sync preview sheet, history timeline scrubber with rollback, settings destination, 5-step first-run wizard, expandable log drawer with in-memory appender, keyboard accelerators.

## Architecture deltas from Phase 1

Phase 1 was a single-module Gradle project. Phase 2 splits it:

```
decksync/
├── core/        // domain + application + infrastructure (engine)
├── gui/         // JavaFX app, depends on :core
├── cli/         // Picocli app, depends on :core
└── buildSrc/    // shared Gradle conventions
```

The split enforces the GUI/engine boundary that was an informal rule in Phase 1. ArchUnit tests in `:core` enforce the hexagonal dependency rule at build time.

New Phase 2 engine concepts:

- `PeerIdentity` — a 256-bit fingerprint derived from the device's TLS certificate public key. Stable across restarts, rotates only on explicit re-pairing.
- `PairingRecord` — `{peerName, fingerprint, firstSeen, lastSeen}` persisted in `~/.decksync/peers.yml`. Phase 2 is still 2-peer, but the data model supports N so Phase 3+ doesn't need a migration.
- `Tombstone(LogicalPath, Instant deletedAt, PeerIdentity deletedBy)` — sidecar metadata for deletes, persisted alongside manifests.
- `ConflictRecord` — both sides modified the same file since last common ancestor. Surfaced to the GUI; blocks sync for that game until resolved.
- `WatchEvent` → debounced → `SyncTrigger`. The watcher doesn't sync directly; it enqueues triggers that the existing sync engine consumes. Keeps the engine synchronous and testable.

## Milestones

### M7 — Modularisation & discovery

Foundational work. Land before anything else in P2 to avoid rework.

- Split Gradle project into `:core`, `:gui`, `:cli`, `buildSrc`
- Move existing Phase 1 code into `:core` with no behavioural changes; all Phase 1 tests still pass
- ArchUnit rules in `:core`: `domain` imports nothing from `infrastructure`/`web`/Spring; `application` imports only `domain`
- Add jmDNS dependency
- `DiscoveryService` advertises `_decksync._tcp.local.` on startup with TXT record containing: `{peerName, fingerprint, version, protocolVersion}`
- `DiscoveryService` subscribes to the same service type and maintains a `DiscoveredPeers` registry with liveness (peers drop after 60s without a response)
- Setup wizard: **insert a new "Find peer" step between the Welcome and Peer URL steps** — shows "Searching for devices…" with a live list of discovered peers. Selecting a peer pre-fills the existing Peer URL step. The manual URL entry stays as a "Pair manually" fallback. Wizard grows from 5 steps to 6.
- GUI peer pill: **rewire the existing pill** to read live from discovery rather than from `config.peerUrl()` via `PeerReachability`. Config still stores the paired fingerprint; discovery provides the current address.
- Log drawer (already shipped): **add a "Network" filter toggle** to the existing drawer toolbar, filtering rows by logger name prefix to show discovery events only.

**Done when:** on two machines freshly booted on the same LAN, each sees the other within 5 seconds of both being online, with no manual config. Phase 1 tests green across the module split.

### M8 — TLS and pairing

Security milestone. No "push this off" — Phase 2 is about trust.

- One-time CA generation at setup: each device generates its own self-signed root and a leaf cert signed by it, stored encrypted in `~/.decksync/keys/` with a key derived from the OS keychain where available (Windows DPAPI / libsecret on Linux), falling back to a machine-bound key file with user-readable permissions
- Spring Boot web layer serves HTTPS on port 47824 (HTTP fallback removed from production builds; kept as `--insecure` flag for local dev only, loudly logged)
- `HttpSyncClient` validates peer's cert against the pinned fingerprint from `PairingRecord`. Fingerprint mismatch → refuse, log loudly, surface in GUI as "Peer identity changed — re-pair required"
- Pairing flow: during setup after a peer is discovered, both sides show a **6-word fingerprint** (BIP-39 style word encoding of a 48-bit prefix of the SHA-256 of the cert). User confirms the words match on both screens. This is the trust-on-first-use step.
- `POST /v1/pair` endpoint: accepts the peer's cert + fingerprint, returns own cert + fingerprint. Only callable once per peer until explicitly re-paired.
- Re-pair flow in Settings → Peer → "Re-pair this device" (destroys existing `PairingRecord`, runs pairing again)
- CLI: `decksync pair --peer <host>` for headless pairing on SteamOS without opening the GUI
- Pairing may pre-seed an entry in `overrides.yml` for games the peer has but the local Steam doesn't — Phase 1's override-only resolution (catalog iterates overrides even without a matching Steam appid) means these games are immediately syncable, no local install required.

**Done when:** a man-in-the-middle on the LAN cannot intercept a sync (tested by running a rogue DeckSync instance claiming the same mDNS name — legit client refuses to connect). Fresh-install-to-first-sync takes under 2 minutes including pairing.

### M9 — Filesystem watcher

Makes sync reactive. Phase 1 shipped with no background sync loop at all — sync runs on-demand from the CLI or the GUI "Sync now" button. M9 introduces the first automatic trigger. Biggest UX improvement of the phase.

- `FileSystemWatcherService` using `java.nio.file.WatchService`, one watch registration per resolved game save directory (recursive via manual subdirectory registration — NIO doesn't do recursive watches natively on Linux)
- Debouncer: a file must be mtime-stable for ≥3 seconds AND `tryLock` must succeed before triggering a sync (reuses the Phase 1 stability logic)
- Event coalescing: bursts of writes in one directory collapse to a single `SyncTrigger` per game
- Backpressure: if sync is in progress for a game, further triggers for that game are collapsed to a single pending trigger
- Safety-net periodic scan every 15 minutes (catches missed events, renames, network folder quirks) — this is the *first* time DeckSync runs anything on a schedule
- GUI toast on successful auto-sync: "Hollow Knight synced from Deck" — dismissable, accumulates in a notification history accessible from the top bar
- Setting: "Watch mode" toggle — `Reactive (recommended)` | `Periodic only (battery saver)` | `Manual only`. Persisted per device. Manual-only preserves the Phase 1 behaviour.

**Done when:** saving a file in a watched game directory on peer A results in it appearing on peer B within 10 seconds, on a cold LAN, 20 times in a row without a miss.

### M10 — Deletes with tombstones

Careful milestone. Deletes are where sync tools ruin user data.

- When the watcher or a scan detects a file present in the previous manifest but absent now, create a `Tombstone` entry
- Manifests now include `tombstones: [...]` alongside `files: [...]`. Protocol bumps to `v2`. Phase 1 ships as `v0.1.0` before M10 starts — that tag is the anchor for the compat story: `/v1/` endpoints retained for one release cycle with a shim that drops tombstones on the wire (a v1 client simply won't delete, which is the safe failure mode). If `v0.1.0` is never tagged, delete this bullet and just bump the protocol.
- Sync planner gains two new actions: `DeleteLocal(path, sourceTombstone)` and `RetainAgainstTombstone(path, reason)` — the latter fires when the local file has been modified after the tombstone was created, meaning the user edited it after deleting elsewhere
- **GUI confirmation is the default.** Sync preview sheet shows deletions in a separate red section: "3 files will be removed on this device." Requires an explicit "Include deletions" checkbox (default off on first run; becomes default on after user has approved one deletion batch — tracked as a per-peer preference)
- Tombstones expire: after 30 days, a tombstone with no conflicting activity is garbage collected. Recreating a file after expiry treats it as new.
- CLI: `decksync sync --allow-deletes` for scripted use
- Backup before delete: every deletion, even user-approved, first copies the file to `~/.decksync/history/<gameId>/<timestamp>/` exactly as overwrites do. Restore via timeline scrubber works identically.

**Done when:** deleting a save slot in-game on peer A, running a sync on peer B with "Include deletions" checked, results in the slot gone on B — and the timeline scrubber shows the deleted file and can restore it.

### M11 — Conflict resolution

The headline GUI feature of Phase 2.

- `ConflictDetector` runs as part of planning: a conflict exists when the local file's mtime > last successful sync mtime AND the remote file's mtime > last successful sync mtime AND their hashes differ
- Per-game conflicts block automatic sync for that game; the game card shows the `⚠ Conflict` chip and the "Review" button
- **Conflict review sheet** (new GUI surface):

```
┌─────────────────────────────────────────────────────────────────┐
│                     Resolve Hollow Knight                       │
│                                                                 │
│   Both you and Deck edited this save since the last sync.       │
│   Pick which version to keep — or keep both.                    │
│                                                                 │
│   ┌───────────────────────────┐   ┌───────────────────────────┐ │
│   │  This PC                  │   │  Steam Deck               │ │
│   │  user1.dat                │   │  user1.dat                │ │
│   │  Modified 14m ago         │   │  Modified 2m ago          │ │
│   │  420 KB                   │   │  418 KB                   │ │
│   │                           │   │                           │ │
│   │  [ Preview ]  ◉ Keep      │   │  [ Preview ]  ○ Keep      │ │
│   └───────────────────────────┘   └───────────────────────────┘ │
│                                                                 │
│   ○ Keep both (rename the loser to user1.conflict-<time>.dat)   │
│                                                                 │
│   ☑ Back up the discarded version to history before replacing   │
│                                                                 │
│   [ Cancel ]                                      [ Resolve ]   │
└─────────────────────────────────────────────────────────────────┘
```

- Keep-both produces a `*.conflict-<ISO-timestamp>.<ext>` sibling file, synced to both peers; games ignore the unfamiliar filename
- Preview button opens a read-only view appropriate to the file (hex for binary in P2; save-format-aware preview is a P3 feature)
- Multi-file conflicts within one game show a scrollable list in the sheet, one "keep local / keep remote / keep both" decision per file. "Apply this choice to all" quick action.
- Resolutions are logged to history as a distinct event type: `conflict-resolved`. Timeline scrubber marks these with a distinct symbol.

**Done when:** Adam can trigger a conflict by editing the same save on both machines between syncs, open the review sheet, and resolve it three different ways (keep local / keep remote / keep both) in three consecutive tests, with no data loss and correct history entries.

### M12 — Autostart & packaging

Ship-readiness milestone.

- **Windows**: setup wizard gains a new "Autostart" step (inserted between Behaviour and Done, so the wizard is 7 steps by end of M12 — or 6 if M7's "Find peer" step hasn't landed yet). The toggle actually installs a Scheduled Task (XML template generated on the fly) and a Start Menu shortcut. Uninstall via MSI removes both. The Settings destination gets the same toggle so it can be flipped after first run.
- **Linux**: generates a systemd user unit `~/.config/systemd/user/decksync.service` and runs `systemctl --user enable --now decksync`. Works on SteamOS without needing root.
- Packaging now produces signed artifacts:
  - Windows MSI signed with a self-signed cert (real code signing deferred; Windows SmartScreen warning documented in README)
  - Linux AppImage with an embedded update channel stub (P3 feature, inert in P2)
- GUI runs as a tray/background app on Windows; on SteamOS the daemon is headless and the GUI is a separate process the user launches from their application menu
- `decksync service status|start|stop|restart` CLI subcommand for managing the installed service
- Auto-update check on GUI startup: HEAD request to a GitHub releases URL, shows a toast if a newer version exists. No auto-download in P2 — just the nudge.

**Done when:** fresh install on a clean Windows VM results in DeckSync running after reboot without the user opening anything. Same on a clean SteamOS image.

## Test strategy deltas

Phase 2 adds meaningful integration complexity. New test categories:

- **Discovery tests**: `DiscoveryIT` boots two Spring contexts on the same loopback, asserts each sees the other within a timeout. Uses jmDNS's testability hooks rather than real multicast where possible.
- **TLS tests**: `TlsHandshakeIT` verifies cert pinning: genuine peer accepted, rogue peer with same mDNS name rejected, tampered cert rejected. Uses ephemeral CA per test.
- **Watcher tests**: `WatcherIT` creates a temp directory, mutates files with known timings, asserts the debouncer produces the expected number of triggers. Includes a fuzzing test that writes bursts of random sizes.
- **Conflict tests**: `ConflictDetectorTest` (unit, pure logic) covers the 9 possible states of `(local-changed?, remote-changed?, hashes-match?, last-sync-present?)`. `ConflictResolveIT` drives the GUI sheet via TestFX and asserts engine state after each resolution type.
- **Upgrade tests**: `ProtocolCompatibilityIT` runs a v2 client against a v1 server and vice versa; ensures no deletes cross the boundary silently.
- **Chaos test**: `ChaosSoakIT` (nightly, not on every PR) runs two peers for 4 hours with a fake player randomly mutating saves and expects no lost or corrupted files at the end.

CI matrix expands: Windows + Linux × JDK 21, plus a nightly soak job on Linux only.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| jmDNS unreliable on Windows with multiple network interfaces | Bind to a specific interface selected at setup; let user override |
| Users skip fingerprint verification, reducing TOFU to theatre | Make the 6-word display prominent and mutual (both screens show until confirmed on both); log the acceptance |
| `WatchService` on SteamOS Btrfs has known edge cases | Keep the 15-min periodic scan as safety net; document the limitation |
| Tombstones cause accidental data loss | Always back up before delete; require explicit user approval by default; expire tombstones after 30 days |
| Conflict UI overwhelms users with many files | Group files by change type; "apply to all" shortcuts; scroll pane with sticky decision bar |
| Code signing cert cost for Windows MSI | Ship self-signed in P2; document SmartScreen workaround; real cert in P3 if the project has legs |
| Systemd user units differ slightly between SteamOS and vanilla Linux | Test on SteamOS 3.5 specifically before ship; fall back to `~/.config/autostart/` XDG if systemd not available |
| Keychain integration fails silently | Log loudly, fall back to user-permission-protected key file, warn in GUI Settings → Peer |
| Re-pair flow gets confused after a cert rotation mid-sync | Abort in-flight sync on fingerprint change, clear state, force re-pair through the wizard |

## Milestone ordering notes

Phase 2 is denser than Phase 1 — less scaffolding, more careful work.

- M7 lands first: the module split has to happen before discovery code starts piling up, or the refactor becomes a mountain.
- M8 follows M7: discovery surfaces peers, but until TLS + pairing exists, connecting to one of them is insecure.
- M9 can run after M8 or in parallel — the watcher has no security dependency.
- M10 depends on M9: tombstones are generated by the watcher/scan path.
- M11 depends on M10 loosely (conflicts and deletes share planner surface) and fully depends on M9 (auto-sync is what produces conflicts worth resolving in the GUI).
- M12 lands last: autostart only makes sense once the daemon is trustworthy enough to run unattended.

Security code (M8) and conflict UI (M11) are the two milestones most likely to feel half-done if rushed — budget accordingly.

## Definition of "Phase 2 complete"

Adam installs DeckSync from the MSI/AppImage on two freshly imaged machines, goes through setup with no terminal involvement, plays a mixed Windows/Deck session across an evening, deliberately triggers one conflict and resolves it via the GUI, deletes a save slot and sees it propagate with his approval, and at the end of the evening every save is where he expects. If that holds, tag `v0.2.0`, write a release post, and start Phase 3.
