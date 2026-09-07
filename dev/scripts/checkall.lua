--[[
  checkall.lua -- the acceptance pass for everything the other scripts leave untouched.

  Usage: checkall [watchSeconds]
     checkall      -- 30s watching the apiary for its signals
     checkall 60   -- longer, for a slow queen

  It closes two gaps at once, which is why it exists rather than a third script per area:

    apiarist.lua, the convenience library. It ships in the jar and the CHANGELOG advertises it,
    and until this script nothing had ever executed a line of it. Everything below goes through
    the library rather than component.invoke, so wrap/runCycle/produce/waitForPrincess are
    exercised by being used, not by being called for the sake of it.

    The Industrial Apiary's own read callbacks. Careful here: component.methods() on that block
    lists thirty names, and ten of them are not this mod's. getQueen, getDrone, canBreed,
    getBeeParents, getBeeBreedingData and listAllSpecies come from Forestry's own OpenComputers
    driver, and canExtract, canReceive, getEnergyStored and getMaxEnergyStored from OpenComputers'
    generic energy driver -- several environments share the block, which is the whole reason
    MachineEnvironment has to be final. Only ours are checked below.

  What this does NOT cover, because it needs a machine loaded rather than a callback called:
    genetic_transposer   the isValidInputs(blank, template) argument order, once reversed
    dna_extractor        silence on _output, and canStart answering "not supported"
  Both are testall's job. The last line below says whether they are on the network yet.
]]

local apiarist = require("apiarist")
local component = require("component")
local event = require("event")
local computer = require("computer")
local shell = require("shell")

local args = shell.parse(...)
local watchSeconds = tonumber(args[1]) or 30

local pass, fail, info = 0, 0, 0

local function ok(label, detail)
  pass = pass + 1
  print(string.format("[ OK ] %s%s", label, detail and (" -- " .. detail) or ""))
end

local function ko(label, detail)
  fail = fail + 1
  print(string.format("[FAIL] %s%s", label, detail and (" -- " .. detail) or ""))
end

local function note(label, detail)
  info = info + 1
  print(string.format("[    ] %s%s", label, detail and (" -- " .. detail) or ""))
end

-- 1. The library's own entry points -----------------------------------------------------------
print("== apiarist.lua")

local wrapped = {}
for _, name in ipairs({ "advmutatron", "industrial_apiary", "mutatron", "genetic_sampler",
                        "genetic_imprinter", "genetic_replicator", "genetic_transposer",
                        "dna_extractor", "protein_liquifier", "mutagen_producer" }) do
  local m = apiarist.wrap(name)
  if m then
    wrapped[name] = m
    -- A wrapper is only worth anything if calls through it reach the machine.
    local energy = m:energy()
    if type(energy) == "table" and energy.capacity then
      ok("wrap " .. name, string.format("%.0f/%.0f", energy.stored, energy.capacity))
    else
      ko("wrap " .. name, "the wrapper resolved but energy did not come back")
    end
  end
end

if next(wrapped) == nil then
  ko("wrap", "no component of this mod on the network")

  return
end

-- The refusal path: a name the library does not drive must be turned away with a reason rather
-- than producing a wrapper that fails later, somewhere less obvious.
local bad, why = apiarist.wrap("redstone")
if bad == nil and why then
  ok("wrap refuses a foreign component", why)
else
  ko("wrap refuses a foreign component", "it returned a wrapper for something it cannot drive")
end

-- 2. The Industrial Apiary's own callbacks -----------------------------------------------------
local apiary = wrapped.industrial_apiary
if not apiary then
  note("industrial_apiary", "absent -- its section is skipped")
else
  print("")
  print("== industrial_apiary, this mod's own callbacks")

  local bees = apiary:call("getBees")
  if type(bees) == "table" then
    ok("getBees", string.format("queen=%s drone=%s",
      bees.queen and (bees.queen.label or bees.queen.name) or "empty",
      bees.drone and (bees.drone.label or bees.drone.name) or "empty"))
  else
    ko("getBees", "no table")
  end

  local env = apiary:call("getEnvironment")
  if type(env) == "table" and env.temperature then
    ok("getEnvironment", string.format("%s / %s", tostring(env.temperature), tostring(env.humidity)))
  else
    ko("getEnvironment", "no temperature")
  end

  local mods = apiary:call("getModifiers")
  if type(mods) == "table" and next(mods) ~= nil then
    local shown = {}
    for k, v in pairs(mods) do shown[#shown + 1] = k .. "=" .. tostring(v) end
    table.sort(shown)
    ok("getModifiers", table.concat(shown, " ", 1, math.min(4, #shown)))
  else
    ko("getModifiers", "empty -- the apiary always has modifiers, upgrades or not")
  end

  local ups = apiary:call("listUpgrades")
  if type(ups) == "table" then
    ok("listUpgrades", #ups == 0 and "none installed" or (#ups .. " installed"))
  else
    ko("listUpgrades", "no table")
  end

  local outs = apiary:outputs()
  if type(outs) == "table" then
    local n = 0
    for _ in pairs(outs) do n = n + 1 end
    ok("listOutputs", n == 0 and "all empty" or (n .. " slot(s) holding something"))
  else
    ko("listOutputs", "no table")
  end

  local progress = apiary:progress()
  if type(progress) == "number" then
    ok("getProgress", string.format("%.2f", progress))
  else
    ko("getProgress", "not a number")
  end

  -- The tuning callbacks, which decide whether signals are emitted at all. Left as found.
  local wasOn = apiary:call("getEventsEnabled")
  apiary:call("setEventsEnabled", false)
  local offNow = apiary:call("getEventsEnabled")
  apiary:call("setEventsEnabled", true)
  local onAgain = apiary:call("getEventsEnabled")

  if offNow == false and onAgain == true then
    ok("setEventsEnabled", "off and on again, ending enabled")
  else
    ko("setEventsEnabled", string.format("off=%s on=%s", tostring(offNow), tostring(onAgain)))
  end

  apiary:call("setSignalInterval", 20)
  apiary:call("applyDefaultTuning")
  ok("setSignalInterval + applyDefaultTuning", "config defaults restored")

  if wasOn == false then apiary:call("setEventsEnabled", false) end
end

-- 3. The apiary's signals ----------------------------------------------------------------------
-- Never observed before this script. A queen runs for minutes, so a cycle boundary inside the
-- watch window is luck: not seeing one is reported as unobserved, not as a failure.
if apiary then
  print("")
  print(string.format("== apiary signals, watching %ds", watchSeconds))

  local seen = {}
  local deadline = computer.uptime() + watchSeconds
  while computer.uptime() < deadline do
    local left = deadline - computer.uptime()
    local name = event.pull(math.min(left, 1), "apiary_.*")
    if name then seen[name] = (seen[name] or 0) + 1 end
  end

  if next(seen) == nil then
    note("apiary signals", "none in the window -- a queen's cycle is longer than that")
  else
    for name, count in pairs(seen) do ok(name, count .. " received") end
  end
end

-- 4. The library's waiting helpers ---------------------------------------------------------------
print("")
print("== apiarist.lua, the parts that wait")

if apiary then
  -- Short on purpose: this is checking that the helper answers sensibly, not breeding a queen.
  local freed, reason = apiary:waitForPrincess(5)
  if freed == true then
    ok("waitForPrincess", "the queen slot came free")
  elseif reason then
    ok("waitForPrincess", "answered a reason rather than hanging: " .. tostring(reason))
  else
    ko("waitForPrincess", "neither a result nor a reason")
  end
end

-- runCycle on whichever processing machine is loaded and able to run.
local ranOne = false
for _, name in ipairs({ "genetic_sampler", "mutatron", "genetic_imprinter", "genetic_replicator",
                        "genetic_transposer" }) do
  local m = wrapped[name]
  if m and not ranOne then
    local outputs, whyNot = m:runCycle(30)
    if outputs then
      ok("runCycle on " .. name, "a cycle ran and the outputs came back")
      ranOne = true
    elseif whyNot then
      note("runCycle on " .. name, whyNot)
    end
  end
end
if not ranOne then
  note("runCycle", "no processing machine was loaded enough to run one -- feed one and run again")
end

-- produce() on the Advanced Mutatron, the library's one-call replacement for the blocking call.
local adv = wrapped.advmutatron
if adv then
  local mutations = adv:call("listMutations")
  if type(mutations) == "table" and next(mutations) ~= nil then
    local product, whyNot = adv:produce(1, 30)
    if product then
      ok("produce on advmutatron", tostring(product.label or product.name))
    else
      note("produce on advmutatron", tostring(whyNot))
    end
  else
    note("produce on advmutatron", "no mutation offered -- load two parents, labware and mutagen")
  end
end

print("")
print(string.rep("-", 52))
print(string.format("%d passed, %d failed, %d not observed", pass, fail, info))

-- What testall still needs, asked of the network rather than assumed. Printing a fixed list of
-- blocks to place reads as an instruction to stop, which is wrong once they are placed.
local missing = {}
for _, name in ipairs({ "genetic_transposer", "dna_extractor" }) do
  if not wrapped[name] then missing[#missing + 1] = name end
end

print("")
if #missing == 0 then
  print("Every block testall needs is on the network. Run testall next.")
else
  print("For testall, still to place: " .. table.concat(missing, ", "))
end
