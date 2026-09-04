package net.ocgendustry.driver;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.bdew.gendustry.apiimpl.TileWorker;
import net.bdew.lib.power.TileBaseProcessor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.ocgendustry.Config;
import net.ocgendustry.util.Stacks;
import net.ocgendustry.util.Tuning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Component shared by every Gendustry processing machine.
 *
 * Exposes what the machines have in common: progress, working state, energy buffer, tanks, slot
 * layout, output contents, the started/finished/output signals and the usual tuning callbacks.
 * A concrete driver describes its machine by overriding {@link #namedSlots()}, and, when it has
 * them, {@link #outputSlots()} and {@link #tanks()}.
 *
 * Writes are intentionally left out: items go in and out through OpenComputers' generic inventory
 * transfer (transposer / inventory controller) using the indices returned by listSlots().
 *
 * @param <T> the Gendustry tile this component wraps
 */
public abstract class MachineEnvironment<T extends TileBaseProcessor & TileWorker> extends AbstractManagedEnvironment implements NamedBlock {
    /** Machines that only output fluid have no output item slot to watch. */
    protected static final int[] NO_SLOTS = new int[0];

    protected final T tile;

    private final String componentName;

    private boolean lastWorking = false;
    private String lastOutputSignature = "";
    private int tickCounter = 0;

    private int signalInterval;
    private double waitStepSeconds;

    // Per-device toggle, in addition to the global Config.enableEvents
    private boolean eventsEnabled;

    protected MachineEnvironment(T tile, String componentName) {
        this.tile = tile;
        this.componentName = componentName;

        setNode(Network.newNode(this, Visibility.Network)
            .withComponent(componentName, Visibility.Network)
            .create());

        applyDefaults();
    }

    // ---- Machine description (filled in by the concrete drivers) ----

    /**
     * Item slots of this machine, keyed by the name Gendustry itself uses (inTemplate, outCopy...).
     * Empty for machines that have no named slot, such as the Mutagen Producer.
     */
    protected abstract Map<String, Integer> namedSlots();

    /** Output item slots watched for the {@code <component>_output} signal. */
    protected int[] outputSlots() {
        return NO_SLOTS;
    }

    /** Tanks of this machine, keyed by role (input, output, dna, protein). */
    protected Map<String, FluidTank> tanks() {
        return Collections.emptyMap();
    }

    // ---- OpenComputers plumbing ----

    @Override
    public String preferredName() {
        return componentName;
    }

    @Override
    public int priority() {
        return 10; // above the generic energy/inventory drivers
    }

    @Override
    public boolean canUpdate() {
        return Config.enableEvents && eventsEnabled;
    }

    @Override
    public void update() {
        if (!Config.enableEvents || !eventsEnabled) return;

        tickCounter++;
        if (signalInterval > 1 && (tickCounter % signalInterval) != 0) return;

        boolean working = tile.isWorking();
        if (working && !lastWorking) sendSignal(componentName + "_started");
        if (!working && lastWorking) sendSignal(componentName + "_finished");
        lastWorking = working;

        // Machines that only produce fluid have no output slot, so they never raise this signal:
        // tank levels change on nearly every tick and would turn the event into noise.
        int[] outputs = outputSlots();
        if (outputs.length == 0) return;

        String signature = outputSignature(outputs);
        if (!signature.equals(lastOutputSignature)) {
            lastOutputSignature = signature;
            sendSignal(componentName + "_output");
        }
    }

    private void sendSignal(String name) {
        if (node() != null) node().sendToReachable("computer.signal", new Object[]{name});
    }

    private String outputSignature(int[] outputs) {
        StringBuilder sb = new StringBuilder();
        for (int slot : outputs) {
            sb.append(Stacks.signature(tile.getStackInSlot(slot))).append('|');
        }

        return sb.toString();
    }

    private void applyDefaults() {
        signalInterval = Tuning.clampSignalInterval(Config.processorSignalInterval, Config.processorSignalIntervalMax);
        waitStepSeconds = Tuning.clampWaitStep(Config.processorWaitInterval);
        eventsEnabled = Config.processorDefaultEventsEnabled;
    }

    // ---- State ----

    @Callback(doc = "function():number -- Returns current work progress (0..1).")
    public Object[] getProgress(Context ctx, Arguments args) {
        return new Object[]{ tile.getProgress() };
    }

    @Callback(doc = "function():boolean -- Returns true while the machine is processing.")
    public Object[] isWorking(Context ctx, Arguments args) {
        return new Object[]{ tile.isWorking() };
    }

    @Callback(doc = "function():boolean -- Try to start processing immediately; returns true if started by this call specifically, false otherwise.")
    public Object[] start(Context ctx, Arguments args) {
        return new Object[]{ tile.tryStart() };
    }

    @Callback(doc = "function():table -- Returns the energy buffer: { stored:number, capacity:number }.")
    public Object[] getEnergy(Context ctx, Arguments args) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();

        out.put("stored", tile.power().stored());
        out.put("capacity", tile.power().capacity());

        return new Object[]{ out };
    }

    @Callback(doc = "function():table -- Returns slot indices for generic item transfer: the machine's named slots, plus { outputs:number[], size:number }.")
    public Object[] listSlots(Context ctx, Arguments args) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>(namedSlots());

        int[] outputs = outputSlots();
        Object[] boxed = new Object[outputs.length];
        for (int i = 0; i < outputs.length; i++) boxed[i] = outputs[i];

        out.put("outputs", boxed);
        out.put("size", tile.getSizeInventory());

        return new Object[]{ out };
    }

    @Callback(doc = "function():table -- Lists the tanks as an array of { name:string, amount:number, capacity:number, fluid?:string }.")
    public Object[] listTanks(Context ctx, Arguments args) {
        List<Object> arr = new ArrayList<>();

        for (Map.Entry<String, FluidTank> e : tanks().entrySet()) {
            FluidTank tank = e.getValue();
            if (tank == null) continue;

            LinkedHashMap<String, Object> info = new LinkedHashMap<>();
            info.put("name", e.getKey());
            info.put("capacity", tank.getCapacity());

            FluidStack contents = tank.getFluid();
            info.put("amount", contents != null ? contents.amount : 0);
            if (contents != null && contents.getFluid() != null && contents.getFluid().getName() != null) {
                info.put("fluid", contents.getFluid().getName());
            }

            arr.add(info);
        }

        return new Object[]{ arr.toArray() };
    }

    @Callback(doc = "function():table -- Lists non-empty output slots as an array of { name, label?, nbt?, count, slot }.")
    public Object[] listOutputs(Context ctx, Arguments args) {
        List<Object> arr = new ArrayList<>();

        for (int slot : outputSlots()) {
            ItemStack stack = tile.getStackInSlot(slot);
            if (Stacks.isEmpty(stack)) continue;

            LinkedHashMap<String, Object> info = Stacks.info(stack);
            info.put("slot", slot);
            arr.add(info);
        }

        return new Object[]{ arr.toArray() };
    }

    @Callback(doc = "function([timeout:number=60]):boolean,string? -- Wait without freezing until the machine stops working, then return true; returns false,\"timeout\" if it is still working when the timeout elapses.")
    public Object[] waitForFinish(Context ctx, Arguments args) {
        double timeoutSec = args.count() > 0 ? Math.max(0, args.checkDouble(0)) : 60.0;
        long deadline = System.currentTimeMillis() + (long) (timeoutSec * 1000L);

        while (tile.isWorking()) {
            if (System.currentTimeMillis() > deadline) return new Object[]{ false, "timeout" };

            ctx.pause(waitStepSeconds);
        }

        return new Object[]{ true };
    }

    // ---- Event controls ----

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

    // ---- Tuning ----

    @Callback(doc = "function(ticks:number):boolean -- Set how often signals are emitted (every N ticks, min 1). Lower = more responsive, higher = less overhead.")
    public Object[] setSignalInterval(Context ctx, Arguments args) {
        signalInterval = Tuning.clampSignalInterval(args.checkInteger(0), Config.processorSignalIntervalMax);

        return new Object[]{ true };
    }

    @Callback(doc = "function(seconds:number):boolean -- Set the cooperative wait step used by blocking operations (default 0.2s, range 0.05..5). Lower = more responsive, higher = less overhead.")
    public Object[] setWaitInterval(Context ctx, Arguments args) {
        waitStepSeconds = Tuning.clampWaitStep(args.checkDouble(0));

        return new Object[]{ true };
    }

    @Callback(doc = "function():boolean -- Reload defaults from the mod config and apply to this component instance.")
    public Object[] applyDefaultTuning(Context ctx, Arguments args) {
        Config.syncFromFile();
        applyDefaults();

        return new Object[]{ true };
    }
}
