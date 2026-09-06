# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

### Added
- Components for the eight remaining Gendustry machines: `mutatron`, `genetic_sampler`,
  `genetic_imprinter`, `genetic_replicator`, `genetic_transposer`, `dna_extractor`,
  `protein_liquifier` and `mutagen_producer`.
  - Shared callbacks: `getProgress`, `isWorking`, `start`, `getEnergy`, `listSlots`, `listTanks`,
    `listOutputs`, plus the usual event and tuning controls. There is deliberately no
    `waitForFinish`: wait on the `<component>_finished` signal from Lua.
  - `canStart` and `isValidInputs` on every one of them; they answer
    `false, "not supported by this machine"` where Gendustry declares no such check (the three
    machines that only fill a tank, and every machine but the Genetic Transposer respectively).
  - Signals `<component>_started`, `<component>_finished` and `<component>_output`.
  - Slot indices are read from the machine at runtime instead of being hardcoded.
- `industrial_apiary` gains `getSpeciesTemplate(species)`, returning a bee species' default genome
  as its thirteen chromosomes, and `listSpeciesTemplates([filter])`, which enumerates species from
  Forestry's allele registry instead of guessing a UID by concatenation — so species added by other
  mods are listed too. Both accept a UID, an allele name or a display name.
- New config category `processing_machines` holding the shared defaults for those eight components.
- Documentation: `docs/components/processing_machines.md`.

### Fixed
- The two hand-written drivers carried the same blocking pattern, inherited from before this work:
  `DriverAdvMutatron.selectAndProduce` and `DriverApiary.waitForPrincess` looped on
  `Context.pause()`. Because `Callback.direct()` defaults to false these run on the server thread,
  and pause does not suspend a call, so each froze the game for its whole timeout — the game log
  recorded two `Running 60045ms behind` ticks. `selectAndProduce` now selects, starts and returns
  immediately (`selectAndProduceAsync` is kept as an alias), and `waitForPrincess` is replaced by
  the non-blocking `getPrincessStatus()`, which keeps the diagnosis it did in its loop: queen slot
  state, the forbidden Automation upgrade, and the first Forestry error. Wait on
  `advmutatron_finished` / `apiary_finished` instead.
- Both hand-written drivers also sampled their working flag only every `signalIntervalTicks` and so
  dropped the transitions of a short cycle, like the processing machines did.
- The now-unused `setWaitInterval` callback and the `waitStepSeconds` config option are removed
  from all three categories rather than left as knobs that govern nothing.
- The Genetic Transposer's `isValidInputs` passed its two stacks to Gendustry in the wrong order.
  `TileTransposer.isValidInputs` takes (blank, template) — that is how Gendustry's own
  `isItemValidForSlot` calls it from either slot — so every pair was reported as
  `incompatible inputs`, including pairs the machine was already processing.
- `_started` and `_finished` were sampled only every `signalIntervalTicks`, so a cycle shorter than
  that interval raised neither signal. A Genetic Sampler at the default interval lost every
  transition. The working flag is now read on every tick and only the output scan stays throttled.
- `waitForFinish` was removed: `Context.pause()` does not suspend a callback, it schedules a pause
  for after it returns, so the loop busy-waited and held the server thread for the whole timeout
  while reporting a bogus early timeout to the script.
- The eight processing-machine components exposed their callbacks but every call failed with
  `no such method`. OpenComputers routes a call to the environment whose class *equals* the
  callback's declaring class as soon as several drivers share a block, which is always the case
  here since its generic energy driver binds to these machines' Forge Energy capability. The
  callbacks were declared on an abstract base class, so none of them were ever dispatched, while
  `component.methods()` still listed them. The component is now a single `final`
  `MachineEnvironment` configured by a `MachineSpec`, and a test guards the invariant.
- `OCGendustryMod.VERSION` was still `0.1.0` while the build produced `0.2.0`; both now report the same version.
- OpenComputers was declared as a soft dependency (`after:opencomputers`) in `@Mod`, contradicting `mcmod.info`
  and the README; it is now `required-after:opencomputers`.
- The access transformer lived at the resources root while the jar manifest declares `FMLAT=ocgendustry_at.cfg`,
  which FML resolves relative to `META-INF`. It moved to `src/main/resources/META-INF/ocgendustry_at.cfg`.
- `processResources` excluded a file name that no longer exists (`ocadvmutatron_at.cfg`); the stale rule is gone.
- Test scripts written by the integration harness are now encoded as UTF-8 explicitly instead of using the
  platform default charset, and the seeded test chest is marked dirty so its contents persist.
- `mcmod.info` advertised only the Advanced Mutatron and had no project URL.

### Changed
- Config category names are now `general`, `advanced_mutatron`, `industrial_apiary` and `integration_test`,
  matching the names used throughout the documentation. **Existing `config/ocgendustry.cfg` files keep their old
  sections (`advanced mutatron`, `industrial apiary`, `integration test`) as dead entries and the new ones are
  recreated with default values** — re-apply any customised setting after upgrading.
- Interval/wait clamping moved to `net.ocgendustry.util.Tuning` and is now shared by both drivers; the Industrial
  Apiary previously duplicated the bounds inline. `MutatronLogic` keeps only the mutation-selection helper.

### Removed
- Dead code: an unused `writeResource` overload, an unused local in the test harness, an unused import and a
  misplaced `@SuppressWarnings`.

## [0.2.0] - 2025-10-14
This release introduces a second component (Industrial Apiary), per-device and global event controls, a config GUI, an in-game test harness, docs, tests, and CI improvements.

### Added
- New component: Industrial Apiary (`industrial_apiary`)
  - Driver: `DriverApiary` (read-only driver for Gendustry Industrial Apiary)
  - Signals: `apiary_started`, `apiary_finished`, `apiary_output`
  - Callbacks include: `listSlots`, `getBees`, `listUpgrades`, `listOutputs`, `getEnvironment`, `getModifiers`, `getProgress`, `getErrors`, `setSignalInterval`, `setWaitInterval`, `applyDefaultTuning`, `waitForPrincess`
  - Registered in `DriverRegistry`

- Advanced Mutatron (`advmutatron`) driver expansion
  - Added selection utilities, async/sync production helpers, tank info, output reporting with stack details, and slot metadata
  - Signals: `advmutatron_started`, `advmutatron_finished`, `advmutatron_output` (output event includes stack info)

- Event controls
  - Global config toggle: `general.enableEvents`
  - Per-device defaults in config: `advanced_mutatron.defaultEventsEnabled`, `industrial_apiary.defaultEventsEnabled`
  - Per-device runtime callbacks on both components: `setEventsEnabled(boolean)`, `getEventsEnabled()`, `areEventsEnabled()`

- Configuration system and GUI
  - `Config.java` with categories for general, advanced_mutatron, industrial_apiary, and integration test harness
  - Mod Config GUI: `client.GuiFactory`, `client.GuiModConfig`
  - `OCGendustryMod` now loads config in `preInit` and registers the GUI factory

- In-game integration test harness (creative/gated)
  - Root command: `/ocgendustry test advmutatron [fresh|reuse|all]`
  - Optional auto placement of a small test rig (transposer+chest+adapter, and optionally an Industrial Apiary)
  - Outputs ready-to-run OC scripts in `ocgendustry-test/`: resources `ocgendustry/scripts/advmutatron_fresh.lua`, `ocgendustry/scripts/advmutatron_reuse.lua`
  - Implemented in `command.OcGendustryCommand`

- Utility and tests
  - `util.MutatronLogic` for selection index/key resolution and tuning clamps
  - Unit tests: `DriverAdvMutatronDocExamplesTest`, `MutatronLogicTest`

- Documentation
  - New component docs: `docs/components/advmutatron.md`, `docs/components/industrial_apiary.md`
  - README restructuring and event controls documentation

### Changed
- `DriverRegistry` now registers both `DriverAdvMutatron` and `DriverApiary`; improved log message
- `OCGendustryMod` updated to initialize config and register server command on `serverStarting`
- `DriverAdvMutatron` refactored/expanded with cooperative waits, throttled signaling, and richer callbacks

### CI
- Workflow `.github/workflows/build.yml` now:
  - Runs unit tests before build
  - Uses `gh release create` to publish GitHub Releases with notes built from annotated tag message and `CHANGELOG.md`
  - Sends a richer changelog to CurseForge (tag message + `CHANGELOG.md` fallback)

### Notes
- Per-device events are initialized from config defaults on environment creation and when calling `applyDefaultTuning()`
- Effective event emission is `general.enableEvents AND device.eventsEnabled`

## [0.1.0] - 2025-10-13
### Added
- Initial release with OpenComputers drivers for:
  - Advanced Mutatron (`advmutatron`): selection APIs.
