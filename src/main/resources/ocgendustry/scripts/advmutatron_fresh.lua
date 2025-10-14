local component = require('component')
local event = require('event')
local computer = require('computer')
local sides = require('sides')
local adv = component.advmutatron or error('advmutatron not found')
local tp = component.transposer or error('transposer not found')
local apiary = component.industrial_apiary -- optional

-- Configuration: adjust these for your setup
local SRC = sides.east   -- chest side
local DST = sides.west   -- mutatron side
local APIARY = sides.north -- apiary side (if placed)
local TIMEOUT = 60       -- seconds per mutation

local function lower(s) return (tostring(s or '')):lower() end
local function log(...) print('[ATest:FRESH]', ...) end

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

-- Fresh-only: always pull a new princess/queen and drone; do not reuse results as inputs
local plan = {
  { out='imperial',   p1='noble',     p2='majestic'  },
  { out='edenic',     p1='tropical',  p2='exotic'    },
  { out='rural',      p1='meadows',   p2='diligent'  },
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
    return lab:find(species) and (lab:find(want) or lab:find('queen') and which == 1)
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
  -- Try to pull result queen to the chest
  return tp.transferItem(DST, SRC, 1, slots.output)
end

local function move_queen_to_apiary()
  -- Move 1 queen/princess from mutatron output into apiary; let transposer pick the right slot
  local moved = tp.transferItem(DST, APIARY, 1, slots.output)
  return moved and moved > 0
end

local function wait_apiary_cycle(timeout)
  local limit = timeout or 60
  local start = computer.uptime()
  if apiary then
    -- Prefer accurate progress if driver is available
    while computer.uptime() - start < limit do
      local prog = apiary.getProgress()
      if type(prog) == 'number' and prog >= 0.999 then return true end
      event.pull(0.5)
    end
    return false
  else
    -- Generic fallback: periodically try to pull something from apiary; if nothing moves for a while, keep waiting up to limit
    local lastMove = 0
    while computer.uptime() - start < limit do
      local moved = tp.transferItem(APIARY, SRC, 64)
      if moved and moved > 0 then
        lastMove = computer.uptime()
      end
      event.pull(0.5)
      -- stop early if we moved something recently and a couple seconds passed without further moves
      if lastMove > 0 and (computer.uptime() - lastMove) > 2 then return true end
    end
    return false
  end
end

for _, step in ipairs(plan) do
  log('TEST-FRESH', step.out, '<-', step.p1, '+', step.p2)
  local ok, why = need_labware()
  if not ok then log('SKIP', why) goto continue end
  local ok1, w1 = need_parent(1, step.p1)
  if not ok1 then log('SKIP', w1) goto continue end
  local ok2, w2 = need_parent(2, step.p2)
  if not ok2 then log('SKIP', w2) goto continue end

  local sel = select_output(step.out)
  if not sel then log('SKIP', 'desired output not offered by current parents') goto continue end

  local okp, res = adv.selectAndProduce(sel, TIMEOUT)
  if not okp then log('FAIL', res) goto continue end
  log('PASS produced', (res.label or res.name or '?'), 'x'..tostring(res.count))
  -- If apiary is present, process the queen then collect outputs; otherwise just pull to chest
  if move_queen_to_apiary() then
    log('apiary: processing queen ...')
    -- Prefer the driver's cooperative blocker if available
    local usedDriverWait = false
    if apiary and apiary.waitForPrincess then
      local ok, res = apiary.waitForPrincess(TIMEOUT)
      usedDriverWait = true
      if not ok then
        log('apiary: waitForPrincess failed', tostring(res))
      end
    end

    if (usedDriverWait and true) or (not usedDriverWait and wait_apiary_cycle(TIMEOUT)) then
      -- pull outputs from apiary to chest using driver-provided slots if available
      local ok, slotsApi = pcall(function() return apiary.listSlots() end)
      if ok and slotsApi and slotsApi.outputs then
        for _, slot in ipairs(slotsApi.outputs) do
          local moved = tp.transferItem(APIARY, SRC, 64, slot)
          if moved and moved > 0 then log('apiary->chest outputs moved', moved, 'from slot', slot) end
        end
      else
        -- generic fallback: try moving anything (best-effort)
        for _=1,20 do
          local moved = tp.transferItem(APIARY, SRC, 64)
          if moved and moved > 0 then log('apiary->chest outputs moved', moved) else break end
        end
      end
    else
      log('apiary: timeout waiting for cycle')
    end
  else
    pull_output()
  end
::continue::
end

log('DONE fresh')
