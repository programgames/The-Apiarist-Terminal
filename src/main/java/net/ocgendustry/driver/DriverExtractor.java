package net.ocgendustry.driver;

import net.bdew.gendustry.machines.extractor.TileExtractor;
import net.minecraftforge.fluids.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DNA Extractor: dissolves an individual into liquid DNA, consuming labware.
 */
public final class DriverExtractor extends MachineDriver<TileExtractor> {
    public static final String COMPONENT = "dna_extractor";

    public DriverExtractor() {
        super(TileExtractor.class, MachineSpec.<TileExtractor>named(COMPONENT)
            .slots(DriverExtractor::slots)
            .tanks(DriverExtractor::tanks)
            .build());
    }

    private static Map<String, Integer> slots(TileExtractor tile) {
        LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

        slots.put("inIndividual", tile.slots().inIndividual());
        slots.put("inLabware", tile.slots().inLabware());

        return slots;
    }

    private static Map<String, FluidTank> tanks(TileExtractor tile) {
        LinkedHashMap<String, FluidTank> tanks = new LinkedHashMap<>();
        tanks.put("output", tile.tank());

        return tanks;
    }
}
