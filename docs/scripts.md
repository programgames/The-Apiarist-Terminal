# The Lua library and programs

Two different things live under this heading, and mixing them up helps nobody.

**The jar ships a floppy**, under `assets/ocgendustry/disk/`: a library you call from your own
programs, and four worked programs you can run as they are. That is what this page is about.

**Four more live in `dev/scripts/` in the repository and are deliberately not in the jar**:
`testall`, `machine_test`, `checkall` and `nofreeze`. They exist to check that this mod behaves, not
to be used with it — see `DEVELOPMENT.md`.

**Two more are in the jar but not on the floppy**, under `assets/ocgendustry/harness/`:
`advmutatron_fresh` and `advmutatron_reuse`. They belong to the `/ocgendustry test` harness, which
writes them out — a fixed plan of three Forestry crosses reported as PASS, SKIP and FAIL. That is a
test, not something to hand a player, and it sat on the floppy until it was read closely.

## The library

`apiarist.lua`, which `install` puts in `/usr/lib`, is the one meant for your own programs.

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

`install` puts these in `/usr/bin`, so each is a command. Each is also a worked example, if you
would rather read one than run it.

| Command | What it does |
|---|---|
| `survey` | Every component on the network and everything it reports about itself. Read-only: it starts nothing and consumes nothing, so it is safe on machines that are idle, empty or unpowered. Writes `/home/report.txt`, which is longer than a screen. |
| `species [filter]` | The bee species Forestry knows about, read through the apiary, grouped by the mod that registered them. |
| `species get <uid>` | One species' default genome: its thirteen chromosomes, each with its dominance. |
| `genome [queen\|drone]` | What the bee in that slot actually carries: both alleles per chromosome, with the mixed ones marked. A mixed chromosome is still carrying something from an older parent and is the only kind a further cross can change. |
| `genome vs <species>` | The queen against a target species: how many chromosomes are already fixed as wanted, and what is left — flagging the recessive ones, which need both parents. |
| `breed <target>` | One cross on an Advanced Mutatron, run from a chest: it feeds the parents and the labware, picks the mutation you named, waits for the cycle and takes the bee back out. Sides are options — `--chest=`, `--mutatron=`, `--apiary=`, `--timeout=`. |

## Getting them onto a computer

**Take the floppy.** The mod registers one with OpenComputers — *Apiarist Terminal*, yellow, in the
creative inventory next to the OpenOS and OPPM disks, and in the wrench cycling other loot disks
take part in. Put it in a disk drive and run:

```
install
```

That copies the library to `/usr/lib` and the programs to `/usr/bin` — where `package.path` and the
shell already look, so `require("apiarist")` and `survey` work straight away, with no paths to set.

If you would rather read the files than install them, they are in the jar under
`assets/ocgendustry/disk/`, laid out exactly as the floppy.

To write them from outside the game, a computer's hard drive is a directory on the host, named by
the component address:

```
<world>/opencomputers/<address>/usr/bin/
```

**A running computer holds its hard drive in memory** and writes it out when the world saves
(`bufferChanges` in OpenComputers' config, `true` by default). Files added underneath a running
computer are invisible to it and overwritten at the next save, so close the world first — or set
`bufferChanges=false`, which is what this repository's dev environment does.
