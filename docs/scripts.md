# The Lua programs

The mod ships nine Lua files inside its jar, under `ocgendustry/scripts/`. They are reference
programs and a library — nothing in the mod needs them, and nothing breaks if you never use them.

## Getting them onto a computer

They are resources inside the jar, so a computer cannot reach them on its own. Open
`apiarist-terminal-<version>.jar` with any zip tool, take what you want out of
`ocgendustry/scripts/`, and put the files on the computer's hard drive:

- `apiarist.lua` goes in `/usr/lib/` — that is on `package.path`, so `require("apiarist")` finds it
- everything else goes in `/usr/bin/` — that is on the shell's `PATH`, so each becomes a command

To write them from outside the game, a computer's hard drive is a directory on the host, named by
the component address:

```
<world>/opencomputers/<address>/usr/bin/
```

**A running computer holds its hard drive in memory** and writes it out when the world saves
(`bufferChanges` in OpenComputers' config, `true` by default). Files added underneath a running
computer are invisible to it and overwritten at the next save, so close the world first — or set
`bufferChanges=false`, which is what this repository's dev environment does.

## The library

`apiarist.lua` is the only file here meant to be used by your own programs. It exists because a
callback cannot wait: it runs on the server thread, and looping in one holds the tick loop for
every player on the server. So the components expose state and signals, and nothing that blocks —
and the waiting moves here, into Lua, where `event.pull` yields the computer and the server keeps
running.

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

## The programs

Each writes what it finds to a file as well as the screen where the output is long, because a full
run scrolls past several screens.

| Command | What it is for |
|---|---|
| `survey` | Every component on the network and everything it reports about itself. Read-only: it starts nothing and consumes nothing, so it is safe on machines that are idle, empty or unpowered. Writes `/home/report.txt`. |
| `testall [component] [seconds]` | Walks every Gendustry machine present and checks it, signals included. Writes `/home/testall.txt`. |
| `machine_test <component>` | One machine, in depth: every callback, and the `_started`/`_finished`/`_output` edges at a deliberately coarse interval. |
| `checkall [seconds]` | The library itself, and the Industrial Apiary's own callbacks and signals. |
| `species [filter\|get <uid>\|dump]` | The bee species Forestry knows about, read through the apiary. `species get` prints one genome's thirteen chromosomes. |
| `nofreeze [n] [timeout]` | Times `selectAndProduce` and says whether the call held the server thread. See below. |
| `advmutatron_fresh`, `advmutatron_reuse` | Two worked automation rigs: an OpenComputers Transposer feeding an Advanced Mutatron from a chest, one breeding a fresh line, one reusing the product. |

## `nofreeze`, and why it exists

```
calling selectAndProduce(1)...
returned in 0.050s
[ OK ] the call returned promptly -- the server thread was never held
waiting for advmutatron_finished, up to 60s...
[ OK ] advmutatron_finished after 5.1s
output: Common Queen x1
```

Two numbers: the call returned in 50 milliseconds, the machine worked for 5.1 seconds. Between
them, the server kept ticking.

Before this fork, `selectAndProduce` did not return until the cycle ended. That is a callback, so it
runs on the server thread, and the whole world stopped for as long as it waited — no chest opens, no
mob moves, for every player on the server. The logs in this repository recorded two ticks running
60045ms behind before it was found.

`nofreeze` is what turns that from a claim into a number you can read.
