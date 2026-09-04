package net.ocgendustry.driver;

import net.bdew.gendustry.machines.extractor.TileExtractor;
import net.minecraftforge.fluids.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DNA Extractor: dissolves an individual into liquid DNA, consuming labware. The result is a
 * fluid, so this component has no output slot and never raises the output signal.
 */
public final class DriverExtractor extends MachineDriver<TileExtractor> {
    public static final String COMPONENT = "dna_extractor";

    public DriverExtractor() {
        super(TileExtractor.class, COMPONENT);
    }

    @Override
    protected MachineEnvironment<TileExtractor> createEnvironment(TileExtractor tile) {
        return new Environment(tile);
    }

    public static final class Environment extends MachineEnvironment<TileExtractor> {
        public Environment(TileExtractor tile) {
            super(tile, COMPONENT);
        }

        @Override
        protected Map<String, Integer> namedSlots() {
            LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

            slots.put("inIndividual", tile.slots().inIndividual());
            slots.put("inLabware", tile.slots().inLabware());

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
