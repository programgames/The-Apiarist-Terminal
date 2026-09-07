--[[
  breed.lua -- run one mutation on an Advanced Mutatron, from a chest, without leaving your seat.

  Usage:
    breed <target>                       -- one cross towards <target>
    breed imperial --timeout=90
    breed forest --chest=south --mutatron=up --apiary=north

  Options, all optional, named by side:
    --chest=<side>     where the parents, the labware and the product live   (default east)
    --mutatron=<side>  the Advanced Mutatron                                 (default west)
    --apiary=<side>    send the product here instead of back to the chest    (default: none)
    --timeout=<n>      seconds to wait for the cycle                         (default 60)

  Sides are named from the Transposer: north, south, east, west, up, down.

  What it needs built
  -------------------
  An OpenComputers Transposer touching the chest and the Mutatron, an Adapter on the Mutatron
  cabled to this computer, and in the chest: a princess or queen, a drone, and labware. The
  Mutatron also needs mutagen, which it draws itself.

      [chest] -- [transposer] -- [mutatron]

  The Advanced Mutatron is the one machine whose choice of mutation exists only in its GUI: no
  pipe, no robot and no other mod can make it. That choice is what this mod exposes, and this
  program is it end to end -- pick the cross, feed the machine, wait, take the bee out.

  The waiting happens here, in Lua, on the advmutatron_finished signal. A callback cannot wait: it
  runs on the server thread, and looping in one holds the world for every player on the server.
]]

local component = require("component")
local event = require("event")
local computer = require("computer")
local sides = require("sides")
local shell = require("shell")

local args, options = shell.parse(...)

local target = args[1]
if not target then
  print("usage: breed <target>   -- the species you want, e.g. breed imperial")
  print("       run `species <filter>` if you are not sure of the name")

  return
end

local function side(name, fallback)
  local wanted = options[name]
  if not wanted then return sides[fallback] end

  local resolved = sides[wanted]
  if not resolved then
    print(string.format("--%s=%s is not a side (north south east west up down)", name, wanted))

    return nil
  end

  return resolved
end

local CHEST = side("chest", "east")
local MUTATRON = side("mutatron", "west")
local APIARY = options.apiary and side("apiary", "north") or nil
if not CHEST or not MUTATRON or (options.apiary and not APIARY) then return end

local TIMEOUT = tonumber(options.timeout) or 60

-- Addresses rather than component.<name>: OpenOS caches a proxy per address in a Lua state that
-- survives a world reload, so a proxy built before a mod update keeps answering with the old
-- method list and a new callback looks like it does not exist.
local advAddress = component.list("advmutatron", true)()
local tpAddress = component.list("transposer", true)()

if not advAddress then
  print("no advmutatron on the network -- put an Adapter against one and cable it here")

  return
end
if not tpAddress then
  print("no transposer on the network -- this program moves items with one")
  print("(that is OpenComputers' own Transposer block, not Gendustry's Genetic Transposer)")

  return
end

local function adv(method, ...)
  local r = table.pack(pcall(component.invoke, advAddress, method, ...))
  if r[1] then return table.unpack(r, 2, r.n) end

  return nil, tostring(r[2])
end

local function tp(method, ...)
  local r = table.pack(pcall(component.invoke, tpAddress, method, ...))
  if r[1] then return table.unpack(r, 2, r.n) end

  return nil, tostring(r[2])
end

local slots = adv("listSlots")
if type(slots) ~= "table" then
  print("the mutatron did not answer listSlots -- is the Adapter still touching it?")

  return
end

-- Finding things in the chest ---------------------------------------------
local function label(stack)
  return (tostring(stack and (stack.label or stack.name) or "")):lower()
end

local function findInChest(matches)
  local n = tp("getInventorySize", CHEST)
  if not n then
    print("cannot see a chest on that side -- check --chest=")

    return nil
  end

  for slot = 1, n do
    local stack = tp("getStackInSlot", CHEST, slot)
    if stack and matches(label(stack)) then return slot, stack end
  end
end

local function feed(what, matches, into)
  local slot = findInChest(matches)
  if not slot then return false, "no " .. what .. " in the chest" end

  local moved = tp("transferItem", CHEST, MUTATRON, 1, slot, into)
  if not moved or moved < 1 then return false, "could not move the " .. what .. " into the mutatron" end

  return true
end

-- Load it -----------------------------------------------------------------
print(string.format("breeding towards '%s'", target))

local ok, why = feed("labware", function(l) return l:find("labware") end, slots.labware)
if not ok then print(why) return end

ok, why = feed("princess or queen", function(l)
  return l:find("princess") or l:find("queen")
end, slots.in1)
if not ok then print(why) return end

ok, why = feed("drone", function(l) return l:find("drone") end, slots.in2)
if not ok then print(why) return end

-- Choose the cross --------------------------------------------------------
local mutations = adv("listMutations")
if type(mutations) ~= "table" or next(mutations) == nil then
  print("these two parents offer no mutation at all -- try a different pair")

  return
end

local choice, offered = nil, {}
for index, m in pairs(mutations) do
  local name = tostring(m.label or m.name or "")
  offered[#offered + 1] = name
  if not choice and name:lower():find(target:lower(), 1, true) then choice = index end
end

if not choice then
  print(string.format("these parents cannot make '%s'. They offer:", target))
  table.sort(offered)
  for _, name in ipairs(offered) do print("  " .. name) end

  return
end

-- Run it ------------------------------------------------------------------
local started, refused = adv("selectAndProduce", choice)
if not started then
  print("the mutatron refused: " .. tostring(refused))

  return
end

print(string.format("started -- waiting up to %ds", TIMEOUT))

-- event.pull yields this computer and the server keeps ticking, which is the whole point.
if not event.pull(TIMEOUT, "advmutatron_finished") then
  if adv("isWorking") then
    print(string.format("still working after %ds -- raise it with --timeout=", TIMEOUT))
  else
    print("the mutatron stopped without finishing -- check its mutagen and its power")
  end

  return
end

local product = adv("getOutput")
if type(product) ~= "table" then
  print("the cycle finished but the output slot is empty -- something else took it")

  return
end

print(string.format("produced %s x%s", tostring(product.label or product.name),
  tostring(product.count)))

-- Take it out -------------------------------------------------------------
local destination, where = CHEST, "the chest"
if APIARY then destination, where = APIARY, "the apiary" end

local moved = tp("transferItem", MUTATRON, destination, 1, slots.output)
if moved and moved > 0 then
  print("moved to " .. where)
else
  print("could not move it to " .. where .. " -- it is still in the mutatron's output slot")
end
