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
import net.bdew.gendustry.api.ApiaryModifiers;
import net.bdew.gendustry.machines.apiary.TileApiary;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import forestry.api.apiculture.IBeekeepingLogic;
import forestry.api.core.IErrorLogic;
import forestry.api.apiculture.IBeeRoot;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.genetics.ISpeciesRoot;
import forestry.api.genetics.AlleleManager;
import net.ocgendustry.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * OpenComputers driver for Gendustry Industrial Apiary (TileApiary).
 * This driver is intentionally read-only and exposes slot indices so scripts can use
 * generic inventory pushItems/pullItems for all write operations.
 */
public final class DriverApiary extends DriverSidedTileEntity {

    @Override
    public Class<?> getTileEntityClass() {
        return TileApiary.class;
    }

    @Override
    public boolean worksWith(World world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof TileApiary;
    }

    @Override
    public ManagedEnvironment createEnvironment(World world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileApiary) {
            return new Environment((TileApiary) te);
        }

        return null;
    }

    public static final class Environment extends AbstractManagedEnvironment implements NamedBlock {
        private final TileApiary tile;
        private final String componentName = "industrial_apiary";
        private double waitStepSeconds = 0.2; // cooperative wait step for blocking helpers
        private int signalInterval = 2; // ticks
        private int tickCounter = 0;
        private boolean lastWorking = false;
        private String lastOutputSig = "";

        // Per-device toggle in addition to global Config.enableEvents
        private boolean eventsEnabled = true;

        // Slot layout from Gendustry TileApiary.scala (mc1.12):
        // 0: queen/princess, 1: drone, 2-5: upgrades, 6-14: output
        private static final int SLOT_QUEEN = 0;
        private static final int SLOT_DRONE = 1;
        private static final int[] SLOTS_UPGRADES = new int[]{2, 3, 4, 5};
        private static final int[] SLOTS_OUTPUT = new int[]{6, 7, 8, 9, 10, 11, 12, 13, 14};

        public Environment(TileApiary tile) {
            this.tile = tile;
            setNode(Network.newNode(this, Visibility.Network)
                .withComponent(componentName, Visibility.Network)
                .create());

            signalInterval = Math.max(1, Math.min(Config.apiarySignalInterval, Config.apiarySignalIntervalMax));
            waitStepSeconds = Math.max(0.05, Math.min(5.0, Config.apiaryWaitInterval));
            eventsEnabled = Config.apiaryDefaultEventsEnabled;
        }

        @Override
        public String preferredName() {
            return componentName;
        }

        @Override
        public int priority() {
            return 10; // above generic energy/inventory drivers
        }

        @Override
        public boolean canUpdate() {
            return Config.enableEvents && eventsEnabled;
        }

        @Override
        public void update() {
            if (!net.ocgendustry.Config.enableEvents || !eventsEnabled) return;

            tickCounter++;
            if (signalInterval > 1 && (tickCounter % signalInterval) != 0) return;

            boolean working = false;
            IBeekeepingLogic logic = tile.getBeekeepingLogic();
            if (logic != null) {
                float pct = logic.getBeeProgressPercent();
                working = pct > 0f && pct < 100f;
            }

            if (working && !lastWorking && node() != null) node().sendToReachable("computer.signal", new Object[]{"apiary_started"});
            if (!working && lastWorking && node() != null) node().sendToReachable("computer.signal", new Object[]{"apiary_finished"});
            lastWorking = working;

            // Emit output change
            String sig = signatureOutputs();
            if (!sig.equals(lastOutputSig)) {
                lastOutputSig = sig;
                if (node() != null) node().sendToReachable("computer.signal", new Object[]{"apiary_output"});
            }
        }

        @Callback(doc = "function(enable:boolean):boolean -- Enable or disable events for this device instance only (global config may still disable events). Returns true on success.")
        public Object[] setEventsEnabled(Context ctx, Arguments args) {
            eventsEnabled = args.checkBoolean(0);

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

        private String signatureOutputs() {
            StringBuilder sb = new StringBuilder();
            for (int slot : SLOTS_OUTPUT) {
                ItemStack st = getStackInSlot(slot);
                if (st != null && !st.isEmpty() && st.getItem() != null && st.getItem().getRegistryName() != null) {
                    sb.append(st.getItem().getRegistryName().toString()).append('@').append(st.getCount()).append('|');
                } else {
                    sb.append('-').append('|');
                }
            }

            return sb.toString();
        }

        // ---- Helpers ----

        private static void putStackInfo(LinkedHashMap<String, Object> out, ItemStack stack) {
            if (stack.getItem() != null && stack.getItem().getRegistryName() != null) {
                out.put("name", stack.getItem().getRegistryName().toString());
            }

            if (stack.hasTagCompound() && stack.getTagCompound() != null) {
                out.put("label", stack.getDisplayName());
                out.put("nbt", stack.getTagCompound().toString());
            }

            out.put("count", stack.getCount());
        }

        private ItemStack getStackInSlot(int slot) {
            return tile.getStackInSlot(slot);
        }

        private static Object[] toArray(int[] slots) {
            Object[] arr = new Object[slots.length];
            for (int i = 0; i < slots.length; i++)
                arr[i] = slots[i];

            return arr;
        }

        // ---- API ----

        @Callback(doc = "function():table -- Returns slot groups for generic item transfer: { queen:number, drone:number, bees:number[], upgrades:number[], outputs:number[] }")
        public Object[] listSlots(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> slots = new LinkedHashMap<>();

            slots.put("queen", SLOT_QUEEN);
            slots.put("drone", SLOT_DRONE);
            slots.put("bees", new Object[]{SLOT_QUEEN, SLOT_DRONE});
            slots.put("upgrades", toArray(SLOTS_UPGRADES));
            slots.put("outputs", toArray(SLOTS_OUTPUT));

            return new Object[]{slots};
        }

        @Callback(doc = "function():table -- Returns shallow info about the bee slots: { queen:table?, drone:table? } with fields {name,label?,nbt?,count}.")
        public Object[] getBees(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            ItemStack queen = getStackInSlot(SLOT_QUEEN);
            if (queen != null && !queen.isEmpty()) {
                LinkedHashMap<String, Object> q = new LinkedHashMap<>();
                putStackInfo(q, queen);
                out.put("queen", q);
            }

            ItemStack drone = getStackInSlot(SLOT_DRONE);
            if (drone != null && !drone.isEmpty()) {
                LinkedHashMap<String, Object> d = new LinkedHashMap<>();
                putStackInfo(d, drone);
                out.put("drone", d);
            }

            return new Object[]{out};
        }

        @Callback(doc = "function():table -- Lists installed upgrades as an array of {name,label?,nbt?,count}.")
        public Object[] listUpgrades(Context ctx, Arguments args) {
            List<Object> arr = new ArrayList<>();

            for (int slot : SLOTS_UPGRADES) {
                ItemStack stack = getStackInSlot(slot);
                if (stack == null || stack.isEmpty()) continue;

                LinkedHashMap<String, Object> info = new LinkedHashMap<>();
                putStackInfo(info, stack);
                info.put("slot", slot);

                arr.add(info);
            }

            return new Object[]{ arr.toArray() };
        }

        @Callback(doc = "function():table -- Lists output slots as an array of {name,label?,nbt?,count,slot} for non-empty outputs.")
        public Object[] listOutputs(Context ctx, Arguments args) {
            List<Object> arr = new ArrayList<>();

            for (int slot : SLOTS_OUTPUT) {
                ItemStack stack = getStackInSlot(slot);
                if (stack == null || stack.isEmpty()) continue;

                LinkedHashMap<String, Object> info = new LinkedHashMap<>();
                putStackInfo(info, stack);
                info.put("slot", slot);
                arr.add(info);
            }

            return new Object[]{ arr.toArray() };
        }

        @Callback(doc = "function():number -- Returns current work progress from Forestry logic as a 0..1 fraction.")
        public Object[] getProgress(Context ctx, Arguments args) {
            IBeekeepingLogic logic = tile.getBeekeepingLogic();

            return new Object[]{ (logic != null) ? (logic.getBeeProgressPercent() / 100.0f) : 0.0f };
        }

        @Callback(doc = "function(seconds:number):boolean -- Set the cooperative wait step used by blocking operations (default 0.2s, range 0.05..5). Lower = more responsive, higher = less overhead.")
        public Object[] setWaitInterval(Context ctx, Arguments args) {
            waitStepSeconds = Math.max(0.05, Math.min(5.0, args.checkDouble(0)));

            return new Object[]{ true };
        }

        @Callback(doc = "function(ticks:number):boolean -- Set how often signals are emitted (every N ticks, min 1). Lower = more responsive, higher = less overhead.")
        public Object[] setSignalInterval(Context ctx, Arguments args) {
            int n = Math.max(1, args.checkInteger(0));

            signalInterval = Math.min(n, Config.apiarySignalIntervalMax);

            return new Object[]{ true };
        }

        @Callback(doc = "function():boolean -- Reload defaults from the mod config and apply to this component instance.")
        public Object[] applyDefaultTuning(Context ctx, Arguments args) {
            Config.syncFromFile();

            signalInterval = Math.max(1, Math.min(Config.apiarySignalInterval, Config.apiarySignalIntervalMax));
            waitStepSeconds = Math.max(0.05, Math.min(5.0, Config.apiaryWaitInterval));
            eventsEnabled = Config.apiaryDefaultEventsEnabled;

            return new Object[]{ true };
        }

    @Callback(doc = "function([timeout:number=180]):boolean,string? -- Wait (non-freezing) until the current queen dies and the queen slot is freed. If a princess is inserted, this continues waiting until a queen is bred and killed, or the timeout elapses. Returns false,reason on timeout or if no princess/queen present.")
        public Object[] waitForPrincess(Context ctx, Arguments args) {
            double timeoutSec = args.count() > 0 ? Math.max(0, args.checkDouble(0)) : 180.0;

            // Resolve bee root to distinguish queen vs princess
            ISpeciesRoot root = AlleleManager.alleleRegistry.getSpeciesRoot("rootBees");
            if (!(root instanceof IBeeRoot)) return new Object[]{ false, "bee root not available" };

            IBeeRoot beeRoot = (IBeeRoot) root;

            // Require a queen to be present initially
            ItemStack slot0 = getStackInSlot(SLOT_QUEEN);
            if (slot0 == null || slot0.isEmpty() || (!beeRoot.isMember(slot0, EnumBeeType.QUEEN) && !beeRoot.isMember(slot0, EnumBeeType.PRINCESS))) {
                return new Object[]{ false, "no princess/queen in slot" };
            }

            long deadline = System.currentTimeMillis() + (long) (timeoutSec * 1000L);

            while (System.currentTimeMillis() < deadline) {
                // Success when queen slot becomes empty (queen died)
                ItemStack cur = getStackInSlot(SLOT_QUEEN);
                if (cur == null || cur.isEmpty()) return new Object[]{ true };

                ApiaryModifiers m2 = tile.getModifiers();
                if (m2 != null && m2.isAutomated) return new Object[]{ false, "automation upgrade should not be used" };

                IErrorLogic err = tile.getErrorLogic();
                if (err != null && err.hasErrors()) {
                    // Surface first error for better diagnostics
                    for (forestry.api.core.IErrorState st : err.getErrorStates()) {
                        if (st != null && st.getUniqueName() != null) { return new Object[]{ false, st.getUniqueName()}; }
                    }

                    return new Object[]{ false, "apiary error" };
                }

                ctx.pause(waitStepSeconds);
            }

            return new Object[]{ false, "timeout" };
        }

        @Callback(doc = "function():table -- Returns effective modifiers from upgrades: {production, lifespan, territory, mutation, flowering, geneticDecay, isSealed, isSelfLighted, isSunlightSimulated, isAutomated, isCollectingPollen, energy, temperature, humidity}")
        public Object[] getModifiers(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            ApiaryModifiers m = tile.getModifiers();
            if (m != null) {
                out.put("production", m.production);
                out.put("lifespan", m.lifespan);
                out.put("territory", m.territory);
                out.put("mutation", m.mutation);
                out.put("flowering", m.flowering);
                out.put("geneticDecay", m.geneticDecay);
                out.put("isSealed", m.isSealed);
                out.put("isSelfLighted", m.isSelfLighted);
                out.put("isSunlightSimulated", m.isSunlightSimulated);
                out.put("isAutomated", m.isAutomated);
                out.put("isCollectingPollen", m.isCollectingPollen);
                out.put("energy", m.energy);
                out.put("temperature", m.temperature);
                out.put("humidity", m.humidity);
            }

            return new Object[]{ out };
        }

        @Callback(doc = "function():table -- Returns environment info: { temperature:string, humidity:string }.")
        public Object[] getEnvironment(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            Object temp = tile.getTemperature();
            if (temp != null) out.put("temperature", temp.toString());

            Object hum = tile.getHumidity();
            if (hum != null) out.put("humidity", hum.toString());

            return new Object[]{ out };
        }

        @Callback(doc = "function():table -- Returns error info: { hasErrors:boolean, errors:string[] } from Forestry/Gendustry error logic.")
        public Object[] getErrors(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            IErrorLogic logic = tile.getErrorLogic();
            out.put("hasErrors", logic != null && logic.hasErrors());

            List<String> names = new ArrayList<>();
            if (logic != null) {
                for (forestry.api.core.IErrorState st : logic.getErrorStates()) {
                    if (st != null && st.getUniqueName() != null) names.add(st.getUniqueName());
                }
            }

            out.put("errors", names.toArray(new Object[0]));

            return new Object[]{ out };
        }
    }
}
