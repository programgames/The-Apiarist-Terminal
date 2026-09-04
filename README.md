# The Apiarist Terminal: A Gendustry addon

OpenComputers drivers that expose Gendustry machines as OC components.

## Overview
- Forge 1.12.2 mod that exposes Gendustry machines to OpenComputers
- Hard dependency on OpenComputers and Gendustry: if either is missing, Forge will fail to load with a clear error

## Requirements
- Minecraft Forge 1.12.2
- OpenComputers (for 1.12.2)
- Gendustry (for 1.12.2)

## Installation
1) Obtain the mod jar for this project (e.g., `apiarist-terminal-<version>.jar`).
2) Place the jar in your `mods/` folder alongside OpenComputers and Gendustry.
3) Start the game. If dependencies are missing or incompatible, Forge will report it during load.

Notes
- Components:
  - Advanced Mutatron: `advmutatron`
  - Industrial Apiary: `industrial_apiary`
  - Mutatron: `mutatron`
  - Genetic Sampler: `genetic_sampler`
  - Genetic Imprinter: `genetic_imprinter`
  - Genetic Replicator: `genetic_replicator`
  - Genetic Transposer: `genetic_transposer`
  - DNA Extractor: `dna_extractor`
  - Protein Liquifier: `protein_liquifier`
  - Mutagen Producer: `mutagen_producer`
- Attach an Adapter/Cable to the machine.
- Logging tag: `[ApiaristTerminal]`. Errors will mention driver names for easier troubleshooting.

## Component API docs
Full callback reference can be found in per-component docs, along with quick start and examples:

- Advanced Mutatron: `docs/components/advmutatron.md`
- Industrial Apiary: `docs/components/industrial_apiary.md`
- The eight other machines: `docs/components/processing_machines.md`

## Configuration
- In-game: Mods -> The Apiarist Terminal -> Config opens a GUI to tweak defaults.
- File: `config/ocgendustry.cfg` creates after first run. You can hand-edit values.

Currently exposed defaults:
- Advanced Mutatron: signal interval (ticks) and wait step (seconds) used by events/blocking helpers.
  - Changes apply to newly created component instances; to apply live, call `adv.applyDefaultTuning()` from an OC computer.
- Processing machines: the same two settings, shared by the eight machines of
  `docs/components/processing_machines.md`, under the `processing_machines` category.

### Event controls
- Global (server/admin): `general.enableEvents` — hard-disables all OC signals from all devices when false.
- Per-device defaults:
  - `advanced_mutatron.defaultEventsEnabled`
  - `industrial_apiary.defaultEventsEnabled`
  - `processing_machines.defaultEventsEnabled`
- Per-device runtime (from OC):
  - `setEventsEnabled(boolean)` — toggle events for that single device instance
  - `getEventsEnabled()` — device flag only
  - `areEventsEnabled()` — effective flag (global AND device)

Notes:
- Devices initialize their per-device flag from the config default on create and when calling `applyDefaultTuning()`.
- When `general.enableEvents=false`, no device will emit events regardless of its per-device flag.

## Extending support
If you want to add drivers for more Gendustry machines, see the development guide for structure and guidelines.

- Development docs: see `DEVELOPMENT.md`.

## Contributing
Contributions are welcome! For small fixes or features:
- Open an issue or PR describing the change.
- Follow the project structure in `DEVELOPMENT.md` and target Java 8 / ForgeGradle 2.x.
- Make sure the project builds locally before submitting:

```bash
# From the repo root (uses Gradle Wrapper - no Gradle installation needed)
./gradlew setupDecompWorkspace
./gradlew build
```

## License
- This module: MIT (see `LICENSE`).
- Gendustry: WTFPL.
- OpenComputers: MIT-like.

## Changelog
See `CHANGELOG.md` for release notes.