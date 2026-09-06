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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one component class shared by every Gendustry processing machine.
 *
 * Exposes what the machines have in common: progress, working state, energy buffer, tanks, slot
 * layout, output contents, the started/finished/output signals and the usual tuning callbacks.
 * What differs from machine to machine arrives as a {@link MachineSpec}.
 *
 * <p><b>This class is final on purpose, and every {@code @Callback} must stay in it.</b> When more
 * than one driver attaches to a block — which is always the case here, since these tiles also
 * expose a Forge Energy capability that OpenComputers' own generic driver binds to — OpenComputers
 * builds a CompoundBlockEnvironment and picks the environment that owns a callback with
 * {@code environment.getClass().equals(method.getDeclaringClass())}. That is an exact class
 * identity test, so a callback declared on a superclass is listed by {@code component.methods()}
 * yet fails every call with "no such method". Subclassing this to add a callback silently breaks
 * the whole component; describe the machine in its {@link MachineSpec} instead.
 *
 * <p>Writes are intentionally left out: items go in and out through OpenComputers' generic
 * inventory transfer (transposer / inventory controller) using the indices from listSlots().
 *
 * @param <T> the Gendustry tile this component wraps
 */
public final class MachineEnvironment<T extends TileBaseProcessor & TileWorker>
        extends AbstractManagedEnvironment implements NamedBlock {

    private final T tile;
    private final MachineSpec<T> spec;

    private boolean lastWorking = false;
    private String lastOutputSignature = "";
    private int tickCounter = 0;

    private int signalInterval;
    private double waitStepSeconds;

    // Per-device toggle, in addition to the global Config.enableEvents
    private boolean eventsEnabled;

    MachineEnvironment(T tile, MachineSpec<T> spec) {
        this.tile = tile;
        this.spec = spec;

        setNode(Network.newNode(this, Visibility.Network)
            .withComponent(spec.componentName(), Visibility.Network)
            .create());

        applyDefaults();
    }

    // ---- OpenComputers plumbing ----

    @Override
    public String preferredName() {
        return spec.componentName();
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

        String component = spec.componentName();

        boolean working = tile.isWorking();
        if (working && !lastWorking) sendSignal(component + "_started");
        if (!working && lastWorking) sendSignal(component + "_finished");
        lastWorking = working;

        // Machines that only produce fluid have no output slot, so they never raise this signal:
        // tank levels change on nearly every tick and would turn the event into noise.
        int[] outputs = spec.outputSlots(tile);
        if (outputs.length == 0) return;

        String signature = outputSignature(outputs);
        if (!signature.equals(lastOutputSignature)) {
            lastOutputSignature = signature;
            sendSignal(component + "_output");
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

    @Callback(doc = "function():boolean -- Try to start processing immediately; returns true only if this call is what started it. The machine auto-starts on its own tick as soon as it has inputs and energy, so false is the normal answer and not an error.")
    public Object[] start(Context ctx, Arguments args) {
        return new Object[]{ tile.tryStart() };
    }

    @Callback(doc = "function():boolean,string? -- Returns true if all conditions to start are currently satisfied (required slots filled, output free, enough energy); false plus a reason on the machines that only fill a tank, for which Gendustry declares no pre-flight check.")
    public Object[] canStart(Context ctx, Arguments args) {
        if (!spec.hasCanStart()) return new Object[]{ false, "not supported by this machine" };

        return new Object[]{ spec.canStart(tile) };
    }

    @Callback(doc = "function():boolean,string? -- Checks the pair currently loaded: true when it can be processed, false plus a reason otherwise; false plus \"not supported by this machine\" where Gendustry offers no such check.")
    public Object[] isValidInputs(Context ctx, Arguments args) {
        if (!spec.hasInputCheck()) return new Object[]{ false, "not supported by this machine" };

        String problem = spec.inputProblem(tile);
        if (problem != null) return new Object[]{ false, problem };

        return new Object[]{ true };
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
        LinkedHashMap<String, Object> out = new LinkedHashMap<>(spec.namedSlots(tile));

        int[] outputs = spec.outputSlots(tile);
        Object[] boxed = new Object[outputs.length];
        for (int i = 0; i < outputs.length; i++) boxed[i] = outputs[i];

        out.put("outputs", boxed);
        out.put("size", tile.getSizeInventory());

        return new Object[]{ out };
    }

    @Callback(doc = "function():table -- Lists the tanks as an array of { name:string, amount:number, capacity:number, fluid?:string }.")
    public Object[] listTanks(Context ctx, Arguments args) {
        List<Object> arr = new ArrayList<>();

        for (Map.Entry<String, FluidTank> e : spec.tanks(tile).entrySet()) {
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

        for (int slot : spec.outputSlots(tile)) {
            ItemStack stack = tile.getStackInSlot(slot);
            if (Stacks.isEmpty(stack)) continue;

            LinkedHashMap<String, Object> info = Stacks.info(stack);
            info.put("slot", slot);
            arr.add(info);
        }

        return new Object[]{ arr.toArray() };
    }

    @Callback(doc = "function([timeout:number=60]):boolean,string? -- Wait without freezing until the machine stops working, then return true; returns false,\"timeout\" if it is still working when the timeout elapses. Returns true immediately if the machine is not working, so check isWorking() first.")
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
