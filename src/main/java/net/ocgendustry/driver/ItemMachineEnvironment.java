package net.ocgendustry.driver;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import net.bdew.gendustry.apiimpl.TileWorker;
import net.bdew.lib.power.TileBaseProcessor;

/**
 * Component for the machines that expose a pre-flight check.
 *
 * Gendustry declares canStart() on each machine that turns items into items, but not on the ones
 * that only fill a tank (DNA Extractor, Protein Liquifier, Mutagen Producer) and there is no shared
 * interface for it. Those machines therefore do not get the callback at all, rather than getting
 * one that always answers nil.
 *
 * @param <T> the Gendustry tile this component wraps
 */
public abstract class ItemMachineEnvironment<T extends TileBaseProcessor & TileWorker> extends MachineEnvironment<T> {
    protected ItemMachineEnvironment(T tile, String componentName) {
        super(tile, componentName);
    }

    /** Calls the machine's own canStart(); each tile declares it separately. */
    protected abstract boolean machineCanStart();

    @Callback(doc = "function():boolean -- Returns true if all conditions to start are currently satisfied (required slots filled, output free, enough energy).")
    public Object[] canStart(Context ctx, Arguments args) {
        return new Object[]{ machineCanStart() };
    }
}
