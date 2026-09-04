package net.ocgendustry.driver;

import net.bdew.gendustry.machines.imprinter.TileImprinter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Genetic Imprinter: writes the genes of a template onto an individual, consuming labware.
 * Note that the individual goes in and comes back out, hence the separate in/out slot names.
 */
public final class DriverImprinter extends MachineDriver<TileImprinter> {
    public static final String COMPONENT = "genetic_imprinter";

    public DriverImprinter() {
        super(TileImprinter.class, COMPONENT);
    }

    @Override
    protected MachineEnvironment<TileImprinter> createEnvironment(TileImprinter tile) {
        return new Environment(tile);
    }

    public static final class Environment extends ItemMachineEnvironment<TileImprinter> {
        public Environment(TileImprinter tile) {
            super(tile, COMPONENT);
        }

        @Override
        protected Map<String, Integer> namedSlots() {
            LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

            slots.put("inTemplate", tile.slots().inTemplate());
            slots.put("inIndividual", tile.slots().inIndividual());
            slots.put("inLabware", tile.slots().inLabware());
            slots.put("outIndividual", tile.slots().outIndividual());

            return slots;
        }

        @Override
        protected int[] outputSlots() {
            return new int[]{ tile.slots().outIndividual() };
        }

        @Override
        protected boolean machineCanStart() {
            return tile.canStart();
        }
    }
}
