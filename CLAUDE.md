# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

**The Apiarist Terminal** (`ocgendustry`) — Minecraft **Forge 1.12.2** mod that exposes
**Gendustry** machines to **OpenComputers** as scriptable components.

- Java package: `net.ocgendustry` · artifact: `apiarist-terminal-<version>.jar`
- Components shipped: `advmutatron` (Advanced Mutatron), `industrial_apiary` (Industrial Apiary)
- Runtime deps (not bundled): OpenComputers, Gendustry, bdlib, Forestry
- Log tag: `[ApiaristTerminal]` (always go through `Log.info/warn/error`)

## Build & test

Toolchain is **locked**: JDK **8**, ForgeGradle **2.3**, Gradle wrapper **4.10.3**, daemon off.
Java 9+ breaks ForgeGradle tasks (NPE in `recompileMc`). Windows shell → `.\gradlew.bat`.

```powershell
.\gradlew.bat setupDecompWorkspace   # first time only, 10-30 min (decompiles MC/Forge)
.\gradlew.bat build                  # -> build/libs/apiarist-terminal-<version>.jar
.\gradlew.bat test                   # JUnit 4 + AssertJ, pure unit tests, no MC bootstrap
.\gradlew.bat printDeps              # shows CurseMaven vs libs/ fallback resolution
```

CI (`.github/workflows/build.yml`) uses `setupCiWorkspace` (faster) then `test` then `build`.
Stale `build/sources/...` errors → `clean`, re-run `setupCiWorkspace`, rebuild.

### Dependencies
Gendustry/bdlib are downloaded from **CurseMaven** using the project/file IDs in
`gradle.properties` (`gendustryProjectId/FileId`, `bdlibProjectId/FileId`). If those are absent,
the build falls back to `compileOnly` jars in `libs/` (`gendustry-deobf.jar`, `bdlib-deobf.jar`).
Never hardcode a dependency path — go through this switch in `build.gradle`.

## Layout

```
src/main/java/net/ocgendustry/
  OCGendustryMod.java          @Mod entrypoint: Config.init + DriverRegistry.registerAll in preInit
  Config.java                  Forge Configuration; categories General / Adv Mutatron / Apiary / Integration Test
  Log.java                     Log4j wrapper with the [ApiaristTerminal] tag
  driver/DriverRegistry.java   single registration point (idempotent)
  driver/DriverAdvMutatron.java  hand written: exposes the GUI-only mutation selection
  driver/DriverApiary.java     hand written, read-only; writes go through generic OC inventory calls
  driver/MachineDriver.java    base driver for the 8 processing machines (tile class + MachineSpec)
  driver/MachineSpec.java      per-machine description: name, slots, tanks, canStart, input check
  driver/MachineEnvironment.java      THE component for those 8: final, holds every @Callback
  driver/Driver{Mutatron,Sampler,Imprinter,Replicator,Transposer,Extractor,Liquifier,MutagenProducer}.java
                               one per machine: component name, named slots, tanks
  util/Tuning.java             clamps for signalInterval / waitStep, shared by every driver
  util/Stacks.java             ItemStack -> Lua table, and the cheap signature used for output events
  util/MutatronLogic.java      mutation-selection helper (no MC types) so logic is unit-testable
  client/GuiFactory|GuiModConfig.java   in-game config GUI
  command/OcGendustryCommand.java       /ocgendustry test advmutatron [fresh|reuse|all]
src/main/resources/
  mcmod.info                   version/mcversion injected by processResources
  META-INF/ocgendustry_at.cfg  access transformer named by the jar manifest (FMLAT), empty placeholder
  ocgendustry/scripts/*.lua    reference OC programs
src/test/java/…               TuningTest, MutatronLogicTest, DriverAdvMutatronDocExamplesTest,
                              MachineComponentNamesTest, MachineEnvironmentDocsTest
docs/components/<name>.md      per-component callback reference
```

## Conventions

**Drivers.** Most Gendustry machines extend bdlib's `TileBaseProcessor` and implement `TileWorker`;
for those, subclass `MachineDriver` and describe the machine (component name, named slots, tanks) —
see `DriverSampler` for the shortest example. **Never subclass `MachineEnvironment`**: when several
drivers share a block (always the case here, OC's generic energy driver binds to their Forge Energy
capability) OpenComputers dispatches a callback only to the environment whose class *equals* the
callback's declaring class, so an inherited `@Callback` is listed by `component.methods()` yet fails
every call with `no such method`. Add the callback to `MachineEnvironment` and gate it on the
`MachineSpec`. Write a driver by hand only for machines exposing state the shared component
cannot reach: `public final class DriverXxx extends DriverSidedTileEntity` with a
`public static final class Environment extends AbstractManagedEnvironment implements NamedBlock`.
Type strongly against the Gendustry tile class — **no reflection**. `priority()` returns `10` so the
driver wins over OC's generic energy/inventory drivers. Register in `DriverRegistry.registerAll()`.
Read slot indices from the tile's own `slots()` accessors rather than hardcoding them.

**Callbacks.** Every `@Callback` carries a `doc` in the form
`"function(arg:type):ret -- description"`. Return `new Object[]{ value }`; signal failure as
`new Object[]{ false, "reason" }` rather than throwing. Build Lua tables with `LinkedHashMap`
(ordering is user-visible). `DriverAdvMutatronDocExamplesTest` asserts on doc strings — keep them
in sync when renaming or rewording.

**Blocking helpers.** Don't write any. `Context.pause()` does **not** suspend a callback: it just
schedules a pause for after the call returns, so looping on it busy-waits and holds the server
thread for the whole timeout. Waiting belongs in Lua, on the `_finished` signal. `DriverApiary` and
`DriverAdvMutatron` still carry that broken pattern (`waitForFinish`, `waitForPrincess`) and need
the same treatment.

**Events.** Emission is gated by `Config.enableEvents` (global) **AND** the per-device
`eventsEnabled` flag; `canUpdate()` returns that conjunction so ticking is skipped entirely when
off. `update()` is throttled by `signalInterval` ticks. Every component exposes
`setEventsEnabled/getEventsEnabled/areEventsEnabled` and `setSignalInterval/applyDefaultTuning`
(the two hand written drivers also still expose `setWaitInterval`).
Signals: `advmutatron_started|finished|output`, `apiary_started|finished|output`.

**Tunables.** Clamp through `Tuning.clampSignalInterval/clampWaitStep`; defaults come from
`Config` at environment creation and are re-read by `applyDefaultTuning()`. New pure logic belongs
in `util/` so it can be tested without a Minecraft bootstrap.

**Style.** 4 spaces, no wildcard imports, `final` classes with private constructors for utilities,
a blank line before multi-statement `return`s, slot indices as named `private static final`
constants. Comments explain the *why* (game quirks), not the *what*.

## Traps

- Calling `ItemStack.getDisplayName()` on Forestry genetic items without genome NBT floods the log.
  Guard on `stack.hasTagCompound()` first (see `listMutations`).
- Slot layouts are hardcoded in the two hand written drivers: Adv Mutatron `0/1` parents, `2` output,
  `3` labware, `4..9` selectors; Apiary `0` queen, `1` drone, `2..5` upgrades, `6..14` output. The
  eight `MachineDriver` components read theirs from `tile.slots()` instead.
- Component names must not collide with OpenComputers' own (`transposer`, `inventory_controller`);
  that is why the Genetic Transposer is `genetic_transposer`. `MachineComponentNamesTest` guards it.
- The version lives in two places: `build.gradle` `version` and `OCGendustryMod.VERSION`. They must match.
- Forge lowercases config category names, so `Config.CAT_*` are lowercase snake_case on purpose —
  they are the names the README and `docs/components/*.md` promise (`advanced_mutatron.defaultEventsEnabled`).
- The access transformer must stay at `META-INF/ocgendustry_at.cfg`: the jar manifest sets
  `FMLAT=ocgendustry_at.cfg` and FML resolves that relative to `META-INF`.
- The integration harness (`/ocgendustry test …`) is off by default and gated by
  `Config.enableIntegrationHarness`; it can place blocks — creative worlds only.

## Changing things

- New component → driver class + `DriverRegistry` + `docs/components/<name>.md` + README component
  list + `CHANGELOG.md` entry, plus a `Config` category if it has tunables.
- New config option → `Config.readValues()` (with comment + range) and document it in the README.
- Config file lives at `config/ocgendustry.cfg`; the in-game GUI path is
  Mods → The Apiarist Terminal → Config.

## Release

Keep `CHANGELOG.md` (Keep a Changelog + SemVer) current. Pushing a tag makes CI build, create the
GitHub Release from `.github/scripts/build_release_notes.sh`, and publish to CurseForge; the release
type is inferred from the tag (`alpha`/`beta`/`rc`/`pre` → pre-release).
