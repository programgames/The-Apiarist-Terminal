package net.ocgendustry.driver;

import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import net.bdew.gendustry.apiimpl.TileWorker;
import net.bdew.lib.power.TileBaseProcessor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Base driver for the Gendustry processing machines.
 *
 * Every Gendustry machine except the Industrial Apiary extends bdew's TileBaseProcessor, which
 * gives them the same shape: a progress value, a working flag, an energy buffer and a sided
 * inventory. This class carries the two things that differ per machine — the tile class it binds
 * to and the OpenComputers component name — so a concrete driver only has to describe its slots
 * and tanks.
 *
 * Each Gendustry tile class is a sibling of the others (they all extend TileItemProcessor or
 * TileBaseProcessor directly, never each other), so binding on the exact class is enough to keep
 * one driver from stealing another machine.
 *
 * @param <T> the Gendustry tile this driver binds to
 */
public abstract class MachineDriver<T extends TileBaseProcessor & TileWorker> extends DriverSidedTileEntity {
    private final Class<T> tileClass;
    private final String componentName;

    protected MachineDriver(Class<T> tileClass, String componentName) {
        this.tileClass = tileClass;
        this.componentName = componentName;
    }

    /** The OpenComputers component name this driver exposes, e.g. {@code genetic_sampler}. */
    public final String componentName() {
        return componentName;
    }

    @Override
    public final Class<?> getTileEntityClass() {
        return tileClass;
    }

    @Override
    public final boolean worksWith(World world, BlockPos pos, EnumFacing side) {
        return tileClass.isInstance(world.getTileEntity(pos));
    }

    @Override
    public final ManagedEnvironment createEnvironment(World world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (!tileClass.isInstance(te)) return null;

        return createEnvironment(tileClass.cast(te));
    }

    /** Builds the component exposed for one machine instance. */
    protected abstract MachineEnvironment<T> createEnvironment(T tile);
}
