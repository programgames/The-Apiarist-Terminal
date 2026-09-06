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
import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IChromosomeType;
import forestry.api.genetics.ISpeciesRoot;
import forestry.api.genetics.AlleleManager;
import net.ocgendustry.Config;
import net.ocgendustry.util.Tuning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

            signalInterval = Tuning.clampSignalInterval(Config.apiarySignalInterval, Config.apiarySignalIntervalMax);
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
            if (!Config.enableEvents || !eventsEnabled) return;

            // Read the progress every tick: a cycle shorter than signalInterval would otherwise
            // start and finish between two samples and raise neither signal. Only the output
            // scan below is throttled.
            boolean working = false;
            IBeekeepingLogic logic = tile.getBeekeepingLogic();
            if (logic != null) {
                float pct = logic.getBeeProgressPercent();
                working = pct > 0f && pct < 100f;
            }

            if (working && !lastWorking && node() != null) node().sendToReachable("computer.signal", new Object[]{"apiary_started"});
            if (!working && lastWorking && node() != null) node().sendToReachable("computer.signal", new Object[]{"apiary_finished"});
            lastWorking = working;

            tickCounter++;
            if (signalInterval > 1 && (tickCounter % signalInterval) != 0) return;

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
            return new Object[]{ Config.enableEvents && eventsEnabled };
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

        @Callback(doc = "function(ticks:number):boolean -- Set how often signals are emitted (every N ticks, min 1). Lower = more responsive, higher = less overhead.")
        public Object[] setSignalInterval(Context ctx, Arguments args) {
            signalInterval = Tuning.clampSignalInterval(args.checkInteger(0), Config.apiarySignalIntervalMax);

            return new Object[]{ true };
        }

        @Callback(doc = "function():boolean -- Reload defaults from the mod config and apply to this component instance.")
        public Object[] applyDefaultTuning(Context ctx, Arguments args) {
            Config.syncFromFile();

            signalInterval = Tuning.clampSignalInterval(Config.apiarySignalInterval, Config.apiarySignalIntervalMax);
            eventsEnabled = Config.apiaryDefaultEventsEnabled;

            return new Object[]{ true };
        }

        @Callback(doc = "function():table -- Non-blocking status of the queen slot: { occupied:boolean, type:string, freed:boolean, automated:boolean, error?:string }. freed is true once the queen slot is empty, which is what a breeding cycle waits for; wait for it with event.pull(timeout, \"apiary_finished\") rather than blocking, then read this again.")
        public Object[] getPrincessStatus(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            ISpeciesRoot root = AlleleManager.alleleRegistry.getSpeciesRoot("rootBees");
            if (!(root instanceof IBeeRoot)) {
                out.put("occupied", false);
                out.put("type", "unknown");
                out.put("freed", false);
                out.put("automated", false);
                out.put("error", "bee root not available");

                return new Object[]{ out };
            }

            IBeeRoot beeRoot = (IBeeRoot) root;
            ItemStack slot0 = getStackInSlot(SLOT_QUEEN);
            boolean occupied = slot0 != null && !slot0.isEmpty();

            String type = "none";
            if (occupied) {
                if (beeRoot.isMember(slot0, EnumBeeType.QUEEN)) type = "queen";
                else if (beeRoot.isMember(slot0, EnumBeeType.PRINCESS)) type = "princess";
                else type = "other";
            }

            out.put("occupied", occupied);
            out.put("type", type);
            out.put("freed", !occupied);

            // An automation upgrade empties the queen slot by itself, so a script watching for a
            // freed slot would draw the wrong conclusion. Surface it rather than let it mislead.
            ApiaryModifiers mods = tile.getModifiers();
            out.put("automated", mods != null && mods.isAutomated);

            IErrorLogic err = tile.getErrorLogic();
            if (err != null && err.hasErrors()) {
                for (forestry.api.core.IErrorState st : err.getErrorStates()) {
                    if (st != null && st.getUniqueName() != null) {
                        out.put("error", st.getUniqueName());
                        break;
                    }
                }
                if (!out.containsKey("error")) out.put("error", "apiary error");
            }

            return new Object[]{ out };
        }

        /**
         * Resolves a bee species from a UID, an allele name or a display name.
         *
         * Walks Forestry's registry instead of building a UID by concatenation: species are
         * contributed by many mods (Magic Bees, Extra Bees, Career Bees...), each with its own
         * prefix, so "forestry.species" + name only ever finds the vanilla Forestry ones.
         */
        private static IAlleleBeeSpecies findSpecies(String wanted) {
            IAllele direct = AlleleManager.alleleRegistry.getAllele(wanted);
            if (direct instanceof IAlleleBeeSpecies) return (IAlleleBeeSpecies) direct;

            for (IAllele allele : AlleleManager.alleleRegistry.getRegisteredAlleles(EnumBeeChromosome.SPECIES)) {
                if (!(allele instanceof IAlleleBeeSpecies)) continue;

                IAlleleBeeSpecies species = (IAlleleBeeSpecies) allele;
                if (wanted.equalsIgnoreCase(species.getUID())
                    || wanted.equalsIgnoreCase(species.getAlleleName())
                    || wanted.equalsIgnoreCase(displayName(species))) {
                    return species;
                }
            }

            return null;
        }

        /** Display name of an allele, falling back to its UID: some modded alleles translate client-side only. */
        private static String displayName(IAllele allele) {
            try {
                String name = allele.getName();
                if (name != null && !name.isEmpty()) return name;
            } catch (RuntimeException ignored) {
                // Fall through to the UID, which is always available.
            }

            return allele.getUID();
        }

        private static LinkedHashMap<String, Object> alleleInfo(IAllele allele) {
            LinkedHashMap<String, Object> info = new LinkedHashMap<>();

            info.put("uid", allele.getUID());
            info.put("name", displayName(allele));
            info.put("dominant", allele.isDominant());

            return info;
        }

        @Callback(doc = "function(species:string):table|boolean,string? -- Returns the default genome template of a bee species, keyed by chromosome name (SPECIES, SPEED, LIFESPAN, FERTILITY, TEMPERATURE_TOLERANCE, NEVER_SLEEPS, HUMIDITY_TOLERANCE, TOLERATES_RAIN, CAVE_DWELLING, FLOWER_PROVIDER, FLOWERING, TERRITORY, EFFECT), each { uid, name, dominant }. The species is accepted as an allele UID, an allele name or a display name. Returns false plus a reason when it is unknown or carries no template.")
        public Object[] getSpeciesTemplate(Context ctx, Arguments args) {
            String wanted = args.checkString(0);

            ISpeciesRoot root = AlleleManager.alleleRegistry.getSpeciesRoot("rootBees");
            if (!(root instanceof IBeeRoot)) return new Object[]{ false, "bee root not available" };

            IAlleleBeeSpecies species = findSpecies(wanted);
            if (species == null) return new Object[]{ false, "unknown species: " + wanted };

            IAllele[] template = root.getTemplate(species.getUID());
            if (template == null) return new Object[]{ false, "no template registered for " + species.getUID() };

            // Walk the karyotype rather than the array: it names each chromosome and gives the
            // index to read, so the answer stays correct if Forestry ever reorders them.
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            for (IChromosomeType type : root.getKaryotype()) {
                int idx = type.ordinal();
                IAllele allele = (idx >= 0 && idx < template.length) ? template[idx] : null;
                if (allele == null) continue; // a template may leave a chromosome unset

                out.put(type.getName(), alleleInfo(allele));
            }

            return new Object[]{ out };
        }

        @Callback(doc = "function([filter:string]):table -- Lists every registered bee species as an array of { uid, name, dominant, hasTemplate }, read from Forestry's allele registry so species added by other mods are included. The optional filter keeps those whose uid or name contains it, case-insensitively.")
        public Object[] listSpeciesTemplates(Context ctx, Arguments args) {
            // optString rather than count() + checkString: a script that passes nil explicitly
            // still counts as one argument, and checkString would then refuse it.
            String raw = args.optString(0, null);
            String filter = (raw == null || raw.isEmpty()) ? null : raw.toLowerCase();

            ISpeciesRoot root = AlleleManager.alleleRegistry.getSpeciesRoot("rootBees");
            if (!(root instanceof IBeeRoot)) return new Object[]{ false, "bee root not available" };

            List<Object> arr = new ArrayList<>();
            for (IAllele allele : AlleleManager.alleleRegistry.getRegisteredAlleles(EnumBeeChromosome.SPECIES)) {
                if (!(allele instanceof IAlleleBeeSpecies)) continue;

                IAlleleBeeSpecies species = (IAlleleBeeSpecies) allele;
                String uid = species.getUID();
                String name = displayName(species);

                if (filter != null
                    && !uid.toLowerCase().contains(filter)
                    && !name.toLowerCase().contains(filter)) {
                    continue;
                }

                LinkedHashMap<String, Object> info = alleleInfo(species);
                info.put("hasTemplate", root.getTemplate(uid) != null);
                arr.add(info);
            }

            return new Object[]{ arr.toArray() };
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
