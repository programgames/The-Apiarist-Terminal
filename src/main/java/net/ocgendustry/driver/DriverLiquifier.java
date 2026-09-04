package net.ocgendustry.driver;

import net.bdew.gendustry.machines.liquifier.TileLiquifier;
import net.minecraftforge.fluids.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protein Liquifier: turns meat into liquid protein. The result is a fluid, so this component has
 * no output slot and never raises the output signal.
 */
public final class DriverLiquifier extends MachineDriver<TileLiquifier> {
    public static final String COMPONENT = "protein_liquifier";

    public DriverLiquifier() {
        super(TileLiquifier.class, COMPONENT);
    }

    @Override
    protected MachineEnvironment<TileLiquifier> createEnvironment(TileLiquifier tile) {
        return new Environment(tile);
    }

    public static final class Environment extends MachineEnvironment<TileLiquifier> {
        public Environment(TileLiquifier tile) {
            super(tile, COMPONENT);
        }

        @Override
        protected Map<String, Integer> namedSlots() {
            LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();
            slots.put("inMeat", tile.slots().inMeat());

            return slots;
        }

        @Override
        protected Map<String, FluidTank> tanks() {
            LinkedHashMap<String, FluidTank> tanks = new LinkedHashMap<>();
            tanks.put("output", tile.tank());

            return tanks;
        }
    }
}
