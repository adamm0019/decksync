# DeckSync

LAN peer-to-peer game save sync for Windows 11 and SteamOS / Linux.

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
