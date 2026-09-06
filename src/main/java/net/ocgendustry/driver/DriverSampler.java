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
        super(TileSampler.class, MachineSpec.<TileSampler>named(COMPONENT)
            .slots(DriverSampler::slots)
            .outputs(tile -> new int[]{ tile.slots().outSample() })
            .canStart(TileSampler::canStart)
            .build());
    }

    private static Map<String, Integer> slots(TileSampler tile) {
        LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

        slots.put("inIndividual", tile.slots().inIndividual());
        slots.put("inSampleBlank", tile.slots().inSampleBlank());
        slots.put("inLabware", tile.slots().inLabware());
        slots.put("outSample", tile.slots().outSample());

        return slots;
    }
}
