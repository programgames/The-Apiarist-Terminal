# Industrial Apiary (component: `industrial_apiary`)

Read-only monitoring and slot metadata for Gendustry's Industrial Apiary, designed to use OC's generic item transfer for writes.

## Slots
- queen = 0
- drone = 1
- upgrades = 2..5
- outputs = 6..14

Use OpenComputers' inventory pushItems/pullItems with these slot indices.

## Callbacks
- listSlots(): table
  - Returns { queen:number, drone:number, bees:number[], upgrades:number[], outputs:number[], size:number }.
- getBees(): table
  - { queen:table?, drone:table? } shallow item info.
- listUpgrades(): table[]
  - Array of installed upgrades: { name, label?, nbt?, count, slot }.
- listOutputs(): table[]
  - Array of non-empty outputs: { name, label?, nbt?, count, slot }.
- getEnvironment(): table
  - { temperature:string, humidity:string }.
- getModifiers(): table
  - Effective modifiers: { production, lifespan, territory, mutation, flowering, geneticDecay, isSealed, isSelfLighted, isSunlightSimulated, isAutomated, isCollectingPollen, energy, temperature, humidity }.
- getProgress(): number
  - 0..1 progress from Forestry logic.
- getErrors(): table
  - { hasErrors:boolean, errors:string[] }.
- setSignalInterval(ticks:number): boolean
  - Emit signals every N ticks (min 1). Defaults from config.
- applyDefaultTuning(): boolean
  - Reload and apply defaults from the mod config.
- getSpeciesTemplate(species:string): table | false, string
  - The default genome of a bee species: one entry per chromosome, keyed by the name Forestry uses — lower_snake_case, not the enum constant: `species`, `speed`, `lifespan`, `fertility`, `temperature_tolerance`, `never_sleeps`, `humidity_tolerance`, `tolerates_rain`, `cave_dwelling`, `flower_provider`, `flowering`, `territory`, `effect`. Each is `{ uid, name, dominant }`.
  - `species` may be an allele UID (`forestry.speciesForest`), an allele name, or a display name; it is resolved through Forestry's registry, so species from Magic Bees, Extra Bees and the rest are found too.
  - The chromosome list comes from the species root's karyotype, not from a fixed order, so it stays correct if Forestry reorders them.
- listSpeciesTemplates([filter:string]): table
  - Every registered bee species as `{ uid, name, dominant, hasTemplate }`, read from Forestry's allele registry. **Not** built by concatenating a prefix and a name: species come from many mods with different prefixes, and guessing a UID only ever finds the vanilla Forestry ones.
  - `filter` keeps the species whose uid or name contains it, case-insensitively. A large pack registers hundreds of species, so filter when you can.
- isWorking(): boolean
  - True while a bee cycle is in progress. Same callback as the eight processing machines, so a script can treat every component the same way.
- getEnergy(): table
  - `{ stored, capacity }`. Also the same as the processing machines; OpenComputers' generic energy driver still offers `getEnergyStored` alongside it.
- getRedstoneMode(): table
  - `{ mode, canWork }`. `mode` is `ALWAYS`, `NEVER`, `RS_ON` or `RS_OFF` — the four the GUI button cycles through. `canWork` says whether the machine is allowed to run right now under that mode.
- setRedstoneMode(mode:string): boolean, string?
  - **The one callback in this mod that changes a machine rather than reading it.** It is how a script stops an apiary and starts it again — to pause breeding at night, or to hold a line while it collects. Returns `false` plus the accepted values on an unknown mode rather than throwing.
- getPrincessStatus(): table
  - Non-blocking view of the queen slot: `{ occupied, type, freed, automated, error? }`. `type` is `queen`, `princess`, `other` or `none`; `freed` is true once the slot is empty, which is what a breeding cycle ends with; `automated` reports the Automation upgrade, which empties the slot by itself and would make `freed` mean something else; `error` carries the first Forestry error state when there is one.
  - This replaces `waitForPrincess([timeout])`, which blocked until the queen died. It could not work: a callback runs on the server thread and `Context.pause()` does not suspend it, so the loop froze the whole game for its timeout. Wait on the `apiary_finished` signal instead, then read this.

## Signals
- apiary_started
- apiary_finished
- apiary_output (emitted when the signature of output slots changes)

### Event controls
- Global toggle (admin/server): `general.enableEvents` in `config/ocgendustry.cfg`.
- Per-device default (applied on creation / applyDefaultTuning): `industrial_apiary.defaultEventsEnabled`.
- Per-device runtime control (from OC):
  - `setEventsEnabled(boolean)`
  - `getEventsEnabled()` — device flag only
  - `areEventsEnabled()` — effective flag (global AND device)

Example:

```lua
local apiary = component.industrial_apiary
apiary.setEventsEnabled(false) -- disable events on this apiary
print("effective:", apiary.areEventsEnabled())
```

## Notes
- Labels and NBT are included only when present, avoiding log spam.
- Writes are done via generic inventory moves; the driver is read-only for state.

## Quick start
1) Attach an Adapter to the Industrial Apiary.
2) From an OC computer, list slots and inspect state:

```lua
local component = require("component")
local apiary = component.industrial_apiary

local slots = apiary.listSlots()
print("queen slot:", slots.queen, "drone slot:", slots.drone)

local env = apiary.getEnvironment()
print("Temperature:", env.temperature, "Humidity:", env.humidity)
```

## Example automation
Polling outputs and pulling items into a chest (or react to signals for event-driven flows):

```lua
local component = require("component")
local sides = require("sides")
local apiary = component.industrial_apiary
local inv = component.inventory_controller -- robot or adapter with upgrade

-- Assume the apiary is on the front side
local apiarySide = sides.front
while true do
  -- Optional: event-driven
  local ev = {computer.pullSignal(0.5)}
  if ev[1] == 'apiary_output' or ev[1] == 'apiary_finished' then
    -- fall through to harvest
  end

  local outputs = apiary.listOutputs()
  for _, item in ipairs(outputs) do
    -- Pull each stack into slot 1 of the local inventory
    inv.pullItems(apiarySide, item.slot, item.count, 1)
  end
  os.sleep(2)
end

-- Wait for the queen to die, without blocking the server:
-- local event = require("event")
-- repeat
--   if not event.pull(120, "apiary_finished") then print("timeout") break end
--   local st = apiary.getPrincessStatus()
--   if st.error then print("error:", st.error) break end
--   if st.automated then print("remove the Automation upgrade") break end
-- until st.freed
```

## Working with genes

### The thirteen chromosomes

A bee's genome is thirteen chromosomes, and `getSpeciesTemplate` returns one entry per chromosome,
keyed by the name Forestry itself uses:

| Chromosome | What it decides |
|---|---|
| `species` | the species itself, which sets the comb and the default of everything else |
| `speed` | how fast a cycle runs |
| `lifespan` | how long a queen lasts |
| `fertility` | how many drones a queen leaves behind |
| `temperature_tolerance`, `humidity_tolerance` | how far from its preferred climate it still works |
| `never_sleeps` | whether it works at night |
| `tolerates_rain` | whether it works in rain |
| `cave_dwelling` | whether it works without sky access |
| `flower_provider` | which flowers it needs |
| `flowering` | how fast it pollinates |
| `territory` | how far it reaches |
| `effect` | the special effect it applies |

Each is `{ uid, name, dominant }`. The list comes from the species root's karyotype rather than a
fixed order, so it stays right if Forestry reorders them.

### What `dominant` means

A real bee carries **two** alleles per chromosome, one from each parent. `dominant` says which wins
when they differ: a dominant allele is expressed even when paired with a recessive one, and a
recessive trait shows only when both sides carry it.

That is what makes it worth reading before a cross. A recessive trait you want has to come from
both parents, or it is carried silently and never shows.

### A species default is not your bee's genome

`getSpeciesTemplate` answers what a species is *by default* -- what a fresh one out of a Genetic
Template holds. The bee in your apiary has been bred, and carries whatever its parents gave it.

This component cannot read that individual genome. `getBees()` returns only what the slot holds --
`{ name, label, nbt, count }` -- because reading a full genome off a stack means walking Forestry's
NBT, which is a different job from driving a machine. Use the Genetic Sampler for that.

So `getSpeciesTemplate` is for **deciding what to breed towards**: it tells you what a target
species is worth before you spend labware getting there.

### Comparing what you have with what you want

The practical use. Say you keep Prussian bees, from Extra Bees, and you want Forest's High
fertility. What would a cross actually change, and which part of it will be slow?

```lua
local apiary = require("component").industrial_apiary

local function compare(have, want)
  local a = apiary.getSpeciesTemplate(have)
  local b = apiary.getSpeciesTemplate(want)
  if not a then return print(have .. ": unknown species") end
  if not b then return print(want .. ": unknown species") end

  -- pairs() gives no order, and an unordered list of thirteen chromosomes is hard to read.
  local changed = {}
  for chromosome, allele in pairs(a) do
    local target = b[chromosome]
    if target and target.uid ~= allele.uid then
      changed[#changed + 1] = { chromosome, allele.name, target.name, target.dominant }
    end
  end
  table.sort(changed, function(x, y) return x[1] < y[1] end)

  print(string.format("%d of 13 chromosomes differ", #changed))
  for _, c in ipairs(changed) do
    print(string.format("  %-22s %-10s -> %-10s %s", c[1], c[2], c[3],
      c[4] and "dominant" or "RECESSIVE, needs both parents"))
  end
end

compare("extrabees.species.blue", "forestry.speciesForest")
```

which prints:

```
3 of 13 chromosomes differ
  fertility              Normal     -> High       RECESSIVE, needs both parents
  flowering              Slowest    -> Slower     RECESSIVE, needs both parents
  species                Prussian   -> Forest     dominant
```

Read it as a shopping list. Ten of the thirteen chromosomes already match, so the cross is smaller
than it looks, and the `species` row is dominant: one good parent carries it over.

The `fertility` row answers the question you actually asked. High fertility is **recessive** on
Forest, so a Prussian crossed once with a Forest will carry it and still lay like a Prussian. It
shows only once both parents have it, which is several generations. Knowing that before you spend
the labware, rather than after, is the whole reason to read the templates.


### Listing and reading, plainly

```lua
local apiary = require("component").industrial_apiary

for _, sp in ipairs(apiary.listSpeciesTemplates("forest")) do
  print(sp.uid, sp.name, sp.hasTemplate and "has template" or "no template")
end

local genome = apiary.getSpeciesTemplate("forestry.speciesForest")
for chromosome, allele in pairs(genome) do
  print(chromosome, allele.name, allele.dominant and "dominant" or "recessive")
end
```
