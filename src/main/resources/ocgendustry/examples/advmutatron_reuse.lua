local component = require('component')
local sides = require('sides')
local adv = component.advmutatron or error('advmutatron not found')
local tp = component.transposer or error('transposer not found')
local apiary = component.industrial_apiary -- optional

-- Configuration: adjusted by the Java command if rig is auto-placed
local SRC = sides.east   -- chest side
local DST = sides.west   -- mutatron side
local APIARY = sides.north -- apiary side (if placed)
local TIMEOUT = 60       -- seconds per mutation

local function lower(s) return (tostring(s or '')):lower() end
local function log(...) print('[ATest:REUSE-CHAIN]', ...) end

local function find_in_src(pred)
  local n = tp.getInventorySize(SRC) or 0
  for slot=1,n do
    local st = tp.getStackInSlot(SRC, slot)
    if st and pred(st, slot) then return slot, st end
  end
end

local function push_one(slotSrc, slotDst)
  local moved = tp.transferItem(SRC, DST, 1, slotSrc, slotDst)
  return moved and moved > 0
end

-- Apply defaults from server config
adv.applyDefaultTuning()
local slots = adv.listSlots()

-- Chained plan:
-- 1) cultivated <= meadows + common (seeded)
-- 2) majestic   <= cultivated + noble (cultivated from 1, noble seeded)
-- 3) imperial   <= majestic + noble (majestic from 2, noble seeded)
local plan = {
  { out='cultivated', p1='meadows',    p2='common' },
  { out='majestic',   p1='cultivated', p2='noble'  },
  { out='imperial',   p1='majestic',   p2='noble'  },
}

local function need_labware()
  local slot, st = find_in_src(function(st) return lower(st.label):find('labware') end)
  if not slot then return false, 'no labware in source chest' end
  if not push_one(slot, slots.labware) then return false, 'failed to push labware' end
  return true
end

local function need_parent(which, species)
  local want = which == 1 and 'princess' or 'drone'
  local slot, st = find_in_src(function(st)
    local lab = lower(st.label)
    return lab:find(species) and lab:find(want)
  end)
  if not slot then return false, 'missing '..want..' for '..species end
  local dst = which == 1 and slots.in1 or slots.in2
  if not push_one(slot, dst) then return false, 'failed to push '..want end
  return true
end

local function select_output(expected)
  local muts = adv.listMutations()
  for i=1,#muts do
    local m = muts[i]
    local nm = lower(m.label or m.name or '')
    if nm:find(expected) then return i end
  end
end

local function pull_output()
  return tp.transferItem(DST, SRC, 1, slots.output)
end

local function move_queen_to_apiary()
  local moved = tp.transferItem(DST, APIARY, 1, slots.output)
  return moved and moved > 0
end

local function wait_apiary_cycle(timeout)
  local computer = require('computer')
  local event = require('event')
  local limit = timeout or 60
  local start = computer.uptime()
  if apiary then
    while computer.uptime() - start < limit do
      local prog = apiary.getProgress()
      if type(prog) == 'number' and prog >= 0.999 then return true end
      event.pull(0.5)
    end
    return false
  else
    local lastMove = 0
    while computer.uptime() - start < limit do
      local moved = tp.transferItem(APIARY, SRC, 64)
      if moved and moved > 0 then lastMove = computer.uptime() end
      event.pull(0.5)
      if lastMove > 0 and (computer.uptime() - lastMove) > 2 then return true end
    end
    return false
  end
end

local function harvest_apiary_outputs()
  -- Try driver-provided outputs first
  if apiary and apiary.listSlots then
    local ok, slotsApi = pcall(function() return apiary.listSlots() end)
    if ok and slotsApi and slotsApi.outputs then
      for _, slot in ipairs(slotsApi.outputs) do
        local moved = tp.transferItem(APIARY, SRC, 64, slot)
        if moved and moved > 0 then log('apiary->chest outputs moved', moved, 'from slot', slot) end
      end
      return
    end
  end
  -- Fallback: best-effort transfer
  for _=1,20 do
    local moved = tp.transferItem(APIARY, SRC, 64)
    if moved and moved > 0 then log('apiary->chest outputs moved', moved) else break end
  end
end

local function pull_princess_from_apiary(expectedSpecies)
  -- Prefer driver slot if available, but slot indices may vary; scan if needed
  local n = tp.getInventorySize(APIARY) or 0
  local targetSlot = nil
  local want = 'princess'
  for slot=1,n do
    local st = tp.getStackInSlot(APIARY, slot)
    if st and st.label then
      local lab = lower(st.label)
      if lab:find(want) and (not expectedSpecies or lab:find(expectedSpecies)) then
        targetSlot = slot
        log('apiary: found princess in outputs slot', slot, 'label', st.label)
        break
      end
    end
  end
  if targetSlot then
    local moved = tp.transferItem(APIARY, SRC, 1, targetSlot)
    if moved and moved > 0 then log('apiary->chest princess moved from slot', targetSlot) end
    return moved
  end
end

for idx, step in ipairs(plan) do
  log('CHAIN', idx, step.out, '<-', step.p1, '+', step.p2)
  local ok, why = need_labware()
  if not ok then log('SKIP', why) goto continue end

  local ok1, w1 = need_parent(1, step.p1)
  if not ok1 then log('SKIP', w1) goto continue end
  local ok2, w2 = need_parent(2, step.p2)
  if not ok2 then log('SKIP', w2) goto continue end

  local sel = select_output(step.out)
  if not sel then log('SKIP', 'desired output not offered by current parents') goto continue end

  -- selectAndProduce starts the cycle and returns; waiting happens here, on the signal. It used
  -- to block until the end, which held the server thread for its whole timeout.
  local okp, why = adv.selectAndProduce(sel)
  if not okp then log('FAIL', why) goto continue end
  if not event.pull(TIMEOUT, 'advmutatron_finished') then
    log('FAIL', 'no advmutatron_finished within '..tostring(TIMEOUT)..'s')
    goto continue
  end
  local res = adv.getOutput()
  if not res then log('FAIL', 'cycle finished but the output slot is empty') goto continue end
  log('PASS produced', (res.label or res.name or '?'), 'x'..tostring(res.count))
  -- Move the queen to the apiary to obtain a princess for the next chain step
  if not move_queen_to_apiary() then
    log('FAIL', 'could not move queen to apiary (check APIARY side wiring)')
    goto continue
  end

  -- Prefer the driver's cooperative blocker if present; else fallback to progress-based wait
  local waited = false
  if apiary and apiary.getPrincessStatus then
    local deadline = computer.uptime() + TIMEOUT
    while computer.uptime() < deadline do
      local st = apiary.getPrincessStatus()
      if st.error then
        log('FAIL', 'apiary error:', tostring(st.error))
        goto continue
      end
      if st.automated then
        log('FAIL', 'remove the Automation upgrade: it empties the queen slot by itself')
        goto continue
      end
      if st.freed then waited = true break end

      -- The wait belongs here, in Lua: the driver cannot block on the server thread.
      event.pull(1, 'apiary_finished')
    end

    if not waited then
      log('FAIL', 'queen still alive after '..tostring(TIMEOUT)..'s')
      goto continue
    end
  else
    waited = wait_apiary_cycle(TIMEOUT)
    if not waited then
      log('FAIL', 'apiary: timeout waiting for princess')
      goto continue
    end
  end

  -- Pull the produced princess (prefer matching the expected next princess species = step.out)
  pull_princess_from_apiary(step.out)
  -- Also pull apiary outputs to chest for convenience
  harvest_apiary_outputs()
::continue::
end

log('DONE reuse-chain')
