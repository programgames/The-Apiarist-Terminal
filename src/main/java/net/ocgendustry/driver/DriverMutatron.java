package net.ocgendustry.driver;

import net.bdew.gendustry.machines.mutatron.TileMutatron;
import net.minecraftforge.fluids.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutatron (the basic one): mutates two parents into a new individual, consuming mutagen and
 * labware. Unlike the Advanced Mutatron it has no mutation to pick, so no selection callback.
 */
public final class DriverMutatron extends MachineDriver<TileMutatron> {
    public static final String COMPONENT = "mutatron";

    public DriverMutatron() {
        super(TileMutatron.class, COMPONENT);
    }

    @Override
    protected MachineEnvironment<TileMutatron> createEnvironment(TileMutatron tile) {
        return new Environment(tile);
    }

    public static final class Environment extends ItemMachineEnvironment<TileMutatron> {
        public Environment(TileMutatron tile) {
            super(tile, COMPONENT);
        }

        @Override
        protected Map<String, Integer> namedSlots() {
            LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

            slots.put("inIndividual1", tile.slots().inIndividual1());
            slots.put("inIndividual2", tile.slots().inIndividual2());
            slots.put("inLabware", tile.slots().inLabware());
            slots.put("outIndividual", tile.slots().outIndividual());

            return slots;
        }

        @Override
        protected int[] outputSlots() {
            return new int[]{ tile.slots().outIndividual() };
        }

        @Override
        protected Map<String, FluidTank> tanks() {
            LinkedHashMap<String, FluidTank> tanks = new LinkedHashMap<>();
            tanks.put("input", tile.tank());

            return tanks;
        }

        @Override
        protected boolean machineCanStart() {
            return tile.canStart();
        }
    }
}
