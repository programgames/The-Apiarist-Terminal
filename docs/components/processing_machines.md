# Processing machines

Eight Gendustry machines share one component API. They all work the same way internally — a
progress bar, an energy buffer, an inventory and sometimes a tank — so they all expose the same
callbacks, plus a couple of extras where the machine has something more to offer.

The Advanced Mutatron (`advmutatron`) and the Industrial Apiary (`industrial_apiary`) are *not* part
of this family: they expose state no generic driver can reach and keep their own pages.

## What this adds over plain OpenComputers

Some of this was already reachable without any driver, because Gendustry implements standard
interfaces that OpenComputers' own generic drivers bind to: the inventories are `ISidedInventory`
(so a Transposer or Inventory Controller can already read and move stacks), the energy buffer is
exposed as a Forge Energy capability, and the tanks are exposed as `CAP_FLUID_HANDLER` (readable
with a Tank Controller). `getEnergy()` and `listTanks()` are conveniences that put those on the same
component, not new information.

What no generic driver could reach is the machine's own state. `getProgress()`, `isWorking()`,
`canStart()` and `tryStart()` live on Gendustry's `TileWorker` / `TileBaseProcessor`, which nothing
outside Gendustry knows about, and the vanilla fallback for such values, `IInventory.getField()`, is
stubbed out in bdlib and always returns `0`. Being able to tell whether a run is in progress, how
far along it is, whether the inputs are valid, and to start one on demand, is what these components
add — together with the signals, and with slot indices read from the machine instead of hardcoded.

This is also why the drivers return `priority() = 10`: on the same block they compete with
OpenComputers' generic inventory and energy drivers, and the higher priority wins.

## Components

| Machine | Component | Item slots | Tanks | Extra callbacks |
|---|---|---|---|---|
| Mutatron | `mutatron` | `inIndividual1`, `inIndividual2`, `inLabware`, `outIndividual` | `input` | `canStart` |
| Genetic Sampler | `genetic_sampler` | `inIndividual`, `inSampleBlank`, `inLabware`, `outSample` | — | `canStart` |
| Genetic Imprinter | `genetic_imprinter` | `inTemplate`, `inIndividual`, `inLabware`, `outIndividual` | — | `canStart` |
| Genetic Replicator | `genetic_replicator` | `inTemplate`, `outIndividual` | `dna`, `protein` | `canStart` |
| Genetic Transposer | `genetic_transposer` | `inTemplate`, `inBlank`, `inLabware`, `outCopy` | — | `canStart`, `isValidInputs` |
| DNA Extractor | `dna_extractor` | `inIndividual`, `inLabware` | `output` | — |
| Protein Liquifier | `protein_liquifier` | `inMeat` | `output` | — |
| Mutagen Producer | `mutagen_producer` | none named | `output` | — |

Two naming notes:

- `genetic_transposer`, not `transposer`: OpenComputers ships its own Transposer block under that
  name and two components cannot share one.
- Slot keys are the names Gendustry uses in its own source. They keep the `in`/`out` prefix because
  some machines have both (the Imprinter takes an individual in and gives one back).

## Callbacks

Available on all eight:

- `getProgress(): number` — 0..1.
- `isWorking(): boolean` — true while processing.
- `start(): boolean` — try to start now; true only if this call is what started it.
- `getEnergy(): table` — `{ stored, capacity }`.
- `listSlots(): table` — the machine's named slots, plus `outputs:number[]` and `size:number`.
- `listTanks(): table` — array of `{ name, amount, capacity, fluid? }`; empty for machines with no tank.
- `listOutputs(): table` — array of `{ name, label?, nbt?, count, slot }` for non-empty output slots.
- `waitForFinish([timeout:number=60]): boolean, string?` — waits without freezing the computer until
  the machine stops working; returns `false, "timeout"` if it is still busy when the timeout elapses. It returns `true` immediately when the machine is not
  working, so only call it after a `start()` that returned `true`: `tryStart()` marks the machine as
  working synchronously, so there is no race in that case.
- `setSignalInterval(ticks)`, `setWaitInterval(seconds)`, `applyDefaultTuning()`.
- `setEventsEnabled(bool)`, `getEventsEnabled()`, `areEventsEnabled()`.

On the five machines that turn items into items:

- `canStart(): boolean` — true when every required slot is filled, the output is free and there is
  enough energy. The three fluid-producing machines do not have it: Gendustry does not declare a
  pre-flight check for them, so the callback is absent rather than always answering `nil`.

On the Genetic Transposer only:

- `isValidInputs(): boolean, string?` — checks the pair *currently loaded*: `false, "missing template"`,
  `false, "missing blank sample"`, or `false, "incompatible inputs"` when the template and the sample
  do not belong to the same species root (bees, trees, butterflies). Use it before committing
  labware, which is consumed on every run.

**Slot indices are read from the machine at runtime**, so `listSlots()` stays correct even if
Gendustry reorders its slots. Never hardcode them in a script.

## Writes

The drivers are read-only. Items go in and out through OpenComputers' generic item transfer
(Transposer or Inventory Controller) using the indices from `listSlots()`.

## Signals

- `<component>_started` — processing began
- `<component>_finished` — processing ended
- `<component>_output` — an output slot changed

For example `genetic_sampler_started`, `mutatron_finished`.

The three machines whose product is a fluid (`dna_extractor`, `protein_liquifier`,
`mutagen_producer`) never raise `_output`: they have no output slot, and tank levels change almost
every tick, which would turn the signal into noise. Poll `listTanks()` instead.

### Event controls

- Global toggle (admin/server): `general.enableEvents` in `config/ocgendustry.cfg`.
- Per-device default: `processing_machines.defaultEventsEnabled`.
- Per-device runtime: `setEventsEnabled(boolean)` / `getEventsEnabled()` / `areEventsEnabled()`.

Tuning defaults for all eight live in the `processing_machines` category
(`signalIntervalTicks`, `waitStepSeconds`).

## Quick start

```lua
local component = require("component")
local sampler = component.genetic_sampler

local slots = sampler.listSlots()
print("put the bee in slot", slots.inIndividual)
print("read the result from slot", slots.outSample)

if sampler.canStart() then
  sampler.start()
  local ok, err = sampler.waitForFinish(30)
  if not ok then error(err) end

  for _, item in ipairs(sampler.listOutputs()) do
    print("produced", item.label or item.name, "x" .. item.count)
  end
end
```

## Example: check before spending labware

Labware is consumed on every run, so on the Genetic Transposer it is worth asking first.

```lua
local component = require("component")
local transposer = component.genetic_transposer

local ok, reason = transposer.isValidInputs()
if not ok then
  print("not starting:", reason)   -- e.g. "incompatible inputs"
  return
end

transposer.start()
transposer.waitForFinish(30)
```

## Example: watch a fluid machine

```lua
local component = require("component")
local extractor = component.dna_extractor

extractor.start()
extractor.waitForFinish(60)

for _, tank in ipairs(extractor.listTanks()) do
  print(tank.name, tank.fluid, tank.amount .. "/" .. tank.capacity)
end
```

## Example: react to signals

```lua
local event = require("event")

-- One computer can watch every machine at once; the signal name carries the component.
while true do
  local name = event.pull("mutatron_finished")
  print("mutation done, collecting")
end
```
