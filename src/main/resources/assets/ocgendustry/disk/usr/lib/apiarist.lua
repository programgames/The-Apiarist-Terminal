--[[
  apiarist.lua -- the convenience layer, on the side where waiting is allowed.

  Copy it to /lib/apiarist.lua on the computer, then:

      local apiarist = require("apiarist")

      local sampler = apiarist.wrap("genetic_sampler")
      local out = sampler:runCycle(30)          -- start, wait, return what came out

  A callback cannot wait. It runs on the server thread, and Context.pause() does not suspend it --
  it schedules a pause for after the call returns, so a loop around it holds the tick loop for its
  whole timeout. That is why the components expose state and signals and nothing that blocks.

  Waiting is legitimate here, in Lua: event.pull yields the computer and the server keeps ticking.
  This library is the ergonomics that were lost when the blocking callbacks were removed, put back
  where they belong.
]]

local component = require("component")
local event = require("event")
local computer = require("computer")

local apiarist = {}

-- Components a machine wrapper can be built on. The two hand-written ones are here too: they now
-- expose isWorking and getEnergy like the rest, so one wrapper fits all ten.
local MACHINES = {
  advmutatron = true, industrial_apiary = true,
  mutatron = true, genetic_sampler = true, genetic_imprinter = true,
  genetic_replicator = true, genetic_transposer = true,
  dna_extractor = true, protein_liquifier = true, mutagen_producer = true,
}

--- Finds a live component of that type, skipping the ghosts a replaced Adapter leaves behind.
-- A ghost keeps its type but has no methods, and answers "no such method" to everything.
-- @param name component type, e.g. "genetic_sampler"
-- @return address, or nil plus a reason
function apiarist.find(name)
  local seen = 0

  for candidate in component.list(name, true) do
    seen = seen + 1
    local ok, methods = pcall(component.methods, candidate)

    -- Test the key, not its value: OpenOS rewrites this table so each entry holds the callback's
    -- `direct` flag, and nothing in this mod is direct, so every value is false.
    if ok and methods and methods.isWorking ~= nil then
      return candidate
    end
  end

  if seen == 0 then
    return nil, "no " .. name .. " on the network"
  end

  return nil, seen .. " " .. name .. " found, all stale -- reboot the computer"
end

local Machine = {}
Machine.__index = Machine

--- Wraps one machine.
-- @param name component type
-- @param address optional; resolved with apiarist.find when omitted
function apiarist.wrap(name, address)
  if not MACHINES[name] then
    return nil, "not a machine this mod drives: " .. tostring(name)
  end

  local addr = address
  if not addr then
    local found, why = apiarist.find(name)
    if not found then return nil, why end
    addr = found
  end

  return setmetatable({ name = name, address = addr }, Machine)
end

--- Calls a callback on this machine. Returns nil plus the reason on failure rather than throwing.
function Machine:call(method, ...)
  local r = table.pack(pcall(component.invoke, self.address, method, ...))
  if r[1] then return table.unpack(r, 2, r.n) end

  return nil, tostring(r[2] or "call failed")
end

function Machine:isWorking() return self:call("isWorking") end
function Machine:progress()  return self:call("getProgress") end
function Machine:energy()    return self:call("getEnergy") end
function Machine:slots()     return self:call("listSlots") end
function Machine:tanks()     return self:call("listTanks") end
function Machine:outputs()   return self:call("listOutputs") end

--- Waits until the machine stops working.
-- @param timeout seconds; defaults to 60
-- @return true, or false plus "timeout"
function Machine:waitForFinish(timeout)
  timeout = timeout or 60

  if not self:isWorking() then return true end

  local deadline = computer.uptime() + timeout
  repeat
    local left = deadline - computer.uptime()
    if left <= 0 then return false, "timeout" end

    -- _finished is raised from the machine's own tick, so a cycle shorter than the signal
    -- interval still wakes us. Re-checking isWorking covers the case where the signal arrived
    -- before we started listening.
    event.pull(math.min(left, 1), self.name .. "_finished")
  until not self:isWorking()

  return true
end

--- Starts a cycle if one is not already running, waits for it, and returns what came out.
--
-- start() usually answers false and that is not an error: bdlib's server tick calls the machine's
-- own tryStart() every tick, so a loaded machine has already started by the time a script asks.
-- What matters is whether a cycle is running, not who started it.
--
-- @param timeout seconds to wait; defaults to 60
-- @return the output table, or nil plus a reason
function Machine:runCycle(timeout)
  local started = self:call("start")
  local running = self:isWorking()

  if not started and not running then
    local can, why = self:call("canStart")
    if can == false and why then return nil, why end

    return nil, "machine is idle and will not start -- check inputs and energy"
  end

  local done, why = self:waitForFinish(timeout)
  if not done then return nil, why end

  local outs = self:outputs()
  if not outs then return nil, "cycle finished but the outputs could not be read" end

  return outs
end

--- Advanced Mutatron only: selects a mutation, runs it, returns the produced stack.
-- This is the one-call convenience the blocking selectAndProduce used to offer, with the wait
-- moved out of the callback.
-- @param n 1-based index from listMutations, or a raw selector slot key
-- @param timeout seconds; defaults to 60
function Machine:produce(n, timeout)
  if self.name ~= "advmutatron" then
    return nil, "produce() is specific to the advmutatron component"
  end

  local ok, why = self:call("selectAndProduce", n)
  if not ok then return nil, why or "selectAndProduce refused" end

  local done, timedOut = self:waitForFinish(timeout)
  if not done then return nil, timedOut end

  return self:call("getOutput")
end

--- Industrial Apiary only: waits until the queen slot is free, watching for the conditions that
-- would make that wait meaningless.
-- @param timeout seconds; defaults to 180, the lifetime of a slow queen
-- @return true, or false plus a reason
function Machine:waitForPrincess(timeout)
  if self.name ~= "industrial_apiary" then
    return nil, "waitForPrincess() is specific to the industrial_apiary component"
  end

  timeout = timeout or 180
  local deadline = computer.uptime() + timeout

  while computer.uptime() < deadline do
    local st = self:call("getPrincessStatus")
    if not st then return false, "status unavailable" end

    if st.freed then return true end
    if st.error then return false, st.error end

    -- The Automation upgrade empties the queen slot by itself, so "freed" would stop meaning
    -- "a cycle ended" and the answer would be a lie.
    if st.automated then return false, "remove the Automation upgrade" end

    event.pull(1, "apiary_finished")
  end

  return false, "timeout"
end

return apiarist
