package net.ocgendustry.driver;

import net.bdew.gendustry.machines.replicator.TileReplicator;
import net.minecraftforge.fluids.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Genetic Replicator: builds a brand new individual from a full template, consuming liquid DNA
 * and protein. It is the only Gendustry machine with two tanks.
 */
public final class DriverReplicator extends MachineDriver<TileReplicator> {
    public static final String COMPONENT = "genetic_replicator";

    public DriverReplicator() {
        super(TileReplicator.class, COMPONENT);
    }

    @Override
    protected MachineEnvironment<TileReplicator> createEnvironment(TileReplicator tile) {
        return new Environment(tile);
    }

    public static final class Environment extends ItemMachineEnvironment<TileReplicator> {
        public Environment(TileReplicator tile) {
            super(tile, COMPONENT);
        }

        @Override
        protected Map<String, Integer> namedSlots() {
            LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

            slots.put("inTemplate", tile.slots().inTemplate());
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

            tanks.put("dna", tile.dnaTank());
            tanks.put("protein", tile.proteinTank());

            return tanks;
        }

        @Override
        protected boolean machineCanStart() {
            return tile.canStart();
        }
    }
}
