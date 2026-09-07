--[[
  genome.lua -- what the bee in the apiary actually carries, and how far it is from what you want.

  Usage:
    genome                          -- the queen's genome
    genome drone                    -- the drone's
    genome vs forestry.speciesForest -- the queen against a target species

  A bee carries two alleles per chromosome and only the active one is expressed. A chromosome where
  both agree is settled and breeds true; one where they differ is still carrying something from an
  older parent, and is the only kind a further cross can change. This prints the difference.

  getSpeciesTemplate answers what a species is worth by default. getGenome answers what this bee
  has. `vs` is the two put side by side, which is the whole of a breeding decision.
]]

local component = require("component")
local shell = require("shell")

local args = shell.parse(...)

-- component.invoke rather than component.industrial_apiary: OpenOS caches a proxy per address in
-- the Lua state, and that state survives a world reload -- so a proxy built before a mod update
-- keeps answering with the old method list, and a new callback looks like it does not exist.
local address = component.list("industrial_apiary", true)()
if not address then
  print("no industrial_apiary on the network -- put an Adapter against one and cable it here")

  return
end

local function call(method, ...)
  local r = table.pack(pcall(component.invoke, address, method, ...))
  if r[1] then return table.unpack(r, 2, r.n) end

  return nil, tostring(r[2])
end

-- pairs() has no order and thirteen chromosomes in arbitrary order do not read as a table.
local function sortedKeys(t)
  local keys = {}
  for k in pairs(t) do keys[#keys + 1] = k end
  table.sort(keys)

  return keys
end

local function fetch(slot)
  local g, why = call("getGenome", slot)
  if not g then
    print(slot .. ": " .. tostring(why))

    return nil
  end

  return g
end

-- genome vs <species> -----------------------------------------------------
if args[1] == "vs" then
  local target = args[2]
  if not target then
    print("usage: genome vs <species>   -- try `species forest` for the names")

    return
  end

  local g = fetch("queen")
  if not g then return end

  local template, why = call("getSpeciesTemplate", target)
  if not template then
    print(target .. ": " .. tostring(why))

    return
  end

  local speciesNow = g.chromosomes.species and g.chromosomes.species.active.name or "?"
  local speciesWant = template.species and template.species.name or target

  print(string.format("your queen : %s %s, generation %d", speciesNow, g.bee.type, g.bee.generation))
  print(string.format("target     : %s  (%s)", speciesWant, target))
  print("")

  local same, todo = 0, {}
  for _, k in ipairs(sortedKeys(template)) do
    local want = template[k]
    local has = g.chromosomes[k]

    if has and has.active.uid == want.uid then
      if has.pure then
        same = same + 1
      else
        -- The right allele is already showing, but the other side carries something else, so a
        -- cross can still lose it. Invisible from the active allele alone, and worth saying.
        todo[#todo + 1] = { k, has.active.name .. " (+" .. has.inactive.name .. ")", want.name,
                            "already right, not fixed" }
      end
    elseif has then
      todo[#todo + 1] = { k, has.active.name, want.name,
        want.dominant and "one cross (dominant)" or "both parents (recessive)" }
    end
  end

  print(string.format("%d of 13 chromosomes already match the target and breed true.", same))
  if #todo == 0 then
    print("Nothing left to breed for -- this queen is the target.")

    return
  end

  print(string.format("%d differ:", #todo))
  print("")
  print(string.format("  %-22s %-24s %-12s %s",
    "chromosome", "your queen has", "target has", "how to get it"))
  print("  " .. string.rep("-", 74))
  for _, row in ipairs(todo) do
    print(string.format("  %-22s %-24s %-12s %s", row[1], row[2], row[3], row[4]))
  end
  print("")
  print("\"one cross\" -- a dominant allele shows up from a single parent that has it.")
  print("\"both parents\" -- a recessive one stays hidden until both sides carry it, which")
  print("                 takes several generations.")

  return
end

-- genome [queen|drone] ----------------------------------------------------
local slot = args[1] or "queen"
local g = fetch(slot)
if not g then return end

print(string.format("%s -- %s, generation %d, %s%s",
  slot, g.bee.type, g.bee.generation,
  g.bee.natural and "natural" or "artificial",
  g.bee.mated and ", mated" or ""))
print("")

local mixed = 0
for _, k in ipairs(sortedKeys(g.chromosomes)) do
  local c = g.chromosomes[k]
  if c.pure then
    print(string.format("  %-22s %-12s", k, c.active.name))
  else
    mixed = mixed + 1
    print(string.format("  %-22s %-12s / %-12s  MIXED", k, c.active.name, c.inactive.name))
  end
end

print("")
if mixed == 0 then
  print("every chromosome breeds true")
else
  print(string.format("%d chromosome(s) still mixed -- only those can change in a further cross",
    mixed))
end

if g.mate then
  print("")
  print("the drone she was mated with:")
  for _, k in ipairs(sortedKeys(g.mate)) do
    local c = g.mate[k]
    print(string.format("  %-22s %-12s%s", k, c.active.name, c.pure and "" or " / " .. c.inactive.name))
  end
end
