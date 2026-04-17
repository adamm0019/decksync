# DeckSync

LAN peer-to-peer game save sync for Windows 11 and SteamOS / Linux. No cloud, no account, no telemetry — two machines on the same network keep each other's save files up to date over plain HTTP.

See [`CLAUDE.md`](CLAUDE.md) for the full project spec and [`docs/phase-1-plan.md`](docs/phase-1-plan.md) for the current roadmap.

## Build

```bash
./gradlew build              # compile, test, spotless, errorprone
./gradlew test               # unit tests only
./gradlew integrationTest    # two-JVM end-to-end tests
./gradlew check              # test + integrationTest + static analysis
./gradlew spotlessApply      # auto-format Java and Gradle sources
./gradlew bootRun            # run the CLI (no subcommands yet)
```

Requires Gradle's Foojay toolchain resolver to auto-provision Temurin 21 on first build; any JDK 17+ is sufficient as the bootstrap JVM.

## Install

Phase 1 packaging targets:

```bash
./gradlew packageMsi         # Windows MSI via jpackage
./gradlew packageAppImage    # Linux AppImage via appimagetool
```

Both bundle a JRE so the target machine does not need Java installed. Install the MSI on Windows; drop the AppImage anywhere writable on SteamOS (e.g. `~/Applications/`) and `chmod +x` it.

## Quickstart

You need **two machines on the same LAN**. Pick one machine's LAN IP as the peer URL on the *other* machine and vice versa. Port `47824/tcp` is the default.

### 1. Create `~/.decksync/config.yml` on each machine

On PC A (IP `192.168.1.10`):

```yaml
peer:
  url: http://192.168.1.11:47824
port: 47824
retention: 20
games:
  - steam:1245620      # Elden Ring
  - stardew-valley
```

On PC B (IP `192.168.1.11`): same file, but `peer.url: http://192.168.1.10:47824`.

All fields are optional — `config.yml` can be absent and the defaults (`peerUrl=http://localhost:47824`, `port=47824`, `retention=20`, `games=[]`) will apply. If `games` is omitted, every game resolvable on *both* peers is synced.

### 2. Open the firewall

**Windows 11** — from an elevated PowerShell:

```powershell
netsh advfirewall firewall add rule `
  name="DeckSync" `
  dir=in action=allow `
  protocol=TCP localport=47824 `
  profile=private
```

Remove later with:

```powershell
netsh advfirewall firewall delete rule name="DeckSync"
```

**SteamOS / Linux** — SteamOS ships with no firewall enabled by default, so nothing needs to be done. If you have `firewalld` installed (e.g. Fedora, some self-managed Decks):

```bash
sudo firewall-cmd --permanent --add-port=47824/tcp
sudo firewall-cmd --reload
```

### 3. Verify

On each machine:

```bash
decksync list-games          # per-game resolved save paths on this host
decksync status              # peer reachability + last-sync time per game
decksync sync --dry-run      # show what would change, apply nothing
decksync sync                # do it
```

`decksync sync` always runs pull-only and hash-then-mtime last-writer-wins. Before overwriting a local file, the old version is copied to `~/.decksync/history/<gameId>/<timestamp>/`. The newest `retention` snapshots per game are kept and older ones pruned.

## Run the daemon

`decksync serve` is the long-running HTTP endpoint that exposes this machine's manifests and save files to the peer. The sync direction is opportunistic: you run `decksync sync` on the machine that wants to *pull* fresh state, and `decksync serve` on the machine that *has* it. Most setups run both on both machines.

### Windows — scheduled task at login

Save as `decksync-serve.xml` and import with `schtasks /Create /TN DeckSync /XML decksync-serve.xml`:

```xml
<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.4" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <Triggers>
    <LogonTrigger>
      <Enabled>true</Enabled>
    </LogonTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author">
      <LogonType>InteractiveToken</LogonType>
      <RunLevel>LeastPrivilege</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <StartWhenAvailable>true</StartWhenAvailable>
    <RestartOnFailure>
      <Interval>PT1M</Interval>
      <Count>3</Count>
    </RestartOnFailure>
  </Settings>
  <Actions Context="Author">
    <Exec>
      <Command>C:\Program Files\DeckSync\decksync.exe</Command>
      <Arguments>serve --log.format=json</Arguments>
    </Exec>
  </Actions>
</Task>
```

Remove with `schtasks /Delete /TN DeckSync /F`.

### SteamOS / Linux — systemd user service

Save as `~/.config/systemd/user/decksync.service`:

```ini
[Unit]
Description=DeckSync save sync daemon
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=%h/Applications/decksync.AppImage serve --log.format=json
Restart=on-failure
RestartSec=10s

[Install]
WantedBy=default.target
```

Enable:

```bash
systemctl --user daemon-reload
systemctl --user enable --now decksync
loginctl enable-linger $USER   # keep it running after logout / during sleep-wake
journalctl --user -u decksync -f
```

Adjust the `ExecStart` path to wherever you dropped the AppImage. `enable-linger` matters on the Steam Deck — without it the service stops as soon as you exit desktop mode.

## Logs

Default output is human-readable. Pass `--log.format=json` (translated internally to Spring Boot 3.4's ECS structured console format) for machine-parseable logs — recommended when running under systemd or Task Scheduler so `journalctl` / Event Viewer capture structured fields.

## Troubleshooting

- **`decksync status` says UNREACHABLE.** Ping the peer's IP first. If ping works but the probe fails, the firewall on the peer is blocking `47824/tcp` — see the snippets above. Remember the `profile=private` filter on Windows: the rule only applies when the adapter is on a private network.
- **"No installed games resolved on this host."** Either nothing in `config.yml`'s `games:` list matches a game the Ludusavi manifest knows about, or the games are not installed where the resolver looks. Check with `decksync list-games`.
- **Steam Deck cold-starts slowly after sleep.** Expected — the Deck aggressively suspends. The daemon handles reconnection; the first `decksync status` after wake may time out, the second will succeed.
- **Proton game shows no resolved path on the Deck.** The Proton prefix under `steamapps/compatdata/<appid>/pfx/` is created on first launch. Launch the game once, then re-run `decksync list-games`.
