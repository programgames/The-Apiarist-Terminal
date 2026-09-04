package net.ocgendustry.driver;

import net.bdew.gendustry.machines.sampler.TileSampler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Genetic Sampler: reads one gene of an individual into a blank gene sample, consuming labware.
 */
public final class DriverSampler extends MachineDriver<TileSampler> {
    public static final String COMPONENT = "genetic_sampler";

    public DriverSampler() {
        super(TileSampler.class, COMPONENT);
    }

    @Override
    protected MachineEnvironment<TileSampler> createEnvironment(TileSampler tile) {
        return new Environment(tile);
    }

    public static final class Environment extends ItemMachineEnvironment<TileSampler> {
        public Environment(TileSampler tile) {
            super(tile, COMPONENT);
        }

        @Override
        protected Map<String, Integer> namedSlots() {
            LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

            slots.put("inIndividual", tile.slots().inIndividual());
            slots.put("inSampleBlank", tile.slots().inSampleBlank());
            slots.put("inLabware", tile.slots().inLabware());
            slots.put("outSample", tile.slots().outSample());

            return slots;
        }

        @Override
        protected int[] outputSlots() {
            return new int[]{ tile.slots().outSample() };
        }

        @Override
        protected boolean machineCanStart() {
            return tile.canStart();
        }
    }
}
