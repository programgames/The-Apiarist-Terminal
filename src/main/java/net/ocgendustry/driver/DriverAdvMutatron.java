package net.ocgendustry.driver;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import net.bdew.gendustry.machines.advmutatron.TileMutatronAdv;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.ocgendustry.Log;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.ocgendustry.Config;
import net.ocgendustry.util.MutatronLogic;

public final class DriverAdvMutatron extends DriverSidedTileEntity {
    
    @Override
    public Class<?> getTileEntityClass() {
        return TileMutatronAdv.class;
    }

    @Override
    public boolean worksWith(World world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof TileMutatronAdv;
    }

    @Override
    public ManagedEnvironment createEnvironment(World world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileMutatronAdv) {
            return new Environment((TileMutatronAdv) te);
        }

        return null;
    }

    public static final class Environment extends AbstractManagedEnvironment implements NamedBlock {
        private final TileMutatronAdv tile;
        private final String componentName = "advmutatron";

        public Environment(TileMutatronAdv tile) {
            this.tile = tile;
            setNode(Network.newNode(this, Visibility.Network)
                .withComponent(componentName, Visibility.Network)
                .create());

            // Apply defaults from config on environment create
            signalInterval = MutatronLogic.clampSignalInterval(Config.advMutatronSignalInterval, Config.advMutatronSignalIntervalMax);
            waitStepSeconds = MutatronLogic.clampWaitStep(Config.advMutatronWaitInterval);
            eventsEnabled = Config.advMutatronDefaultEventsEnabled;
        }

        @Override
        public String preferredName() {
            return componentName;
        }

        @Override
        public int priority() {
            return 10;  // Higher than generic energy driver (0)
        }

        private Map<Integer, ItemStack> getPossibleMutations() {
            return tile.getPossibleMutations();
        }

        private void setMutation(int key) {
            tile.setMutation(key);
        }

        // Track state to emit OpenComputers signals without blocking.
        private boolean lastWorking = false;
        private String lastOutSig = "";
        private int signalInterval = 2; // emit signals every N ticks (default 2)
        private int tickCounter = 0;
        private double waitStepSeconds = 0.2; // cooperative wait step used by blocking methods

        // Per-device toggle in addition to global Config.enableEvents
        private boolean eventsEnabled = true;

        @Override
        public boolean canUpdate() {
            // Enable per-tick update() only when event emissions are enabled.
            return net.ocgendustry.Config.enableEvents && eventsEnabled;
        }

        @Override
        public void update() {
            // Respect global config for event emissions
            if (!net.ocgendustry.Config.enableEvents || !eventsEnabled) return;

            // Throttle update frequency for performance if configured
            tickCounter++;
            if (signalInterval > 1 && (tickCounter % signalInterval) != 0) return;

            boolean working = tile.isWorking();

            if (working && !lastWorking) {
                if (node() != null) node().sendToReachable("computer.signal", new Object[]{"advmutatron_started"});
            } else if (!working && lastWorking) {
                if (node() != null) node().sendToReachable("computer.signal", new Object[]{"advmutatron_finished"});
            }

            lastWorking = working;

            // Detect output changes (slot 2) and emit an event with the new stack info.
            ItemStack out = tile.getStackInSlot(2);
            String sig = signature(out);
            if (!sig.equals(lastOutSig)) {
                lastOutSig = sig;
                if (node() != null) node().sendToReachable("computer.signal", new Object[]{"advmutatron_output", stackInfo(out)});
            }
        }

        @Callback(doc = "function(enable:boolean):boolean -- Enable or disable events for this device instance only (global config may still disable events). Returns true on success.")
        public Object[] setEventsEnabled(Context ctx, Arguments args) {
            boolean en = args.checkBoolean(0);
            eventsEnabled = en;

            return new Object[]{ true };
        }

        @Callback(doc = "function():boolean -- Returns the per-device eventsEnabled flag (does not consider global config).")
        public Object[] getEventsEnabled(Context ctx, Arguments args) {
            return new Object[]{ eventsEnabled };
        }

        @Callback(doc = "function():boolean -- Returns whether events are effectively enabled right now (global AND per-device).")
        public Object[] areEventsEnabled(Context ctx, Arguments args) {
            return new Object[]{ net.ocgendustry.Config.enableEvents && eventsEnabled };
        }

        private static String signature(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return "";

            String name = (stack.getItem() != null && stack.getItem().getRegistryName() != null)
                ? stack.getItem().getRegistryName().toString() : "";

            String nbt = (stack.hasTagCompound() && stack.getTagCompound() != null)
                ? stack.getTagCompound().toString() : "";

            return name + "@" + stack.getCount() + "#" + nbt;
        }

        private static LinkedHashMap<String, Object> stackInfo(ItemStack stack) {
            LinkedHashMap<String, Object> info = new LinkedHashMap<>();

            if (stack != null && !stack.isEmpty()) {
                if (stack.getItem() != null && stack.getItem().getRegistryName() != null) {
                    info.put("name", stack.getItem().getRegistryName().toString());
                }

                info.put("count", stack.getCount());

                if (stack.hasTagCompound() && stack.getTagCompound() != null) {
                    info.put("label", stack.getDisplayName());
                    info.put("nbt", stack.getTagCompound().toString());
                }
            }

            return info;
        }

        // ---- Shared helpers for selection flows ----
        private Integer resolveSelectionKey(int n, Map<Integer, ItemStack> map) {
            if (map == null || map.isEmpty()) return null;

            List<Integer> keys = new ArrayList<>();
            for (Integer k : map.keySet()) keys.add(k);

            return MutatronLogic.resolveSelectionKey(n, keys);
        }

        // Return null if OK, otherwise an error reason string
        private String checkPreconditionsBeforeSelect() {
            ItemStack in1 = tile.getStackInSlot(0);
            ItemStack in2 = tile.getStackInSlot(1);
            ItemStack lab = tile.getStackInSlot(3);
            ItemStack out = tile.getStackInSlot(2);

            if (in1 == null || in1.isEmpty()) return "missing parent 1";
            if (in2 == null || in2.isEmpty()) return "missing parent 2";
            if (lab == null || lab.isEmpty()) return "missing labware";
            if (out != null && !out.isEmpty()) return "output full";

            return null;
        }

        @Callback(doc = "function():table -- Returns slot indices for generic item transfer: { in1:number, in2:number, labware:number, output:number, selectors:number[] }")
        public Object[] listSlots(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> slots = new LinkedHashMap<>();

            slots.put("in1", 0);
            slots.put("in2", 1);
            slots.put("labware", 3);
            slots.put("output", 2);
            slots.put("selectors", new Object[]{4, 5, 6, 7, 8, 9});

            return new Object[]{ slots };
        }

        @Callback(doc = "function():number -- Returns current work progress (0..1).")
        public Object[] getProgress(Context ctx, Arguments args) {
            // TileWorker provides getProgress in [0..1]
            return new Object[]{ tile.getProgress() };
        }

        @Callback(doc = "function():boolean -- Returns true if all conditions to start are currently satisfied.")
        public Object[] canStart(Context ctx, Arguments args) {
            return new Object[]{ tile.canStart() };
        }

        @Callback(doc = "function(ticks:number):boolean -- Set how often signals are emitted (every N ticks, min 1). Lower = more responsive, higher = less overhead.")
        public Object[] setSignalInterval(Context ctx, Arguments args) {
            int n = Math.max(1, args.checkInteger(0));
            signalInterval = MutatronLogic.clampSignalInterval(n, Config.advMutatronSignalIntervalMax);

            return new Object[]{ true };
        }

        @Callback(doc = "function(seconds:number):boolean -- Set the cooperative wait step used by blocking operations (default 0.2s, range 0.05..5). Lower = more responsive, higher = less overhead.")
        public Object[] setWaitInterval(Context ctx, Arguments args) {
            waitStepSeconds = MutatronLogic.clampWaitStep(args.checkDouble(0));

            return new Object[]{ true };
        }

        @Callback(doc = "function():boolean -- Reload defaults from the mod config and apply to this component instance.")
        public Object[] applyDefaultTuning(Context ctx, Arguments args) {
            Config.syncFromFile();

            signalInterval = MutatronLogic.clampSignalInterval(Config.advMutatronSignalInterval, Config.advMutatronSignalIntervalMax);
            waitStepSeconds = MutatronLogic.clampWaitStep(Config.advMutatronWaitInterval);
            eventsEnabled = Config.advMutatronDefaultEventsEnabled;

            return new Object[]{ true };
        }

        @Callback(doc = "function():table -- Returns mutagen tank info: { amount:number, capacity:number, fluid?:string }.")
        public Object[] getTank(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            IFluidHandler handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
            if (handler != null) {
                IFluidTankProperties[] props = handler.getTankProperties();
                if (props != null && props.length > 0 && props[0] != null) {
                    IFluidTankProperties p = props[0];
                    FluidStack fs = p.getContents();

                    out.put("capacity", p.getCapacity());
                    out.put("amount", fs != null ? fs.amount : 0);
                    if (fs != null && fs.getFluid() != null && fs.getFluid().getName() != null) {
                        out.put("fluid", fs.getFluid().getName());
                    }
                }
            }

            return new Object[]{ out };
        }

        @Callback(doc = "function():table|nil -- Returns the current output stack from slot 2 as {name,label?,nbt?,count}, or nil if empty.")
        public Object[] getOutput(Context ctx, Arguments args) {
            ItemStack out = tile.getStackInSlot(2);
            if (out == null || out.isEmpty()) return new Object[]{ null };

            return new Object[]{ stackInfo(out) };
        }

        @Callback(doc = "function():table -- Returns a table of possible mutations keyed by 1..N with fields {index,key,name,label?,nbt?}.")
        public Object[] listMutations(Context ctx, Arguments args) {
            Map<Integer, ItemStack> map = getPossibleMutations();
            LinkedHashMap<Integer, Object> out = new LinkedHashMap<>();

            if (map != null) {
                int i = 0;

                for (Map.Entry<Integer, ItemStack> e : map.entrySet()) {
                    ItemStack stack = e.getValue();
                    if (stack == null || stack.isEmpty()) continue;

                    i++;

                    LinkedHashMap<String, Object> info = new LinkedHashMap<>();
                    info.put("index", i);
                    info.put("key", e.getKey());

                    Item item = stack.getItem();
                    if (item != null && item.getRegistryName() != null) {
                        info.put("name", item.getRegistryName().toString());
                    }

                    // Avoid calling getDisplayName() on genetic items with no genome NBT, which causes Forestry to log spam.
                    if (stack.hasTagCompound() && stack.getTagCompound() != null) {
                        info.put("label", stack.getDisplayName());
                        info.put("nbt", stack.getTagCompound().toString());
                    }

                    out.put(i, info);
                }
            }

            return new Object[]{ out };
        }

        @Callback(doc = "function(n:number):boolean,string? -- Select a mutation by 1-based index (from listMutations) or by raw key (slot index), and start the process if possible.")
        public Object[] setMutation(Context ctx, Arguments args) {
            int n = args.checkInteger(0);

            Map<Integer, ItemStack> map = getPossibleMutations();
            if (map == null || map.isEmpty()) {
                return new Object[]{ false, "no mutations available" };
            }

            int size = map.size();
            int keyToUse = n;
            if (n >= 1 && n <= size) {
                int i = 0;
                for (Integer k : map.keySet()) {
                    i++;

                    if (i == n) {
                        keyToUse = k;
                        break;
                    }
                }
            }

            if (!map.containsKey(keyToUse)) return new Object[]{ false, "invalid index/key" };
            setMutation(keyToUse);

            return new Object[]{ true };
        }

        @Callback(doc = "function():boolean -- Try to start processing immediately; returns true if started by this call specifically, false otherwise.")
        public Object[] start(Context ctx, Arguments args) {
            return new Object[]{ tile.tryStart() };
        }

        @Callback(doc = "function(n:number[, timeout:number=60]):boolean,table|string? -- Select mutation (1-based index from listMutations or raw slot key), wait until finished without freezing, then return true and the output stack {name,label?,nbt?,count}; on failure returns false,reason.")
        public Object[] selectAndProduce(Context ctx, Arguments args) {
            int n = args.checkInteger(0);
            double timeoutSec = args.count() > 1 ? Math.max(0, args.checkDouble(1)) : 60.0;

            // Resolve selection key
            Map<Integer, ItemStack> map = getPossibleMutations();
            if (map == null || map.isEmpty()) return new Object[]{ false, "no mutations available" };

            Integer keyToUse = resolveSelectionKey(n, map);
            if (keyToUse == null) return new Object[]{ false, "invalid index/key" };

            // Preconditions (don't call canStart; selection may be required first)
            String why = checkPreconditionsBeforeSelect();
            if (why != null) return new Object[]{ false, why };

            // Set selection; some builds start automatically upon selection
            setMutation(keyToUse);
            tile.tryStart();

            // Wait cooperatively until processing starts (if not immediate) and then until it finishes
            long deadline = System.currentTimeMillis() + (long) (timeoutSec * 1000L);

            // Wait for start
            while (!tile.isWorking()) {
                if (System.currentTimeMillis() > deadline) return new Object[]{ false, "timeout (not started)" };

                ctx.pause(waitStepSeconds);
            }

            // Wait for finish
            while (tile.isWorking()) {
                if (System.currentTimeMillis() > deadline) return new Object[]{ false, "timeout" };

                ctx.pause(waitStepSeconds);
            }

            // Return produced item (if any) from output slot 2
            ItemStack out = tile.getStackInSlot(2);
            if (out == null || out.isEmpty()) return new Object[]{ false, "no output" };

            return new Object[]{ true, stackInfo(out) };
        }

        @Callback(doc = "function(n:number):boolean,string? -- Select mutation (1-based index from listMutations or raw slot key) and return immediately; use events advmutatron_started/finished/output to react.")
        public Object[] selectAndProduceAsync(Context ctx, Arguments args) {
            int n = args.checkInteger(0);

            Map<Integer, ItemStack> map = getPossibleMutations();
            if (map == null || map.isEmpty()) return new Object[]{ false, "no mutations available" };

            Integer keyToUse = resolveSelectionKey(n, map);
            if (keyToUse == null) return new Object[]{ false, "invalid index/key" };

            String why = checkPreconditionsBeforeSelect();
            if (why != null) return new Object[]{ false, why };

            setMutation(keyToUse);
            tile.tryStart();

            return new Object[]{ true };
        }
    }
}
