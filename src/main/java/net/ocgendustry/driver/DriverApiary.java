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
import net.bdew.gendustry.gui.rscontrol.RSMode;
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
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IChromosomeType;
import forestry.api.genetics.ISpeciesRoot;
import forestry.api.genetics.AlleleManager;
import net.ocgendustry.Config;
import net.ocgendustry.util.SignalState;
import net.ocgendustry.util.Tuning;

import java.util.ArrayList;
import java.util.Locale;
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
            // Signal bookkeeping, shared with the other drivers and unit-tested in SignalStateTest.
        // Primed in the constructor: a default the machine never held would make the first tick
        // raise a signal that describes nothing.
        private final SignalState signals;

        // Per-device toggle in addition to global Config.enableEvents
        private boolean eventsEnabled = true;

        // Slot layout from Gendustry TileApiary.scala (mc1.12):
        // 0: queen/princess, 1: drone, 2-5: upgrades, 6-14: output
        // Slot indices come from the machine, never from constants: Gendustry names them, so a
        // reordering on its side cannot leave this driver reading the wrong slot. The eight
        // processing machines were written this way from the start; these are now aligned.
        private int slotQueen() { return tile.slots().queen(); }
        private int slotDrone() { return tile.slots().drone(); }

        private int[] slotsUpgrades() { return range(tile.slots().upgrades()); }
        private int[] slotsOutput()   { return range(tile.slots().output()); }

        private static int[] range(scala.collection.immutable.Range.Inclusive r) {
            int[] out = new int[r.length()];
            for (int i = 0; i < out.length; i++) out[i] = r.apply(i);

            return out;
        }

        public Environment(TileApiary tile) {
            this.tile = tile;
            setNode(Network.newNode(this, Visibility.Network)
                .withComponent(componentName, Visibility.Network)
                .create());

            signals = new SignalState(
                Tuning.clampSignalInterval(Config.apiarySignalInterval, Config.apiarySignalIntervalMax),
                currentlyWorking(),
                signatureOutputs());

            eventsEnabled = Config.apiaryDefaultEventsEnabled;
        }

        /** The apiary reports progress rather than a flag; a cycle is between 0 and 100 percent. */
        private boolean currentlyWorking() {
            IBeekeepingLogic logic = tile.getBeekeepingLogic();
            if (logic == null) return false;

            float pct = logic.getBeeProgressPercent();

            return pct > 0f && pct < 100f;
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

            switch (signals.sample(currentlyWorking())) {
                case STARTED:
                    if (node() != null) node().sendToReachable("computer.signal", new Object[]{"apiary_started"});
                    break;
                case FINISHED:
                    if (node() != null) node().sendToReachable("computer.signal", new Object[]{"apiary_finished"});
                    break;
                default:
                    break;
            }

            // Walking the nine output slots is the expensive half, so that one is throttled.
            if (!signals.dueForOutputScan()) return;

            if (signals.outputChanged(signatureOutputs())) {
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
            for (int slot : slotsOutput()) {
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

        @Callback(doc = "function():table -- Returns slot groups for generic item transfer: { queen:number, drone:number, bees:number[], upgrades:number[], outputs:number[], size:number }")
        public Object[] listSlots(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> slots = new LinkedHashMap<>();

            slots.put("queen", slotQueen());
            slots.put("drone", slotDrone());
            slots.put("bees", new Object[]{slotQueen(), slotDrone()});
            slots.put("upgrades", toArray(slotsUpgrades()));
            slots.put("outputs", toArray(slotsOutput()));

            // Every listSlots reports size, including the two hand-written drivers: a script that
            // drives any machine through the generic inventory calls iterates over it, and getting
            // nil back from the two most interesting components makes one loop into three.
            slots.put("size", tile.getSizeInventory());

            return new Object[]{slots};
        }

        @Callback(doc = "function():table -- Returns shallow info about the bee slots: { queen:table?, drone:table? } with fields {name,label?,nbt?,count}.")
        public Object[] getBees(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            ItemStack queen = getStackInSlot(slotQueen());
            if (queen != null && !queen.isEmpty()) {
                LinkedHashMap<String, Object> q = new LinkedHashMap<>();
                putStackInfo(q, queen);
                out.put("queen", q);
            }

            ItemStack drone = getStackInSlot(slotDrone());
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

            for (int slot : slotsUpgrades()) {
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

            for (int slot : slotsOutput()) {
                ItemStack stack = getStackInSlot(slot);
                if (stack == null || stack.isEmpty()) continue;

                LinkedHashMap<String, Object> info = new LinkedHashMap<>();
                putStackInfo(info, stack);
                info.put("slot", slot);
                arr.add(info);
            }

            return new Object[]{ arr.toArray() };
        }

        @Callback(doc = "function():boolean -- Returns true while a bee cycle is in progress.")
        public Object[] isWorking(Context ctx, Arguments args) {
            return new Object[]{ currentlyWorking() };
        }

        @Callback(doc = "function():table -- Returns the energy buffer: { stored:number, capacity:number }.")
        public Object[] getEnergy(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            out.put("stored", tile.power().stored());
            out.put("capacity", tile.power().capacity());

            return new Object[]{ out };
        }

        @Callback(doc = "function():number -- Returns current work progress from Forestry logic as a 0..1 fraction.")
        public Object[] getProgress(Context ctx, Arguments args) {
            IBeekeepingLogic logic = tile.getBeekeepingLogic();

            return new Object[]{ (logic != null) ? (logic.getBeeProgressPercent() / 100.0f) : 0.0f };
        }

        @Callback(doc = "function(ticks:number):boolean -- Set how often signals are emitted (every N ticks, min 1). Lower = more responsive, higher = less overhead.")
        public Object[] setSignalInterval(Context ctx, Arguments args) {
            signals.setSignalInterval(Tuning.clampSignalInterval(args.checkInteger(0), Config.apiarySignalIntervalMax));

            return new Object[]{ true };
        }

        @Callback(doc = "function():boolean -- Reload defaults from the mod config and apply to this component instance.")
        public Object[] applyDefaultTuning(Context ctx, Arguments args) {
            Config.syncFromFile();

            signals.setSignalInterval(
                Tuning.clampSignalInterval(Config.apiarySignalInterval, Config.apiarySignalIntervalMax));
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
            ItemStack slot0 = getStackInSlot(slotQueen());
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

        @Callback(doc = "function(slot:string):table|boolean,string? -- Returns the genome of the bee actually in that slot -- \"queen\" or \"drone\" -- as { bee, chromosomes, mate? }. chromosomes is keyed by the chromosome names getSpeciesTemplate uses, each { active, inactive, pure }: a bee carries two alleles per chromosome and only the active one is expressed, so `pure` says whether both sides agree and the trait breeds true. bee is { type, analyzed, natural, generation, mated }, and a mated queen also answers mate, the drone's genome. Returns false plus a reason when the slot is empty, holds no bee, or holds an unanalysed one while industrial_apiary.requireAnalyzedBees is on.")
        public Object[] getGenome(Context ctx, Arguments args) {
            String which = args.checkString(0).toLowerCase(Locale.ROOT);

            int slot;
            if ("queen".equals(which)) slot = slotQueen();
            else if ("drone".equals(which)) slot = slotDrone();
            else return new Object[]{ false, "unknown slot: " + which + " (expected queen or drone)" };

            ItemStack stack = getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) return new Object[]{ false, "the " + which + " slot is empty" };

            ISpeciesRoot root = AlleleManager.alleleRegistry.getSpeciesRoot("rootBees");
            if (!(root instanceof IBeeRoot)) return new Object[]{ false, "bee root not available" };
            IBeeRoot bees = (IBeeRoot) root;

            // isMember first, always. Reaching into Forestry's genetic items without genome NBT
            // floods the log, which is the trap listSlots' neighbours already have to respect.
            if (!bees.isMember(stack)) return new Object[]{ false, "the " + which + " slot holds no bee" };

            IBee bee = bees.getMember(stack);
            if (bee == null) return new Object[]{ false, "could not read the bee in the " + which + " slot" };

            // Forestry keeps the whole genome in NBT whether or not the bee has been analysed --
            // the flag only decides what the tooltip shows. Reading it regardless would quietly
            // remove the Beealyzer's reason to exist, so that is the server's call, not ours.
            if (Config.apiaryRequireAnalyzedBees && !bee.isAnalyzed()) {
                return new Object[]{ false, "this bee has not been analysed -- run it through a "
                    + "Beealyzer, or turn off industrial_apiary.requireAnalyzedBees" };
            }

            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("bee", beeInfo(bee, bees.getType(stack)));
            out.put("chromosomes", genomeInfo(root, bee.getGenome()));

            // A princess or a drone has no mate; a mated queen carries the drone's genome too, and
            // that is half of what the next generation will be made of.
            IBeeGenome mate = bee.getMate();
            if (mate != null) out.put("mate", genomeInfo(root, mate));

            return new Object[]{ out };
        }

        private static LinkedHashMap<String, Object> beeInfo(IBee bee, EnumBeeType type) {
            LinkedHashMap<String, Object> info = new LinkedHashMap<>();

            info.put("type", type != null ? type.getName() : "unknown");
            info.put("analyzed", bee.isAnalyzed());
            info.put("natural", bee.isNatural());
            info.put("generation", bee.getGeneration());
            info.put("mated", bee.getMate() != null);

            return info;
        }

        /**
         * One entry per chromosome, walked through the karyotype for the same reason
         * getSpeciesTemplate walks it: it names each chromosome, so the answer stays correct if
         * Forestry reorders them, and both callbacks come back keyed the same way.
         */
        private static LinkedHashMap<String, Object> genomeInfo(ISpeciesRoot root, IGenome genome) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            for (IChromosomeType type : root.getKaryotype()) {
                IAllele active = genome.getActiveAllele(type);
                IAllele inactive = genome.getInactiveAllele(type);
                if (active == null && inactive == null) continue;

                LinkedHashMap<String, Object> pair = new LinkedHashMap<>();
                if (active != null) pair.put("active", alleleInfo(active));
                if (inactive != null) pair.put("inactive", alleleInfo(inactive));
                // Saying it here spares every caller the uid comparison, and it is the one fact
                // that decides whether a trait survives the next cross.
                pair.put("pure", active != null && inactive != null
                    && active.getUID().equals(inactive.getUID()));

                out.put(type.getName(), pair);
            }

            return out;
        }

        @Callback(doc = "function(species:string):table|boolean,string? -- Returns the default genome template of a bee species, keyed by the chromosome name Forestry itself uses, lower_snake_case (species, speed, lifespan, fertility, temperature_tolerance, never_sleeps, humidity_tolerance, tolerates_rain, cave_dwelling, flower_provider, flowering, territory, effect), each { uid, name, dominant }. The species is accepted as an allele UID, an allele name or a display name. Returns false plus a reason when it is unknown or carries no template.")
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

        @Callback(doc = "function():table -- Returns the redstone control state: { mode:string, canWork:boolean }. mode is one of ALWAYS, NEVER, RS_ON, RS_OFF -- the same four the machine's GUI button cycles through; canWork says whether the machine is allowed to run right now under that mode.")
        public Object[] getRedstoneMode(Context ctx, Arguments args) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();

            out.put("mode", String.valueOf(tile.rsmode().value()));
            out.put("canWork", tile.canWork());

            return new Object[]{ out };
        }

        @Callback(doc = "function(mode:string):boolean,string? -- Sets the redstone control mode to ALWAYS, NEVER, RS_ON or RS_OFF; returns false plus the accepted values when given anything else. This is the one callback in the mod that changes a machine rather than reading it: it is how a script stops and restarts an apiary.")
        public Object[] setRedstoneMode(Context ctx, Arguments args) {
            String wanted = args.checkString(0).toUpperCase(java.util.Locale.ROOT);

            try {
                // withName throws NoSuchElementException on an unknown name; turning that into a
                // false plus a reason keeps the callback contract of never throwing at a script.
                tile.rsmode().$colon$eq(RSMode.withName(wanted));
            } catch (RuntimeException unknown) {
                return new Object[]{ false, "unknown mode: " + wanted + " (expected ALWAYS, NEVER, RS_ON or RS_OFF)" };
            }

            return new Object[]{ true };
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
