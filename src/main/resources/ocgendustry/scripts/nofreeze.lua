--[[
  nofreeze.lua -- times selectAndProduce and shows that it does not hold the server thread.

  Usage: nofreeze [n] [timeout]
     nofreeze        -- first available mutation, 60s to finish
     nofreeze 2 30   -- the second one, shorter wait

  Load the Advanced Mutatron first: two parent bees, a STACK of labware, and power.

  What this measures, and why it is the test the whole fork rests on
  ------------------------------------------------------------------
  A callback runs on the server thread. Callback.direct() defaults to false, and Context.pause()
  does not suspend a call -- it only schedules a pause for after the call returns. So a callback
  that loops waiting for a machine holds the tick loop for its whole timeout: the world stops for
  every player on the server, mobs freeze, no GUI opens. The original selectAndProduce did exactly
  that, and logs/ recorded two ticks running 60045ms behind.

  So the number that matters is the FIRST one below: how long the call itself takes to return.
  A few milliseconds means the work was handed to the machine and the server kept ticking. Seconds
  mean the server thread was held, and every player was held with it.

  The waiting then happens here, in Lua, on the advmutatron_finished signal -- event.pull yields
  the computer and the server keeps running. That is the whole shape of the fix: the machine is
  asked, the tick loop is released, and the answer arrives as a signal.
]]

local component = require("component")
local computer = require("computer")
local event = require("event")
local shell = require("shell")

local args = shell.parse(...)
local choice = tonumber(args[1]) or 1
local timeout = tonumber(args[2]) or 60

-- A callback that returns this fast cannot have waited for anything.
local PROMPT_SECONDS = 1.0

local adv = component.list("advmutatron", true)()
if not adv then
  print("no advmutatron on the network -- put an Adapter against one and cable it here")

  return
end
print(string.format("advmutatron %s", adv:sub(1, 8)))

local function call(method, ...)
  local r = table.pack(pcall(component.invoke, adv, method, ...))
  if r[1] then return table.unpack(r, 2, r.n) end

  return nil, tostring(r[2])
end

-- What the machine can make right now. Empty means the inputs are missing or make no mutation,
-- and there is nothing to time.
local mutations = call("listMutations")
if type(mutations) ~= "table" or next(mutations) == nil then
  print("no mutations offered -- load two parent bees, a stack of labware, and power")

  return
end

for k, m in pairs(mutations) do
  print(string.format("  %s  %s", tostring(k), tostring(m.label or m.name)))
end

if not mutations[choice] then
  print(string.format("no mutation %d in that list", choice))

  return
end

-- The measurement. computer.uptime() is wall clock; os.clock() would report the Lua state's own
-- CPU time and show nothing while the server thread is blocked elsewhere.
print(string.format("calling selectAndProduce(%d)...", choice))
local before = computer.uptime()
local started, why = call("selectAndProduce", choice)
local elapsed = computer.uptime() - before

print(string.format("returned in %.3fs", elapsed))

if elapsed < PROMPT_SECONDS then
  print("[ OK ] the call returned promptly -- the server thread was never held")
else
  print(string.format("[FAIL] the call took %.1fs. A callback cannot wait: this held the tick loop "
    .. "and every player on the server with it.", elapsed))
end

if not started then
  print("selectAndProduce refused: " .. tostring(why))

  return
end

-- Now the waiting, in the one place it is allowed. event.pull yields the computer; the server
-- keeps ticking, which is exactly what the blocking version could not do.
print(string.format("waiting for advmutatron_finished, up to %ds...", timeout))
local waitFrom = computer.uptime()
local deadline = waitFrom + timeout
local finished = false

repeat
  local left = deadline - computer.uptime()
  if left <= 0 then break end

  -- Re-checking isWorking covers the cycle that ended before we started listening.
  if event.pull(math.min(left, 1), "advmutatron_finished") then finished = true end
until finished or not call("isWorking")

local waited = computer.uptime() - waitFrom

if finished then
  print(string.format("[ OK ] advmutatron_finished after %.1fs", waited))
elseif not call("isWorking") then
  print(string.format("[ OK ] the cycle ended within %.1fs, signal not caught", waited))
else
  print(string.format("[    ] still working after %.1fs -- raise the timeout", waited))
end

local out = call("getOutput")
if type(out) == "table" then
  print(string.format("output: %s x%s", tostring(out.label or out.name), tostring(out.count)))
else
  print("output slot empty -- the product may have been pulled already")
end

print("")
print("Now check the server console: no 'Running ...ms behind' should have appeared,")
print("and the world should have stayed responsive throughout.")
