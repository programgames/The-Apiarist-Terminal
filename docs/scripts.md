# The Lua library and programs

Two different things live under this heading, and mixing them up helps nobody.

**The jar ships five files**, under `ocgendustry/`: a library you call from your own programs, and
four worked programs you can run as they are. That is what this page is about.

**Four more live in `dev/scripts/` in the repository and are deliberately not in the jar**:
`testall`, `machine_test`, `checkall` and `nofreeze`. They exist to check that this mod behaves, not
to be used with it — see `DEVELOPMENT.md`.

## The library

`ocgendustry/lib/apiarist.lua` is the one meant for your own programs.

It exists because a callback cannot wait. A callback runs on the server thread, so looping in one
until a machine finishes holds the tick loop — and every player on the server — for as long as it
waits. The components therefore expose state and signals and nothing that blocks, and the waiting
moves here, into Lua, where `event.pull` yields your computer and the server keeps running.

```lua
local apiarist = require("apiarist")

local sampler = apiarist.wrap("genetic_sampler")
local out = sampler:runCycle(30)          -- start if needed, wait, return what came out

local adv = apiarist.wrap("advmutatron")
local bee = adv:produce(1, 60)            -- select mutation 1, wait for it, return the product

local apiary = apiarist.wrap("industrial_apiary")
apiary:waitForPrincess(180)               -- until the queen slot frees, with the reasons it cannot
```

| Function | What it does |
|---|---|
| `apiarist.find(name)` | the address of a live component of that type, skipping the ghosts a replaced Adapter leaves behind |
| `apiarist.wrap(name[, addr])` | a wrapper, or `nil` plus a reason for anything this mod does not drive |
| `m:isWorking()` `m:progress()` `m:energy()` | state, straight through |
| `m:slots()` `m:tanks()` `m:outputs()` | layout and contents |
| `m:waitForFinish(timeout)` | until the machine stops, on the `_finished` signal |
| `m:runCycle(timeout)` | start if needed, wait, return the outputs |
| `m:produce(n, timeout)` | Advanced Mutatron only: select, run, return the product |
| `m:waitForPrincess(timeout)` | Industrial Apiary only, and it refuses to lie: with an Automation upgrade the queen slot empties by itself, so "freed" would stop meaning "a cycle ended" |

Every call answers `nil` plus a reason rather than throwing, so a missing machine or a stale address
is something your program can react to.

`runCycle` deserves a note. `start()` usually answers `false`, and that is not an error: the
machines start themselves on their own server tick, so a loaded one is already running by the time
a script asks. What matters is whether a cycle is running, not who started it, and that is what
`runCycle` checks.

## The programs

In `ocgendustry/examples/`. Each is a complete program you can run, and a worked example if you
would rather read than run.

| Command | What it does |
|---|---|
| `survey` | Every component on the network and everything it reports about itself. Read-only: it starts nothing and consumes nothing, so it is safe on machines that are idle, empty or unpowered. Writes `/home/report.txt`, which is longer than a screen. |
| `species [filter]` | The bee species Forestry knows about, read through the apiary, grouped by the mod that registered them. |
| `species get <uid>` | One species' default genome: its thirteen chromosomes, each with its dominance. |
| `advmutatron_fresh` | A full automation rig: an OpenComputers Transposer feeding an Advanced Mutatron from a chest, breeding a fresh line and moving the product to an apiary. |
| `advmutatron_reuse` | The same rig, reusing the mutatron's own product as the next parent. |

## Getting them onto a computer

They are resources inside the jar, so a computer cannot reach them by itself. Open
`apiarist-terminal-<version>.jar` with any zip tool and copy what you want out:

- `ocgendustry/lib/apiarist.lua` goes in `/usr/lib/` — that is on `package.path`, so
  `require("apiarist")` finds it
- everything in `ocgendustry/examples/` goes in `/usr/bin/` — that is on the shell's `PATH`, so each
  becomes a command

To write them from outside the game, a computer's hard drive is a directory on the host, named by
the component address:

```
<world>/opencomputers/<address>/usr/bin/
```

**A running computer holds its hard drive in memory** and writes it out when the world saves
(`bufferChanges` in OpenComputers' config, `true` by default). Files added underneath a running
computer are invisible to it and overwritten at the next save, so close the world first — or set
`bufferChanges=false`, which is what this repository's dev environment does.
