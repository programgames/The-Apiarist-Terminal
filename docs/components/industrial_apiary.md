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
  - Returns { queen:number, drone:number, bees:number[], upgrades:number[], outputs:number[] }.
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
- setWaitInterval(seconds:number): boolean
  - Cooperative wait step (default ~0.2s).
- applyDefaultTuning(): boolean
  - Reload and apply defaults from the mod config.
- waitForPrincess([timeout:number=180]): boolean, string?
  - Waits until the queen dies and the queen slot becomes empty. If an Automation upgrade is present, the method will automatically fail as it is intended for killing queen only. Returns false,reason on timeout or if no princess/queen present.

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

-- Wait for the queen to die (reuse helper):
-- local ok, res = apiary.waitForPrincess(120)
-- if not ok then print(res) end
```
