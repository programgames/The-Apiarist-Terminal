# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [Unreleased]

### Fixed
- `advmutatron.selectAndProduce` checks the mutagen tank before reporting a start. It used to
  answer `true` with an empty tank: the machine accepted the selection, never ran, and a script
  waited out its whole timeout for a signal that was never coming. `canStart()` would have caught
  it but cannot be called there -- on this machine it requires a selection to have been made
  already, which is the very thing the check runs before.

### Added
- `industrial_apiary` gains `getRedstoneMode()` and `setRedstoneMode(mode)`, the first callback in
  the mod that changes a machine rather than reading it: it is how a script stops an apiary and
  starts it again. The four modes are the ones the machine's own GUI button cycles through.
- `advmutatron` and `industrial_apiary` gain `isWorking()` and `getEnergy()`, which the eight
  processing machines already had. A script no longer has to special-case them.
- `listSlots()` on `advmutatron` and `industrial_apiary` now reports `size`, as the eight
  processing machines already did. A script driving any machine through the generic inventory
  calls iterates over that field, and it came back `nil` from the two components most worth
  driving.
- `ocgendustry/scripts/nofreeze.lua`, which times `selectAndProduce` and says plainly whether the
  call held the server thread. It is the acceptance test for the change this fork exists for, so it
  belongs in the repository rather than in a shell history.
- `ocgendustry/scripts/apiarist.lua`, a library that restores the one-call ergonomics the blocking
  callbacks used to offer -- `machine:runCycle(timeout)`, `adv:produce(n, timeout)`,
  `apiary:waitForPrincess(timeout)` -- with the waiting done in Lua, where it is allowed.

- Components for the eight remaining Gendustry machines: `mutatron`, `genetic_sampler`,
  `genetic_imprinter`, `genetic_replicator`, `genetic_transposer`, `dna_extractor`,
  `protein_liquifier` and `mutagen_producer`.
  - Shared callbacks: `getProgress`, `isWorking`, `start`, `canStart`, `isValidInputs`,
    `getEnergy`, `listSlots`, `listTanks`, `listOutputs`, plus the event and tuning controls.
  - Signals `<component>_started`, `<component>_finished` and `<component>_output`.
  - Slot indices are read from the machine at runtime instead of being hardcoded.
- New config category `processing_machines`, listed in the in-game config GUI, holding the shared
  defaults for those eight components.
- Documentation: `docs/components/processing_machines.md`.
- Three read-only diagnostic programs shipped with the mod, under `ocgendustry/scripts/`:
  `survey.lua` reports every component on the network and, for each Gendustry one, its callbacks,
  state, slots, tanks, verdicts and inventory into a file that can be read off-screen;
  `testall.lua` walks the processing machines checking the expectations specific to each;
  `machine_test.lua` runs one machine through a full cycle and watches its signals.

### Added
- `industrial_apiary` gains `getSpeciesTemplate(species)`, returning a bee species' default genome
  as its thirteen chromosomes keyed by the name Forestry uses, and `listSpeciesTemplates([filter])`,
  which enumerates species from Forestry's allele registry instead of guessing a UID by
  concatenation - so species added by other mods are listed too. On a large pack that is the
  difference between 44 species and 408. Both accept a UID, an allele name or a display name.
- `species.lua`, which browses that registry from the game: an overview grouped by the mod that
  registered each species, a filtered listing, one genome in karyotype order, and a dump to file.

### Changed
- The two hand-written drivers read their slot indices from `tile.slots()` instead of constants,
  as the eight processing machines already did. The constants happened to be right, but a
  Gendustry reorder would have made them silently wrong.

### Changed
- The started/finished/output decisions moved to `util/SignalState`, shared by all three drivers
  and covered by `SignalStateTest`. Until now the suite only checked names and doc strings, and
  every behavioural defect found so far was found by running the mod rather than by a test.
- `_started` and `_finished` were sampled only every `signalIntervalTicks` in the two hand-written
  drivers, so a cycle shorter than that interval raised neither signal. The working flag is now
  read on every tick and only the output scan stays throttled.
- Every driver started its signal state from a value the machine had never held, so a component
  created while its machine was already running raised a phantom `_started` on its first tick, and
  one with an output slot a phantom `_output`, on every chunk load. Both are primed from the
  machine now.
- The output signature includes the metadata and an NBT hash. It was name and count only, so two
  different bees in the same slot looked identical and an output change between two scans could
  raise no signal - on machines whose whole product is defined by its NBT.
- `applyDefaultTuning()` re-reads the config file at most once a second. It is reachable from Lua
  on the server thread, so a script calling it in a loop was doing disk I/O every iteration.

### Removed
- `waitForPrincess` (`industrial_apiary`) and `setWaitInterval` (every component), along with the
  `waitStepSeconds` config option. **This breaks scripts that call them.** The first could not work
  and the second only tuned its loop. Wait on the `<component>_finished` signal instead, and read
  `getPrincessStatus()` afterwards.

### Changed
- `selectAndProduce(n, timeout)` becomes `selectAndProduce(n)` and returns as soon as the cycle is
  started; `selectAndProduceAsync`, which already did exactly that, is kept as an alias. The two
  reference programs shipped in the jar were updated to match.
- `DriverAdvMutatron.selectAndProduce` and `DriverApiary.waitForPrincess` looped on
  `Context.pause()` waiting for the machine. `Callback.direct()` defaults to false, so those
  callbacks run on the server thread, and pause does not suspend a call: it schedules one for after
  the call returns. Each held the tick loop for its entire timeout -- the game log recorded two
  ticks as `Running 60045ms behind` and `Running 60051ms behind`.
- `waitForPrincess` did more than wait: inside its loop it rejected the Automation upgrade and
  surfaced Forestry error states. That diagnosis lives on in `getPrincessStatus()`.

### Fixed
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
