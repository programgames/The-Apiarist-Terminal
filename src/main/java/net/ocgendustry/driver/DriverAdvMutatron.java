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
import net.ocgendustry.util.SignalState;
import net.ocgendustry.util.Tuning;

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
            signals = new SignalState(
                Tuning.clampSignalInterval(Config.advMutatronSignalInterval, Config.advMutatronSignalIntervalMax),
                tile.isWorking(),
                signature(tile.getStackInSlot(slotOutput())));

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

        // Signal bookkeeping, shared with the other drivers and unit-tested in SignalStateTest.
        // Primed from the machine in the constructor: starting from a value it never held makes
        // the first tick raise a signal that describes nothing.
        private final SignalState signals;

        // Per-device toggle in addition to global Config.enableEvents
        private boolean eventsEnabled = true;

        @Override
        public boolean canUpdate() {
            // Enable per-tick update() only when event emissions are enabled.
            return Config.enableEvents && eventsEnabled;
        }

        @Override
        public void update() {
            // Respect global config for event emissions
            if (!Config.enableEvents || !eventsEnabled) return;

            switch (signals.sample(tile.isWorking())) {
                case STARTED:
                    if (node() != null) node().sendToReachable("computer.signal", new Object[]{"advmutatron_started"});
                    break;
                case FINISHED:
                    if (node() != null) node().sendToReachable("computer.signal", new Object[]{"advmutatron_finished"});
                    break;
                default:
                    break;
            }

            // Reading the output slot is the expensive half, so that one is throttled.
            if (!signals.dueForOutputScan()) return;

            ItemStack out = tile.getStackInSlot(slotOutput());
            if (signals.outputChanged(signature(out))) {
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
            return new Object[]{ Config.enableEvents && eventsEnabled };
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
            ItemStack in1 = tile.getStackInSlot(slotIn1());
            ItemStack in2 = tile.getStackInSlot(slotIn2());
            ItemStack lab = tile.getStackInSlot(slotLabware());
            ItemStack out = tile.getStackInSlot(slotOutput());

            if (in1 == null || in1.isEmpty()) return "missing parent 1";
            if (in2 == null || in2.isEmpty()) return "missing parent 2";
            if (lab == null || lab.isEmpty()) return "missing labware";
            if (out != null && !out.isEmpty()) return "output full";

            // The mutagen, which the item slots say nothing about. Without it the machine accepts
            // the selection, reports a start, and then does nothing -- so a script waits out its
            // whole timeout for a signal that is never coming. canStart() would have caught it,
            // but it cannot be called here: on this machine it requires a selection to have been
            // made already, which is what this method runs before.
            int mutagen = tile.tank().getFluidAmount();
            int needed = tile.cfg().mutagenPerItem();
            if (mutagen < needed) return "not enough mutagen: " + mutagen + " of " + needed + " mB";

            return null;
        }

        // Slot indices come from the machine, never from constants. Gendustry names them, so a
        // reordering on its side cannot leave this driver reading the wrong slot -- the mistake the
        // eight processing machines were written to avoid from the start.
        private int slotIn1()     { return tile.slots().inIndividual1(); }
        private int slotIn2()     { return tile.slots().inIndividual2(); }
        private int slotLabware() { return tile.slots().inLabware(); }
        private int slotOutput()  { return tile.slots().outIndividual(); }

        private int[] slotSelectors() {
            scala.collection.immutable.Range.Inclusive range = tile.slots().selectors();
            int[] out = new int[range.length()];
            for (int i = 0; i < out.length; i++) out[i] = range.apply(i);

            return out;
        }

        @Callback(doc = "function():table -- Returns slot indices for generic item transfer: { in1:number, in2:number, labware:number, output:number, selectors:number[], size:number }")
        public Object[] listSlots(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> slots = new LinkedHashMap<>();

            slots.put("in1", slotIn1());
            slots.put("in2", slotIn2());
            slots.put("labware", slotLabware());
            slots.put("output", slotOutput());

            int[] selectors = slotSelectors();
            Object[] boxed = new Object[selectors.length];
            for (int i = 0; i < selectors.length; i++) boxed[i] = selectors[i];
            slots.put("selectors", boxed);

            // Every listSlots reports size, including the two hand-written drivers: a script that
            // drives any machine through the generic inventory calls iterates over it, and getting
            // nil back from the two most interesting components makes one loop into three.
            slots.put("size", tile.getSizeInventory());

            return new Object[]{ slots };
        }

        @Callback(doc = "function():boolean -- Returns true while the machine is processing.")
        public Object[] isWorking(Context ctx, Arguments args) {
            return new Object[]{ tile.isWorking() };
        }

        @Callback(doc = "function():table -- Returns the energy buffer: { stored:number, capacity:number }.")
        public Object[] getEnergy(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            out.put("stored", tile.power().stored());
            out.put("capacity", tile.power().capacity());

            return new Object[]{ out };
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
            signals.setSignalInterval(Tuning.clampSignalInterval(n, Config.advMutatronSignalIntervalMax));

            return new Object[]{ true };
        }

        @Callback(doc = "function():boolean -- Reload defaults from the mod config and apply to this component instance.")
        public Object[] applyDefaultTuning(Context ctx, Arguments args) {
            Config.syncFromFile();

            signals.setSignalInterval(
                Tuning.clampSignalInterval(Config.advMutatronSignalInterval, Config.advMutatronSignalIntervalMax));
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
            ItemStack out = tile.getStackInSlot(slotOutput());
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

            Integer keyToUse = resolveSelectionKey(n, map);
            if (keyToUse == null) return new Object[]{ false, "invalid index/key" };

            setMutation(keyToUse);

            return new Object[]{ true };
        }

        @Callback(doc = "function():boolean -- Try to start processing immediately; returns true if started by this call specifically, false otherwise.")
        public Object[] start(Context ctx, Arguments args) {
            return new Object[]{ tile.tryStart() };
        }

        @Callback(doc = "function(n:number):boolean,string? -- Select mutation (1-based index from listMutations or raw slot key) and start it, returning immediately; wait for the advmutatron_finished signal and then read getOutput(). Answers false plus a reason when a parent, the labware or the mutagen is missing, or the output slot is full. This used to block until the cycle ended, which froze the server thread for the whole timeout.")
        public Object[] selectAndProduce(Context ctx, Arguments args) {
            int n = args.checkInteger(0);

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

            return new Object[]{ true };
        }

        @Callback(doc = "function(n:number):boolean,string? -- Alias of selectAndProduce, kept for scripts written when selectAndProduce was the blocking variant.")
        public Object[] selectAndProduceAsync(Context ctx, Arguments args) {
            return selectAndProduce(ctx, args);
        }
    }
}
