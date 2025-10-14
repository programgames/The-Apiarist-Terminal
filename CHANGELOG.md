# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

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
