--[[
  testall.lua -- walks every Gendustry processing component present on the network and checks it.

  Usage: testall [component] [watchSeconds]
     testall                    -- every machine it can find, pausing between them
     testall genetic_transposer -- just that one
     testall dna_extractor 20   -- shorter signal watch

  Feed each machine a STACK of every input before running: these machines auto-start on their own
  tick, so a script can never catch one idle, and a single input is consumed before you can type.
  A machine that is neither working nor able to start is reported and skipped, not failed.
]]

local component = require("component")
local event = require("event")
local computer = require("computer")
local shell = require("shell")

local MACHINES = {
  "mutatron",
  "genetic_sampler",
  "genetic_imprinter",
  "genetic_replicator",
  "genetic_transposer",
  "dna_extractor",
  "protein_liquifier",
  "mutagen_producer",
}

-- Machines whose product is a fluid have no output slot and must stay silent on _output.
local FLUID_ONLY = {
  dna_extractor = true,
  protein_liquifier = true,
  mutagen_producer = true,
}

local args = shell.parse(...)
local only = args[1]
local watchSeconds = tonumber(args[2]) or 25

local totals = { pass = 0, fail = 0, skipped = 0, absent = 0 }
local report = {}

local function ok(label, detail)
  totals.pass = totals.pass + 1
  print(string.format("  [ OK ] %s%s", label, detail and (" -- " .. detail) or ""))
end

local function ko(label, detail)
  totals.fail = totals.fail + 1
  print(string.format("  [FAIL] %s%s", label, detail and (" -- " .. detail) or ""))
end

local function info(label, detail)
  print(string.format("  [    ] %s%s", label, detail and (" -- " .. detail) or ""))
end

-- Resolves a live component of that exact type. component.list matches substrings by default,
-- which would make "mutatron" also find "advmutatron", hence the exact flag. A component whose
-- methods() comes back empty is a ghost left by a replaced Adapter: skip it.
local function findLive(name)
  for candidate in component.list(name, true) do
    local okMethods, methods = pcall(component.methods, candidate)
    if okMethods and methods then
      for _ in pairs(methods) do return candidate, methods end
    end
  end

  return nil
end

local function call(addr, method, ...)
  local results = table.pack(pcall(component.invoke, addr, method, ...))
  if results[1] then return table.unpack(results, 2, results.n) end

  local err = results[2]
  if err == nil or err == "" then err = "java exception with no message" end

  return nil, tostring(err)
end

-- One machine, start to finish. Returns a short verdict for the final summary.
local function testMachine(name)
  local addr, methods = findLive(name)
  if not addr then
    totals.absent = totals.absent + 1
    print(string.format("== %s -- not on the network", name))

    return "absent"
  end

  print(string.format("== %s  %s", name, addr:sub(1, 8)))

  -- Read-only state ------------------------------------------------------
  local progress = call(addr, "getProgress")
  local working = call(addr, "isWorking")
  local energy = call(addr, "getEnergy")

  if energy and energy.capacity and energy.capacity > 0 then
    -- %.0f, not %d: these arrive as Java floats and %d raises on a non-integer value in Lua 5.3.
    ok("state", string.format("progress %.2f, working %s, energy %.0f/%.0f",
      progress or -1, tostring(working), energy.stored, energy.capacity))
  else
    ko("state", "getEnergy reported no capacity")
  end

  local slots = call(addr, "listSlots")
  local outputCount = 0
  if slots then
    local names = {}
    for k, v in pairs(slots) do
      if k ~= "outputs" and k ~= "size" then names[#names + 1] = k .. "=" .. tostring(v) end
    end
    table.sort(names)
    if slots.outputs then for _ in pairs(slots.outputs) do outputCount = outputCount + 1 end end

    local listed = #names > 0 and table.concat(names, " ") or "none named"
    ok("listSlots", string.format("size=%s, %s, %d output slot(s)",
      tostring(slots.size), listed, outputCount))
  else
    ko("listSlots", "no answer")
  end

  local tanks = call(addr, "listTanks")
  local tankCount = 0
  if tanks then
    for _, t in pairs(tanks) do
      tankCount = tankCount + 1
      info("tank " .. tostring(t.name), string.format("%s/%s %s",
        tostring(t.amount), tostring(t.capacity), t.fluid or "empty"))
    end
    if tankCount == 0 then info("listTanks", "no tank on this machine") end
  end

  -- The two checks that answer false plus a reason where Gendustry has none ---
  local can, canWhy = call(addr, "canStart")
  info("canStart", tostring(can) .. (canWhy and (" -- " .. canWhy) or ""))

  local valid, validWhy = call(addr, "isValidInputs")
  info("isValidInputs", tostring(valid) .. (validWhy and (" -- " .. validWhy) or ""))

  if FLUID_ONLY[name] then
    if can == false and canWhy == "not supported by this machine" then
      ok("canStart is absent-by-design", "fluid machine, Gendustry declares no pre-flight check")
    else
      ko("canStart is absent-by-design", "expected \"not supported by this machine\", got " .. tostring(canWhy))
    end
  end

  if name ~= "genetic_transposer" and valid == false and validWhy ~= "not supported by this machine" then
    ko("isValidInputs", "only the Genetic Transposer should give a real verdict, got: " .. tostring(validWhy))
  end

  -- Signals --------------------------------------------------------------
  call(addr, "setEventsEnabled", true)
  call(addr, "setSignalInterval", 40) -- coarse on purpose: the case that used to drop transitions

  if not (working or can == true) then
    totals.skipped = totals.skipped + 1
    info("signals", "skipped: machine idle and unable to start -- load a stack of inputs and power it")

    return "idle"
  end

  local seen = { started = 0, finished = 0, output = 0 }
  local handlers = {}
  for _, key in ipairs({ "started", "finished", "output" }) do
    -- Hold on to the closure: event.ignore matches on identity.
    handlers[key] = function() seen[key] = seen[key] + 1 end
    event.listen(name .. "_" .. key, handlers[key])
  end

  info("signals", string.format("watching up to %ds at interval 40", watchSeconds))

  local function enough()
    return seen.started > 0 and seen.finished > 0
      and (outputCount == 0 or seen.output > 0)
  end

  local t0 = computer.uptime()
  while computer.uptime() - t0 < watchSeconds and not enough() do
    os.sleep(0.2)
  end
  local watched = computer.uptime() - t0

  for key, handler in pairs(handlers) do event.ignore(name .. "_" .. key, handler) end

  local function signal(label, n)
    if n > 0 then ok(label, n .. " received")
    else ko(label, string.format("none in %.1fs", watched)) end
  end

  signal("_started", seen.started)
  signal("_finished", seen.finished)

  if outputCount == 0 then
    if seen.output == 0 then
      ok("_output stays silent", "correct: no output slot, tank levels would be noise")
    else
      ko("_output stays silent", seen.output .. " raised on a machine with no output slot")
    end
  else
    signal("_output", seen.output)
  end

  local outs = call(addr, "listOutputs")
  if outs then
    local n = 0
    for _, it in pairs(outs) do
      n = n + 1
      info("output", string.format("slot %s: %s x%s",
        tostring(it.slot), tostring(it.label or it.name), tostring(it.count)))
    end
    if n == 0 then info("output", "slot empty right now") end
  end

  call(addr, "applyDefaultTuning")

  return "tested"
end

-- Main -------------------------------------------------------------------
local list = only and { only } or MACHINES
local present = 0

for _, name in ipairs(list) do
  local verdict = testMachine(name)
  report[#report + 1] = string.format("%-20s %s", name, verdict)

  if verdict ~= "absent" then
    present = present + 1
    -- Pause so each machine's block can be read before the next scrolls it away.
    if not only then
      io.write("  -- enter for the next machine, q to stop --")
      if (io.read() or ""):sub(1, 1) == "q" then break end
    end
  end
end

print(string.rep("-", 46))
for _, line in ipairs(report) do print(line) end
print(string.format("%d machine(s) found, %d passed, %d failed, %d skipped",
  present, totals.pass, totals.fail, totals.skipped))
