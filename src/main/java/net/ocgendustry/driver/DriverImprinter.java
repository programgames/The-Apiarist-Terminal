package net.ocgendustry.driver;

import net.bdew.gendustry.machines.imprinter.TileImprinter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Genetic Imprinter: writes a genetic template onto an individual, consuming labware.
 */
public final class DriverImprinter extends MachineDriver<TileImprinter> {
    public static final String COMPONENT = "genetic_imprinter";

    public DriverImprinter() {
        super(TileImprinter.class, MachineSpec.<TileImprinter>named(COMPONENT)
            .slots(DriverImprinter::slots)
            .outputs(tile -> new int[]{ tile.slots().outIndividual() })
            .canStart(TileImprinter::canStart)
            .build());
    }

    private static Map<String, Integer> slots(TileImprinter tile) {
        LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

        slots.put("inTemplate", tile.slots().inTemplate());
        slots.put("inIndividual", tile.slots().inIndividual());
        slots.put("inLabware", tile.slots().inLabware());
        slots.put("outIndividual", tile.slots().outIndividual());

        return slots;
    }
}
