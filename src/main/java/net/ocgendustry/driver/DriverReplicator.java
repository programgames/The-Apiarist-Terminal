package net.ocgendustry.driver;

import net.bdew.gendustry.machines.replicator.TileReplicator;
import net.minecraftforge.fluids.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Genetic Replicator: rebuilds an individual from a template, consuming DNA and protein.
 */
public final class DriverReplicator extends MachineDriver<TileReplicator> {
    public static final String COMPONENT = "genetic_replicator";

    public DriverReplicator() {
        super(TileReplicator.class, MachineSpec.<TileReplicator>named(COMPONENT)
            .slots(DriverReplicator::slots)
            .outputs(tile -> new int[]{ tile.slots().outIndividual() })
            .tanks(DriverReplicator::tanks)
            .canStart(TileReplicator::canStart)
            .build());
    }

    private static Map<String, Integer> slots(TileReplicator tile) {
        LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

        slots.put("inTemplate", tile.slots().inTemplate());
        slots.put("outIndividual", tile.slots().outIndividual());

        return slots;
    }

    private static Map<String, FluidTank> tanks(TileReplicator tile) {
        LinkedHashMap<String, FluidTank> tanks = new LinkedHashMap<>();

        tanks.put("dna", tile.dnaTank());
        tanks.put("protein", tile.proteinTank());

        return tanks;
    }
}
