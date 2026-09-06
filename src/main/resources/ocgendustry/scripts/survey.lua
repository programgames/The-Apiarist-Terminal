--[[
  survey.lua -- full read-only report on every component this computer can see.

  Usage: survey [outputPath]
     survey                 -- writes /home/report.txt
     survey /home/r2.txt    -- somewhere else

  Read-only and quick: it starts nothing and consumes nothing, so it can be run on machines that
  are idle, empty or unpowered. It records what every Gendustry component reports about itself, so
  the state of a whole base can be reviewed off-screen.

  The report is far longer than a screen. Two ways to get it out:
    - quit the world to title, and the file lands on disk under
      saves/<world>/opencomputers/<uuid>/home/  (OpenComputers only flushes on save)
    - or, with an Internet Card:  pastebin put /home/report.txt
]]

local component = require("component")
local computer = require("computer")
local shell = require("shell")

local OURS = {
  advmutatron = "hand-written driver",
  industrial_apiary = "hand-written driver",
  mutatron = "processing machine",
  genetic_sampler = "processing machine",
  genetic_imprinter = "processing machine",
  genetic_replicator = "processing machine",
  genetic_transposer = "processing machine",
  dna_extractor = "processing machine (fluid output)",
  protein_liquifier = "processing machine (fluid output)",
  mutagen_producer = "processing machine (fluid output)",
}

local args = shell.parse(...)
local path = args[1] or "/home/report.txt"

local out = io.open(path, "w")
if not out then
  print("cannot write " .. path)
  return
end

local function w(line)
  out:write((line or "") .. "\n")
end

local function call(addr, method, ...)
  local r = table.pack(pcall(component.invoke, addr, method, ...))
  if r[1] then return table.unpack(r, 2, r.n) end

  return nil, tostring(r[2] or "error with no message")
end

-- 1. Everything on the network, by type ----------------------------------
local byType, order, total = {}, {}, 0
for addr, kind in component.list() do
  if not byType[kind] then
    byType[kind] = {}
    order[#order + 1] = kind
  end
  byType[kind][#byType[kind] + 1] = addr
  total = total + 1
end
table.sort(order)

w("APIARIST TERMINAL -- NETWORK SURVEY")
w(string.format("uptime %.0fs, memory %d/%d bytes free",
  computer.uptime(), computer.freeMemory(), computer.totalMemory()))
w("")
w(string.format("%d components of %d types", total, #order))
w(string.rep("=", 72))
for _, kind in ipairs(order) do
  local tag = OURS[kind] and ("   <-- " .. OURS[kind]) or ""
  w(string.format("%-28s x%d%s", kind, #byType[kind], tag))
end

-- 2. Every Gendustry component, in detail --------------------------------
local found, running = 0, 0

for _, kind in ipairs(order) do
  if OURS[kind] then
    for _, addr in ipairs(byType[kind]) do
      found = found + 1
      w("")
      w(string.rep("=", 72))
      w(string.format("%s   %s   (%s)", kind, addr, OURS[kind]))
      w(string.rep("=", 72))

      -- Which callbacks this component actually offers. A short list here means a driver
      -- problem; the long one is what a healthy component looks like.
      local okM, methods = pcall(component.methods, addr)
      if okM and methods then
        local names = {}
        for m in pairs(methods) do names[#names + 1] = m end
        table.sort(names)
        w(string.format("callbacks (%d): %s", #names, table.concat(names, " ")))
      else
        w("callbacks: NONE -- ghost component or driver failure")
      end

      local has = {}
      if okM and methods then for m in pairs(methods) do has[m] = true end end

      local working, wErr
      if has.isWorking then
        working, wErr = call(addr, "isWorking")
        if working then running = running + 1 end
      else
        wErr = "not exposed by this driver"
      end
      local progress = call(addr, "getProgress")
      w(string.format("working: %s%s   progress: %s",
        tostring(working), wErr and (" (" .. wErr .. ")") or "", tostring(progress)))

      local energy = call(addr, "getEnergy")
      if type(energy) == "table" then
        w(string.format("energy: %.0f / %.0f", energy.stored or 0, energy.capacity or 0))
      else
        -- The hand-written drivers expose the buffer under different names.
        local stored = call(addr, "getEnergyStored")
        local max = call(addr, "getMaxEnergyStored")
        w(string.format("energy: %s / %s", tostring(stored), tostring(max)))
      end

      local slots = call(addr, "listSlots")
      if type(slots) == "table" then
        local names = {}
        for k, v in pairs(slots) do
          if k ~= "outputs" and k ~= "size" then
            if type(v) == "table" then
              local parts = {}
              for _, idx in pairs(v) do parts[#parts + 1] = tostring(idx) end
              table.sort(parts)
              names[#names + 1] = k .. "=[" .. table.concat(parts, ",") .. "]"
            else
              names[#names + 1] = k .. "=" .. tostring(v)
            end
          end
        end
        table.sort(names)
        local outs = {}
        if slots.outputs then
          for _, idx in pairs(slots.outputs) do outs[#outs + 1] = tostring(idx) end
        end
        w(string.format("slots: size=%s | named: %s | outputs: [%s]",
          tostring(slots.size),
          #names > 0 and table.concat(names, " ") or "(none named)",
          table.concat(outs, ",")))
      else
        w("slots: no listSlots on this component")
      end

      local tanks = call(addr, "listTanks")
      if type(tanks) == "table" then
        local n = 0
        for _, t in pairs(tanks) do
          n = n + 1
          w(string.format("tank %-10s %s / %s  %s",
            tostring(t.name), tostring(t.amount), tostring(t.capacity), t.fluid or "(empty)"))
        end
        if n == 0 then w("tanks: none") end
      end

      local can, canWhy = call(addr, "canStart")
      if can ~= nil then
        w("canStart: " .. tostring(can) .. (canWhy and ("  -- " .. canWhy) or ""))
      end

      local valid, validWhy = call(addr, "isValidInputs")
      if valid ~= nil then
        w("isValidInputs: " .. tostring(valid) .. (validWhy and ("  -- " .. validWhy) or ""))
      end

      local events = call(addr, "areEventsEnabled")
      if events ~= nil then w("events enabled: " .. tostring(events)) end

      local outs = call(addr, "listOutputs")
      if type(outs) == "table" then
        local n = 0
        for _, it in pairs(outs) do
          n = n + 1
          w(string.format("output slot %s: %s x%s",
            tostring(it.slot), tostring(it.label or it.name), tostring(it.count)))
        end
        if n == 0 then w("outputs: all empty") end
      end

      -- Full inventory as the generic OpenComputers driver sees it, so the report also shows
      -- what sits in the input slots, which our read-only driver does not expose.
      local size = call(addr, "getInventorySize")
      if type(size) == "number" then
        for i = 1, size do
          local stack = call(addr, "getStackInSlot", i)
          if type(stack) == "table" then
            w(string.format("  inv[%d] %s x%s", i, tostring(stack.label or stack.name),
              tostring(stack.size or stack.count)))
          end
        end
      end
    end
  end
end

w("")
w(string.rep("=", 72))
w(string.format("%d Gendustry component(s), %d currently working", found, running))

out:close()

print(string.format("%d components, %d Gendustry, %d working", total, found, running))
print("report written to " .. path)
print("upload it with:  pastebin put " .. path)
print("or quit to title and it lands on disk for Claude to read")
