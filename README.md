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

## Lua library and programs
The mod registers an OpenComputers floppy, **Apiarist Terminal** — yellow, in the creative
inventory beside the OpenOS disk. Put it in a drive and run `install`: you get `apiarist.lua` in
`/usr/lib` and four programs in `/usr/bin` (`survey`, `species`, and two worked automation rigs).
`docs/scripts.md` covers what each does.

`apiarist.lua` is the one meant for your own programs. A callback cannot wait for a machine without
holding the server thread, so the components expose state and signals and nothing that blocks; the
library puts the waiting back where it is allowed, in Lua:

```lua
local apiarist = require("apiarist")
local bee = apiarist.wrap("advmutatron"):produce(1, 60)   -- select, wait, return the product
```

## Configuration
- In-game: Mods -> The Apiarist Terminal -> Config opens a GUI to tweak defaults.
- File: `config/ocgendustry.cfg` creates after first run. You can hand-edit values.

Currently exposed defaults:
- Advanced Mutatron and Industrial Apiary: signal interval (ticks) and whether events start enabled.
  - Changes apply to newly created component instances; to apply live, call `adv.applyDefaultTuning()` from an OC computer.
- Processing machines: the same settings, shared by the eight machines of
  `docs/components/processing_machines.md`, under the `processing_machines` category.
- The signal interval throttles the `_output` signal only. `_started` and `_finished` are raised
  from the machine's own tick, so a short cycle cannot slip between two samples.
- `industrial_apiary.requireAnalyzedBees` (default **true**): whether `getGenome()` refuses a bee
  that has not been through a Beealyzer. Forestry keeps the whole genome in NBT either way and uses
  the analysed flag only for the tooltip, so reading it regardless is possible — and would quietly
  remove the Beealyzer's reason to exist. Left on, a script sees exactly what a player would.

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

On Windows, use `gradlew.bat` instead of `./gradlew`.

## License
- This module: MIT (see `LICENSE`).
- Gendustry: WTFPL.
- OpenComputers: MIT-like.

## Changelog
See `CHANGELOG.md` for release notes.