--[[
  species.lua -- browse the bee species Forestry knows about, through the Industrial Apiary.

  Usage:
    species                  -- how many species, and which mod each batch comes from
    species <filter>         -- list the species whose uid or name contains <filter>
    species get <species>    -- the thirteen chromosomes of one species' default genome
    species dump [filter]    -- write the whole listing to /home/species.txt

  <species> is an allele uid, an allele name or a display name: the component resolves it through
  Forestry's registry, so anything the listing shows can be pasted straight back in.
]]

local component = require("component")
local shell = require("shell")

-- Forestry's karyotype order. pairs() over the returned table is unordered, and reading a genome
-- is much easier when the chromosomes always come out the same way round.
-- The keys are what IChromosomeType.getName() returns, which is lower_snake_case -- not the name
-- of the EnumBeeChromosome constant.
local CHROMOSOMES = {
  "species", "speed", "lifespan", "fertility", "temperature_tolerance",
  "never_sleeps", "humidity_tolerance", "tolerates_rain", "cave_dwelling",
  "flower_provider", "flowering", "territory", "effect",
}

local args = shell.parse(...)

local addr
local seenApiaries = 0
for candidate in component.list("industrial_apiary", true) do
  seenApiaries = seenApiaries + 1
  local ok, methods = pcall(component.methods, candidate)

  -- Test the key, not its value: OpenOS rewrites the methods table so each entry holds the
  -- callback's `direct` flag, and none of this mod's callbacks are direct, so every value is
  -- false. `if methods.listSpeciesTemplates then` would reject a perfectly good component.
  if ok and methods and methods.listSpeciesTemplates ~= nil then
    addr = candidate
    break
  end
end

if not addr then
  if seenApiaries == 0 then
    print("no industrial_apiary on the network")
    print("attach an Adapter to an Industrial Apiary; if you just moved one, reboot to clear the")
    print("stale component the old Adapter left behind")
  else
    print(string.format("%d industrial_apiary found, none exposing listSpeciesTemplates", seenApiaries))
    print("the loaded jar predates that callback -- restart Minecraft, not just the world")
  end

  return
end

local function call(method, ...)
  local r = table.pack(pcall(component.invoke, addr, method, ...))
  if r[1] then return table.unpack(r, 2, r.n) end

  return nil, tostring(r[2] or "call failed")
end

-- Sorted array of species, so the same command always prints the same order.
local function fetch(filter)
  -- Call with no argument at all when there is no filter: passing nil still counts as an
  -- argument on the Java side, where it would be rejected as a missing string.
  local list, err
  if filter then
    list, err = call("listSpeciesTemplates", filter)
  else
    list, err = call("listSpeciesTemplates")
  end
  if not list then
    print("listSpeciesTemplates failed: " .. tostring(err))

    return nil
  end

  local arr = {}
  for _, sp in pairs(list) do arr[#arr + 1] = sp end
  table.sort(arr, function(a, b) return tostring(a.uid) < tostring(b.uid) end)

  return arr
end

-- The uid prefix is the mod that registered the species. Showing the split is the whole point of
-- reading the registry: building "forestry.species" .. name would only ever find one of these.
local function prefixOf(uid)
  local prefix = tostring(uid):match("^([^%.]+)%.")

  return prefix or "(no prefix)"
end

local function showGenome(wanted)
  local genome, err = call("getSpeciesTemplate", wanted)
  if not genome then
    print("getSpeciesTemplate failed: " .. tostring(err))

    return
  end

  if genome == false then
    print("not found: " .. tostring(err))

    return
  end

  print("genome of " .. wanted)
  local seen = {}
  for _, name in ipairs(CHROMOSOMES) do
    local allele = genome[name]
    if allele then
      seen[name] = true
      print(string.format("  %-22s %-28s %s",
        name, tostring(allele.name), allele.dominant and "dominant" or "recessive"))
    else
      print(string.format("  %-22s (not set in this template)", name))
    end
  end

  -- Anything Forestry added that this script does not know about yet.
  for name, allele in pairs(genome) do
    if not seen[name] then
      print(string.format("  %-22s %-28s %s  <-- unlisted chromosome",
        name, tostring(allele.name), allele.dominant and "dominant" or "recessive"))
    end
  end
end

local function showList(arr, limit)
  local shown = 0
  for _, sp in ipairs(arr) do
    if limit and shown >= limit then
      print(string.format("  ... and %d more, narrow it down with a filter", #arr - shown))
      break
    end
    shown = shown + 1
    print(string.format("  %-38s %-24s %s%s",
      tostring(sp.uid), tostring(sp.name),
      sp.dominant and "dominant " or "recessive",
      sp.hasTemplate and "" or "  (no template)"))
  end
end

local mode = args[1]

if mode == "get" then
  if not args[2] then
    print("usage: species get <uid|name>")

    return
  end
  showGenome(args[2])

elseif mode == "dump" then
  local arr = fetch(args[2])
  if not arr then return end

  local path = "/home/species.txt"
  local out = io.open(path, "w")
  if not out then
    print("cannot write " .. path)

    return
  end

  for _, sp in ipairs(arr) do
    out:write(string.format("%s\t%s\t%s\t%s\n", tostring(sp.uid), tostring(sp.name),
      sp.dominant and "dominant" or "recessive",
      sp.hasTemplate and "template" or "no-template"))
  end
  out:close()

  print(string.format("%d species written to %s", #arr, path))
  print("save the world and read it on the host, under opencomputers/<uuid>" .. path)

elseif mode then
  local arr = fetch(mode)
  if not arr then return end

  print(string.format("%d species matching '%s'", #arr, mode))
  showList(arr, 40)

else
  local arr = fetch()
  if not arr then return end

  local byPrefix, order, withTemplate = {}, {}, 0
  for _, sp in ipairs(arr) do
    local prefix = prefixOf(sp.uid)
    if not byPrefix[prefix] then
      byPrefix[prefix] = 0
      order[#order + 1] = prefix
    end
    byPrefix[prefix] = byPrefix[prefix] + 1
    if sp.hasTemplate then withTemplate = withTemplate + 1 end
  end
  table.sort(order, function(a, b) return byPrefix[a] > byPrefix[b] end)

  print(string.format("%d bee species registered, %d with a genome template", #arr, withTemplate))
  print("by the mod that registered them:")
  for _, prefix in ipairs(order) do
    print(string.format("  %-22s %d", prefix, byPrefix[prefix]))
  end

  print("")
  print("species <filter>       list them        e.g. species forest")
  print("species get <uid>      one genome       e.g. species get " .. tostring(arr[1] and arr[1].uid))
  print("species dump [filter]  write to a file")
end
