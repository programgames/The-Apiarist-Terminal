# Development Guide

This document is for developers and CI maintainers working on The Apiarist Terminal.

- Game/Tooling target: Minecraft Forge 1.12.2, ForgeGradle 2.3, Gradle wrapper 4.10.3, Java 8.
- Primary goal: expose Gendustry machines to OpenComputers as components
  (currently `advmutatron` and `industrial_apiary`).

## Project layout
- `src/main/java/net/ocgendustry/OCGendustryMod.java` — Mod entrypoint; hard requires OC + Gendustry, loads `Config`, calls `DriverRegistry`, registers the server command.
- `src/main/java/net/ocgendustry/Config.java` — Forge config (categories `general`, `advanced_mutatron`, `industrial_apiary`, `integration_test`).
- `src/main/java/net/ocgendustry/Log.java` — Log4j wrapper adding the `[ApiaristTerminal]` tag.
- `src/main/java/net/ocgendustry/driver/DriverRegistry.java` — Central place to register all drivers (add more here as you extend support).
- `src/main/java/net/ocgendustry/driver/DriverAdvMutatron.java` — Driver for Advanced Mutatron (component: `advmutatron`).
- `src/main/java/net/ocgendustry/driver/DriverApiary.java` — Read-only driver for Industrial Apiary (component: `industrial_apiary`).
- `src/main/java/net/ocgendustry/driver/MachineDriver.java` / `MachineEnvironment.java` / `ItemMachineEnvironment.java` — Shared base for the eight processing machines.
- `src/main/java/net/ocgendustry/driver/Driver{Mutatron,Sampler,Imprinter,Replicator,Transposer,Extractor,Liquifier,MutagenProducer}.java` — One small class per processing machine: component name, slots, tanks.
- `src/main/java/net/ocgendustry/util/` — Pure-Java helpers (`Tuning` clamps, `MutatronLogic` selection) kept free of MC types so they are unit-testable.
- `src/main/java/net/ocgendustry/client/` — Forge config GUI (`GuiFactory`, `GuiModConfig`).
- `src/main/java/net/ocgendustry/command/OcGendustryCommand.java` — Gated in-game test harness (`/ocgendustry test advmutatron [fresh|reuse|all]`).
- `src/main/resources/mcmod.info` — Mod metadata (hard depends on OC + Gendustry).
- `src/main/resources/META-INF/ocgendustry_at.cfg` — Access transformer referenced by the jar manifest (`FMLAT`); FML resolves it relative to `META-INF`.
- `src/main/resources/assets/ocgendustry/disk/` — the floppy `LootDisk` registers, laid out
  verbatim: `.prop`, `usr/lib/apiarist.lua`, `usr/bin/*.lua`. The path is load-bearing:
  `FileSystem.fromClass` resolves `/assets/<modid>/disk/` literally, so moving the directory breaks
  the disk. The two `advmutatron_*` rigs are also what the test harness writes out.
- `dev/scripts/*.lua` — acceptance tooling, **not** in the jar: `testall` walks every machine,
  `machine_test` takes one apart, `checkall` covers the library and the apiary, `nofreeze` times
  `selectAndProduce` and reports whether the call held the server thread. `./gradlew
  installDevScripts` copies all of it onto a dev computer.
- `src/test/java/` — JUnit 4 + AssertJ unit tests; no Minecraft bootstrap.
- `docs/components/<component>.md` — Per-component callback reference.

## Prerequisites
- JDK 8 on PATH (JAVA_HOME set to JDK 1.8).
- Gradle Wrapper is included (no manual Gradle installation needed).

## Dependency management: CurseMaven auto-download + deobf
This project can auto-download Gendustry and bdlib from CurseMaven and have ForgeGradle remap them (deobf) at compile time.
Configure the CurseForge project/file IDs via `gradle.properties` or `-P` overrides.

### Option A: gradle.properties (recommended for local dev)
Edit `gradle.properties` in the repository root and set numeric IDs:

```
# examples — replace with real IDs from CurseForge
# Gendustry
gendustryProjectId=XXXXX
gendustryFileId=YYYYYYY
# bdlib
bdlibProjectId=AAAAA
bdlibFileId=BBBBBBB
```

### Option B: Command-line overrides (useful in CI)

```bash
# Setup workspace with explicit IDs
./gradlew setupDecompWorkspace -PgendustryProjectId=XXXXX -PgendustryFileId=YYYYYYY -PbdlibProjectId=AAAAA -PbdlibFileId=BBBBBBB

# Build with the same IDs
./gradlew build -PgendustryProjectId=XXXXX -PgendustryFileId=YYYYYYY -PbdlibProjectId=AAAAA -PbdlibFileId=BBBBBBB
```

### How to find IDs
- On CurseForge, the `projectId` and `fileId` are visible in the URL of a file page (or via API). Choose the project (Gendustry / bdlib) and the specific file for 1.12.2.
- The Maven coordinates used by CurseMaven are: `curse.maven:<slug>-<projectId>:<fileId>`.

### Fallback: local deobf jars
If IDs are not provided, the build falls back to `compileOnly` jars in `libs/`:
- `libs/gendustry-deobf.jar`
- `libs/bdlib-deobf.jar`

See `libs/README.md` for options to obtain these.

## Build
ForgeGradle 2.x, Java 8. Uses Gradle Wrapper (no manual Gradle installation needed).

```bash
# First-time workspace setup (can be slow)
./gradlew setupDecompWorkspace

# Build the mod
./gradlew build

# Run the unit tests only (fast; no Minecraft bootstrap)
./gradlew test
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

- Artifact output: `build/libs/apiarist-terminal-<version>.jar`.

## Extending to more Gendustry machines
Most Gendustry machines extend bdew's `TileBaseProcessor` and implement `TileWorker`, so they all
have progress, a working flag, an energy buffer and a sided inventory. For those, subclass
`MachineDriver` and describe the machine — component name, named slots, tanks — instead of writing
a driver from scratch; see `DriverSampler` for the shortest example. Extend `ItemMachineEnvironment`
rather than `MachineEnvironment` when the tile declares `canStart()`.

Write a driver by hand only when the machine exposes state the shared component cannot reach, the
way the Advanced Mutatron exposes its mutation selection:
- Create a `DriverXxx extends DriverSidedTileEntity` with a nested
  `public static final class Environment extends AbstractManagedEnvironment implements NamedBlock`.
- Type strongly against the relevant Gendustry tile class (no reflection); return `priority() = 10`
  so the driver wins over OpenComputers' generic energy/inventory drivers.
- Register it in `DriverRegistry.registerAll()`.
- Document every `@Callback` with a `"function(arg:type):ret -- description"` doc string, and add a
  `docs/components/<component>.md` page.
- Gate event emission on `Config.enableEvents` (global) AND the per-device `eventsEnabled` flag, and
  return that conjunction from `canUpdate()`.
- Never make a callback wait. `Callback.direct()` defaults to `false`, so callbacks run on the
  server thread, and `Context.pause()` does not suspend the call — it schedules a pause for after
  it returns. A loop around it freezes the game for the whole timeout. Emit a signal and let the
  script wait with `event.pull`.
- Clamp tunables through `net.ocgendustry.util.Tuning`; add a config category if the driver has defaults.

## Running the mod for real

Unit tests never start Minecraft, so anything that touches OpenComputers, Gendustry or Forestry is
only checked by the compiler. Every behavioural defect in this mod's history was found by running
it. There is a dev client and a dev server for that.

```powershell
.\gradlew.bat setupDevMods    # once: downloads the third-party mods into run/ and run-server/
.\gradlew.bat runClient       # a client, world in run/
.\gradlew.bat runServer       # a dedicated server, world in run-server/
```

`setupDevMods` pulls OpenComputers, Binnie's Mods, and the Thermal/CoFH stack for a Creative Energy
Cell, from CurseMaven. Nothing is committed: redistributing someone else's jar is not ours to do.
It also writes `run-server/eula.txt` and a `server.properties` set up for a flat creative world with
`online-mode=false`, so a dev client can join.

Gendustry, bdlib, Forestry and RedstoneFlux are deliberately **not** in that list. The build already
puts them on the classpath, and a second copy under `run/mods` makes FML refuse to start on a
duplicate mod id.

One trap worth knowing: Gradle keeps `compileOnly` dependencies off the run classpath, but IntelliJ
maps them to Provided scope and puts them on it. A `compileOnly` mod jar therefore duplicates only
when the game is launched from the IDE, and not from `gradlew`. RedstoneFlux is declared
`deobfCompile` so both paths see exactly one copy.

### From IntelliJ

`.idea/runConfigurations/` holds two shared configurations, **Minecraft Client** and **Minecraft
Server**, so they appear in the run dropdown after a Gradle import — with the debugger attached,
which is the only comfortable way to inspect a tile entity mid-cycle.

They exist as files rather than being left to `gradlew genIntellijRuns` because that task writes
into `.idea/workspace.xml`, which is per-developer and not versioned. It also gives each one its own
working directory: `run/` for the client, `run-server/` for the server. That matters — both default
to `run/`, and a client and a server started together then fight over the same `logs/latest.log`.

### Running a client and a server together

Start the server first, then the client, and connect to `localhost` through Multiplayer → Direct
Connect. The dev client logs in as `Player###` in offline mode, which the generated
`server.properties` accepts.

A server started outside a real terminal stops immediately: it reads its console input and treats
the closed stream as the `stop` command.

## Troubleshooting
- First build slow: The initial `setupDecompWorkspace` can take 10–30 minutes (decompiling MC/Forge). Subsequent builds are faster.
- Use Java 8: ForgeGradle 2.3 requires JDK 8. Running with Java 9+ can cause NPEs in tasks like `recompileMc`.
- Stale generated sources: If you see errors in `build/sources/...` (e.g., duplicate `package` lines):
  1. Remove stale outputs: `./gradlew clean`
  2. Re-run: `./gradlew setupCiWorkspace` (faster) or `./gradlew setupDecompWorkspace`
  3. Build: `./gradlew build`
- Network hiccups: If Forge/MCP downloads time out/corrupt, clear `~/.gradle/caches/minecraft` (Linux/Mac) or `%USERPROFILE%\.gradle\caches\minecraft` (Windows) and retry.

## Notes
- Component names: `advmutatron`, `industrial_apiary`. Attach an Adapter/Cable to the machine during in-game testing.
- Logging tag: `[ApiaristTerminal]` for driver-related diagnostics (always go through `Log`).
- Config file: `config/ocgendustry.cfg`; Forge lowercases category names, so keep the constants in
  `Config` lowercase/snake_case to match what the docs promise.
- Releases: push a tag; CI builds, creates the GitHub Release from
  `.github/scripts/build_release_notes.sh` and publishes to CurseForge. Keep `version` in
  `build.gradle` and `OCGendustryMod.VERSION` in sync.
