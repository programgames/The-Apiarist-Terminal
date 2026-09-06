package net.ocgendustry.driver;

import net.bdew.gendustry.machines.mutatron.TileMutatron;
import net.minecraftforge.fluids.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutatron: crosses two individuals into a new one, consuming labware and mutagen.
 */
public final class DriverMutatron extends MachineDriver<TileMutatron> {
    public static final String COMPONENT = "mutatron";

    public DriverMutatron() {
        super(TileMutatron.class, MachineSpec.<TileMutatron>named(COMPONENT)
            .slots(DriverMutatron::slots)
            .outputs(tile -> new int[]{ tile.slots().outIndividual() })
            .tanks(DriverMutatron::tanks)
            .canStart(TileMutatron::canStart)
            .build());
    }

    private static Map<String, Integer> slots(TileMutatron tile) {
        LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

        slots.put("inIndividual1", tile.slots().inIndividual1());
        slots.put("inIndividual2", tile.slots().inIndividual2());
        slots.put("inLabware", tile.slots().inLabware());
        slots.put("outIndividual", tile.slots().outIndividual());

        return slots;
    }

    private static Map<String, FluidTank> tanks(TileMutatron tile) {
        LinkedHashMap<String, FluidTank> tanks = new LinkedHashMap<>();
        tanks.put("input", tile.tank());

        return tanks;
    }
}
