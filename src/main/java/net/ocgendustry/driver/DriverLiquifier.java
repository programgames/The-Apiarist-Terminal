package net.ocgendustry.driver;

import net.bdew.gendustry.machines.liquifier.TileLiquifier;
import net.minecraftforge.fluids.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Protein Liquifier: turns meat into liquid protein.
 */
public final class DriverLiquifier extends MachineDriver<TileLiquifier> {
    public static final String COMPONENT = "protein_liquifier";

    public DriverLiquifier() {
        super(TileLiquifier.class, MachineSpec.<TileLiquifier>named(COMPONENT)
            .slots(DriverLiquifier::slots)
            .tanks(DriverLiquifier::tanks)
            .build());
    }

    private static Map<String, Integer> slots(TileLiquifier tile) {
        LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();
        slots.put("inMeat", tile.slots().inMeat());

        return slots;
    }

    private static Map<String, FluidTank> tanks(TileLiquifier tile) {
        LinkedHashMap<String, FluidTank> tanks = new LinkedHashMap<>();
        tanks.put("output", tile.tank());

        return tanks;
    }
}
