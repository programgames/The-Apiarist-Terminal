--[[
  machine_test.lua -- acceptance test for the Gendustry processing-machine components.

  Usage: machine_test <component> [cycleTimeout]
     e.g. machine_test genetic_sampler
          machine_test dna_extractor 60

  Load the machine by hand first (inputs + labware where the recipe needs them), then run this.
  Every call is wrapped: one failing callback reports itself and the run carries on, so a single
  screenshot shows the whole picture.
]]

local component = require("component")
local event = require("event")
local computer = require("computer")
local shell = require("shell")

local args = shell.parse(...)
local name = args[1]
local cycleTimeout = tonumber(args[2]) or 60

local pass, fail = 0, 0

local function ok(label, detail)
  pass = pass + 1
  print(string.format("[ OK ] %s%s", label, detail and (" -- " .. detail) or ""))
end

local function ko(label, detail)
  fail = fail + 1
  print(string.format("[FAIL] %s%s", label, detail and (" -- " .. detail) or ""))
end

local function info(label, detail)
  print(string.format("[    ] %s%s", label, detail and (" -- " .. detail) or ""))
end

-- The address of the live component, filled in below; every call goes through it.
local addr

-- Calls a callback by name via component.invoke rather than through a component proxy.
-- Proxies are cached per address in the computer's Lua state (proxyCache in machine.lua) and
-- that state survives a world reload, so a proxy built while the component was still a ghost
-- keeps answering "no such method" until the computer is rebooted. Invoking by name sidesteps
-- the cache. Returns nil on failure, after printing the reason; an empty message means a
-- Java-side exception with no text.
local function try(label, method, ...)
  local results = table.pack(pcall(component.invoke, addr, method, ...))
  if results[1] then return table.unpack(results, 2, results.n) end

  local err = results[2]
  if err == nil or err == "" or err == ":" then
    err = "java exception with no message (likely a NullPointerException)"
  end
  ko(label, tostring(err))

  return nil
end

if not name then
  print("usage: machine_test <component> [cycleTimeout]")
  print("visible components:")
  for addr, kind in component.list() do
    print("  " .. kind .. "  " .. addr:sub(1, 8))
  end
  return
end

-- 1. The component exists and answers -------------------------------------
if not component.isAvailable(name) then
  ko("component " .. name, "not found -- is the Adapter touching the machine?")
  print("visible components:")
  for addr, kind in component.list() do print("  " .. kind) end
  return
end

-- Resolve through component.list() rather than getPrimary(): after an Adapter is broken and
-- replaced, the old address lingers in the machine's component list as a ghost that still
-- reports its type but answers "no such method" to everything, and getPrimary can pick it.
-- A component whose methods() comes back empty is such a ghost; skip it and take a live one.
local methodNames = {}
for candidate in component.list(name) do
  local okMethods, methods = pcall(component.methods, candidate)
  local count = 0
  if okMethods and methods then
    for _ in pairs(methods) do count = count + 1 end
  end

  if count > 0 then
    addr = candidate
    for methodName in pairs(methods) do methodNames[methodName] = true end
    break
  end

  info("ghost component", candidate:sub(1, 8) .. " reports no method -- skipped (reboot clears it)")
end

if not addr then
  ko("component " .. name, "only ghost components found -- reboot the computer")
  return
end

ok("component " .. name, "found at " .. addr:sub(1, 8))

-- What the component actually offers. A missing callback here explains any later failure.
local offered = {}
for k in pairs(methodNames) do offered[#offered + 1] = k end
table.sort(offered)
info("callbacks", table.concat(offered, " "))

-- 2. Read-only callbacks --------------------------------------------------
local progress = try("getProgress", "getProgress")
if progress then info("getProgress", string.format("%.2f", progress)) end

local working = try("isWorking", "isWorking")
if working ~= nil then info("isWorking", tostring(working)) end

local energy = try("getEnergy", "getEnergy")
if energy then
  if energy.capacity and energy.capacity > 0 then
    ok("getEnergy", string.format("%s / %s", tostring(energy.stored), tostring(energy.capacity)))
  else
    ko("getEnergy", "no capacity reported")
  end
end

local slots = try("listSlots", "listSlots")
local outputCount = 0
if slots then
  local slotNames = {}
  for k, v in pairs(slots) do
    if k ~= "outputs" and k ~= "size" then slotNames[#slotNames + 1] = k .. "=" .. tostring(v) end
  end
  table.sort(slotNames)
  if slots.outputs then for _ in pairs(slots.outputs) do outputCount = outputCount + 1 end end

  if slots.size and slots.size > 0 then
    ok("listSlots", string.format("size=%d, %s", slots.size, table.concat(slotNames, " ")))
  else
    ko("listSlots", "no inventory size reported")
  end
end

local tanks = try("listTanks", "listTanks")
local tankCount = 0
if tanks then
  for _, t in pairs(tanks) do
    tankCount = tankCount + 1
    info("listTanks", string.format("%s: %s/%s %s",
      tostring(t.name), tostring(t.amount), tostring(t.capacity), t.fluid or "empty"))
  end
  if tankCount == 0 then info("listTanks", "no tank on this machine") end
end

-- canStart and isValidInputs answer false plus a reason where Gendustry declares no such check,
-- so the reason matters as much as the boolean: "not supported by this machine" is a different
-- answer from "conditions not met".
local can, canWhy = try("canStart", "canStart")
if can ~= nil then
  info("canStart", tostring(can) .. (canWhy and (" -- " .. canWhy) or ""))
end

local valid, validWhy = try("isValidInputs", "isValidInputs")
if valid ~= nil then
  info("isValidInputs", tostring(valid) .. (validWhy and (" -- " .. validWhy) or ""))
end

-- 3. Events: enable, widen the interval, watch a full cycle ---------------
if try("setEventsEnabled(true)", "setEventsEnabled", true) ~= nil then
  local effective = try("areEventsEnabled", "areEventsEnabled")
  if effective then
    ok("events on", "global and per-device both enabled")
  else
    ko("events on", "still off -- general.enableEvents is probably false in the config")
  end
end

-- 40 ticks = 2s between samples: the interval that could drop a short cycle.
if try("setSignalInterval(40)", "setSignalInterval", 40) ~= nil then
  ok("setSignalInterval(40)", "coarse interval, watching for dropped signals")
end

-- Drain anything already queued so the counts below are about this cycle only.
while event.pull(0, name .. "_started") do end
while event.pull(0, name .. "_finished") do end
while event.pull(0, name .. "_output") do end

-- 4. A full cycle, watched through the signals ----------------------------
-- These machines cannot be driven from a script: bdlib's server tick calls tryStart() as soon as
-- the machine has inputs and energy, so it is already running by the time anyone types a command,
-- and canStart() reads false because the inputs have been consumed. Trying to catch it idle is
-- pointless. Feed it a *stack* of each input instead and watch a started/finished pair go by.
local started = try("start()", "start")
local running = try("isWorking", "isWorking")

if started then
  info("start()", "this call started the machine")
elseif running then
  info("start()", "false -- the tick had already auto-started the cycle (expected)")
else
  info("start()", "false and the machine is idle -- load a stack of inputs to watch a cycle")
end

local seen = { started = 0, finished = 0, output = 0 }
local handlers = {}
for _, key in ipairs({ "started", "finished", "output" }) do
  -- Keep the closure: event.ignore matches on identity, a fresh one would remove nothing.
  handlers[key] = function() seen[key] = seen[key] + 1 end
  event.listen(name .. "_" .. key, handlers[key])
end

local watch = math.min(cycleTimeout, 30)
info("watching signals", string.format("%ds at interval 40 -- keep the machine fed", watch))

-- Stop once every signal this machine can raise has been seen. _output must be part of the
-- condition: it is the one signal still throttled by signalInterval, so it trails the finish by
-- up to that many ticks and leaving early would report it missing.
local function enough()
  return seen.started > 0 and seen.finished > 0
    and (outputCount == 0 or seen.output > 0)
end

local t0 = computer.uptime()
local deadline = t0 + watch
while computer.uptime() < deadline and not enough() do
  os.sleep(0.2)
end
local watched = computer.uptime() - t0

for key, handler in pairs(handlers) do event.ignore(name .. "_" .. key, handler) end

-- The point of the coarse interval: a cycle shorter than 40 ticks must still raise both
-- transitions. Losing them here is the dropped-transition bug.
local function report(label, n)
  if n > 0 then
    ok(label, n .. " received")
  else
    ko(label, string.format("none in %.1fs", watched))
  end
end

report("signal _started", seen.started)
report("signal _finished", seen.finished)

if outputCount == 0 then
  if seen.output == 0 then
    ok("signal _output", "correctly silent: no output slot on this machine")
  else
    ko("signal _output", seen.output .. " raised on a machine that has no output slot")
  end
else
  report("signal _output", seen.output)
end

local outs = try("listOutputs", "listOutputs")
if outs then
  local n = 0
  for _, it in pairs(outs) do
    n = n + 1
    ok("listOutputs", string.format("slot %s: %s x%s",
      tostring(it.slot), tostring(it.label or it.name), tostring(it.count)))
  end
  if n == 0 then info("listOutputs", "output slot empty right now") end
end

-- 5. Restore the configured defaults --------------------------------------
if try("applyDefaultTuning", "applyDefaultTuning") ~= nil then
  ok("applyDefaultTuning", "config defaults restored")
end

print(string.rep("-", 40))
print(string.format("%s: %d passed, %d failed", name, pass, fail))
