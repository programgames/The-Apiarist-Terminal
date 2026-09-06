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

| Machine | Component | Item slots | Tanks | Answers `canStart` / `isValidInputs` |
|---|---|---|---|---|
| Mutatron | `mutatron` | `inIndividual1`, `inIndividual2`, `inLabware`, `outIndividual` | `input` | yes / no |
| Genetic Sampler | `genetic_sampler` | `inIndividual`, `inSampleBlank`, `inLabware`, `outSample` | — | yes / no |
| Genetic Imprinter | `genetic_imprinter` | `inTemplate`, `inIndividual`, `inLabware`, `outIndividual` | — | yes / no |
| Genetic Replicator | `genetic_replicator` | `inTemplate`, `outIndividual` | `dna`, `protein` | yes / no |
| Genetic Transposer | `genetic_transposer` | `inTemplate`, `inBlank`, `inLabware`, `outCopy` | — | yes / yes |
| DNA Extractor | `dna_extractor` | `inIndividual`, `inLabware` | `output` | no / no |
| Protein Liquifier | `protein_liquifier` | `inMeat` | `output` | no / no |
| Mutagen Producer | `mutagen_producer` | none named | `output` | no / no |

Two naming notes:

- `genetic_transposer`, not `transposer`: OpenComputers ships its own Transposer block under that
  name and two components cannot share one.
- Slot keys are the names Gendustry uses in its own source. They keep the `in`/`out` prefix because
  some machines have both (the Imprinter takes an individual in and gives one back).

## Callbacks

Available on all eight:

- `getProgress(): number` — 0..1.
- `isWorking(): boolean` — true while processing.
- `start(): boolean` — try to start now; true only if this call is what started it. **Expect `false`
  most of the time, and do not treat it as an error**: bdlib's server tick calls the machine's own
  `tryStart()` every tick as soon as it has enough energy and is not working, so a loaded machine
  starts on its own and `start()` loses that race. It is useful mainly for a machine the tick will
  not start by itself. To drive a cycle, check `isWorking()` and wait on the `_finished` signal
  rather than relying on `start()` returning true.
- `getEnergy(): table` — `{ stored, capacity }`.
- `listSlots(): table` — the machine's named slots, plus `outputs:number[]` and `size:number`.
- `listTanks(): table` — array of `{ name, amount, capacity, fluid? }`; empty for machines with no tank.
- `listOutputs(): table` — array of `{ name, label?, nbt?, count, slot }` for non-empty output slots.
- `setSignalInterval(ticks)`, `applyDefaultTuning()`.
- `setEventsEnabled(bool)`, `getEventsEnabled()`, `areEventsEnabled()`.

Two more are present on every component, and answer `false, "not supported by this machine"` where
Gendustry offers no such check. They are exposed everywhere rather than only on the machines that
support them because OpenComputers dispatches a callback only when the environment's class is
exactly the class declaring it, so a component cannot pick and choose which callbacks it carries
(see *Why one class* below).

- `canStart(): boolean, string?` — true when every required slot is filled, the output is free and
  there is enough energy. Answers `false, "not supported by this machine"` on the DNA Extractor,
  the Protein Liquifier and the Mutagen Producer: Gendustry declares no pre-flight check for the
  machines that only fill a tank.
- `isValidInputs(): boolean, string?` — Genetic Transposer only. Checks the pair *currently
  loaded*: `false, "missing template"`, `false, "missing blank sample"`, or
  `false, "incompatible inputs"` when Gendustry itself rejects the pair. The verdict is
  delegated to the machine rather than reimplemented, so it stays right whatever Gendustry accepts. Use it before committing labware, which is consumed on every run.
  Every other machine answers `false, "not supported by this machine"`.

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
(`signalIntervalTicks`, `defaultEventsEnabled`).

## Quick start

```lua
local component = require("component")
local sampler = component.genetic_sampler

local slots = sampler.listSlots()
print("put the bee in slot", slots.inIndividual)
print("read the result from slot", slots.outSample)

if sampler.canStart() then
  sampler.start()                                   -- may return false: the tick often wins
  if sampler.isWorking() then
    if not event.pull(30, "genetic_sampler_finished") then error("timeout") end
  end

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
if transposer.isWorking() then event.pull(30, "genetic_transposer_finished") end
```

## Example: watch a fluid machine

```lua
local component = require("component")
local extractor = component.dna_extractor

extractor.start()
if extractor.isWorking() then event.pull(60, "dna_extractor_finished") end

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

## Waiting for a cycle

There is no `waitForFinish` callback, and there cannot be one. OpenComputers' `Context.pause()` does
not suspend a callback: it schedules a pause for *after* the call returns. A Java loop around it
therefore busy-waits and holds the server thread for the whole timeout. Wait in Lua instead, on the
signal:

```lua
local event = require("event")
local sampler = require("component").genetic_sampler

if sampler.isWorking() then
  event.pull(30, "genetic_sampler_finished")
end
```

`_started` and `_finished` are raised from the machine's own tick and are never throttled, so a
short cycle cannot slip between two samples. `setSignalInterval(ticks)` only governs how often the
output slots are scanned for `_output`.

## Why one class

All eight components are instances of a single `final` class, `MachineEnvironment`, configured by a
`MachineSpec` that carries the machine's name, slots, tanks and checks. That is not a style
preference, it is forced by OpenComputers.

These tiles expose a Forge Energy capability, so OpenComputers' own generic energy driver always
binds to them alongside this mod's driver. Two drivers on one block means OpenComputers wraps them
in a `CompoundBlockEnvironment`, and to route an incoming call it looks for the environment whose
class *is* the class declaring the callback:

```java
environment.getClass().equals(callback.method().getDeclaringClass())
```

An exact identity test, not `isAssignableFrom`. A callback inherited from an abstract base class is
therefore never dispatched — and the failure is quiet and misleading: `component.methods()` still
lists the callback, because that list is built by a separate scan that does walk the hierarchy, but
every call fails with `no such method`.

So: **never subclass `MachineEnvironment` to add or specialise a callback.** Add the callback to
`MachineEnvironment` itself and let the `MachineSpec` say whether the machine supports it.
`MachineEnvironmentDocsTest` fails if the class stops being final or if a callback ends up inherited.
