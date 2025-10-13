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
- Advanced Mutatron's name: `advmutatron`. Attach an Adapter/Cable to the Advanced Mutatron.
- Logging tag: `[ApiaristTerminal]`. Errors will mention driver names for easier troubleshooting.

## Provided callbacks (advmutatron)
These are the callbacks exposed to OpenComputers for the Advanced Mutatron component.

- `listMutations(): table` — Returns a table (1..N) of mutation info tables `{ index, key, name, label?, nbt? }`.
- `setMutation(n: number): boolean, string?` — Select mutation by 1-based index (from `listMutations`) or by raw key. **Automatically attempts to start the mutation** if conditions are met (items present, power available). Returns `false, err` on invalid index/key.
- `start(): boolean` — Try to start processing with the currently selected mutation. Useful for retrying after adding items/power, or for batch processing the same mutation repeatedly.

**Note on usage:**
- `setMutation()` will automatically try to start the mutation, so calling `start()` immediately after is usually redundant.
- `start()` always returns `false` if the machine is already running or couldn't start.
- To check if processing succeeded, wait a few seconds and check the output slot, rather than relying on `start()`'s return value.

## Quick start (OpenComputers)
- Connect an Adapter to an Advanced Mutatron.
- From an OC computer:
  - Use `component.list("advmutatron")` to find the address.
  - Invoke methods like `component.proxy(addr).listMutations()`.

**Example automation script:**
```lua
local component = require("component")
local adv = component.advmutatron

-- List available mutations
local mutations = adv.listMutations()
for i, mut in pairs(mutations) do
  -- label may be nil if the stack lacks NBT to avoid Forestry genome spam
  print(i, mut.label or mut.name)
end

-- Select and start mutation #1 (auto-starts if items/power available)
local success, err = adv.setMutation(1)
if not success then
  print("Failed to set mutation: " .. (err or "unknown"))
end

-- Wait for processing (check output slot to confirm completion)
-- To repeat the same mutation without reselecting:
-- adv.start()  -- will attempt to start with current mutation
```

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