# Advanced Mutatron (component: `advmutatron`)

Quality-of-life callbacks for Gendustry's Advanced Mutatron with safe defaults and generic item transfer.

## Slots
- in1 = 0
- in2 = 1
- output = 2
- labware = 3
- selectors = 4..9

Use OpenComputers' inventory pushItems/pullItems with these slot indices.

## Callbacks
- listSlots(): table
  - Returns { in1:number, in2:number, labware:number, output:number, selectors:number[] }.
- listMutations(): table
  - Array-like (1..N) of { index, key, name, label?, nbt? }.
- setMutation(n:number): boolean, string?
  - Select by 1-based index from listMutations() or by raw selector key (4..9). Selection may auto-start; returns false,reason on invalid selection.
- start(): boolean
  - Attempts to start with current selection (not usually needed if selection auto-starts).
- getProgress(): number
  - 0..1 progress reported by the machine.
- canStart(): boolean
  - True if current conditions allow starting (pre-selection state may report false in auto-start builds).
- getTank(): table
  - { amount:number, capacity:number, fluid?:string } for mutagen tank.
- getOutput(): table|nil
  - Current output in slot 2: { name, label?, nbt?, count } or nil if empty.
- selectAndProduce(n:number[, timeout:number=60]): boolean, table|string?
  - Validates inputs (parents, labware, empty output), selects the mutation, waits (ctx.pause) until finished, returns produced stack; returns false,reason on failure.
- selectAndProduceAsync(n:number): boolean, string?
  - Same validation, selects, returns immediately; subscribe to the events below for feedback.
- setSignalInterval(ticks:number): boolean
  - Frequency of checks for event emission (in ticks between checks). Default 2 ticks.
- setWaitInterval(seconds:number): boolean
  - Wait time between checks for blocking flows. Default 0.2s.
- applyDefaultTuning(): boolean
  - Reload and apply defaults from the mod config (Mod Options GUI or ocgendustry.cfg).

## Events
- advmutatron_started — fired when processing begins
- advmutatron_finished — fired when processing ends
- advmutatron_output — fired when output slot changes; second argument is the output stack info

### Event controls
- Global toggle (admin/server): `general.enableEvents` in `config/ocgendustry.cfg`.
- Per-device default (applied on creation / applyDefaultTuning): `advanced_mutatron.defaultEventsEnabled`.
- Per-device runtime control (from OC):
  - `setEventsEnabled(boolean)`
  - `getEventsEnabled()` — device flag only
  - `areEventsEnabled()` — effective flag (global AND device)

Example:

```lua
local adv = component.advmutatron
assert(adv.setEventsEnabled(false))  -- silence events from this device only
print("device flag:", adv.getEventsEnabled())
print("effective:", adv.areEventsEnabled())
```

## Notes
- Labels and NBT are only included when present to avoid Forestry genome spam.
- Writes are performed via generic inventory moves; no direct inserts/removals are exposed by the driver.
- Defaults for signal frequency and wait step are configurable in-game (Mods menu) or via `config/ocgendustry.cfg`.

## Quick start
1) Attach an Adapter to the Advanced Mutatron.
2) From an OC computer, find the component and use the API:

```lua
local component = require("component")
local adv = component.advmutatron

-- List available mutations
local muts = adv.listMutations()
for i, m in pairs(muts) do
  print(i, m.label or m.name)
end
```

## Example automation
Event-driven selection and wait for output:

```lua
local component = require("component")
local event = require("event")
local adv = component.advmutatron

-- Start asynchronously (validates items and output slot)
local ok, err = adv.selectAndProduceAsync(1)
if not ok then error(err) end

-- Wait up to 60s for output event
local ev, stack = event.pull(60, "advmutatron_output")
assert(ev, "timed out waiting for output")
print("Produced:", stack.name, stack.count)
```

Same as above but with cooperative wait:

```lua
local component = require("component")
local adv = component.advmutatron

local ok, res = adv.selectAndProduce(1, 30) -- 30s timeout
if not ok then error(res) end

print("Produced:", res.name, res.count)
```

## Integration tests (creative, gated)
- Enable: Mods -> The Apiarist Terminal -> Config -> integration_test -> enable = true
- Optional auto placement: set `allowAutoPlacement = true` (creative required). This places a Transposer + Chest + Adapter around a found Mutatron and seeds the chest with parents and labware.
- Command:
  - `/ocgendustry test advmutatron fresh` — writes `ocgendustry-test/advmutatron_fresh.lua`
  - `/ocgendustry test advmutatron reuse` — writes `ocgendustry-test/advmutatron_reuse.lua` (chained: cultivated -> majestic -> imperial)
  - `/ocgendustry test advmutatron all` — writes both
- Run: copy the script(s) to an OC disk and execute on a computer connected to the Adapter. If auto-placement was used, scripts are patched to use `sides.front` (Mutatron) and `sides.back` (Chest).