package net.ocgendustry.driver;

import net.bdew.gendustry.machines.mproducer.TileMutagenProducer;
import net.minecraftforge.fluids.FluidTank;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutagen Producer: turns organic matter into mutagen. Gendustry declares no named slot for it,
 * so listSlots() only reports the inventory size; use it with the generic item transfer as usual.
 */
public final class DriverMutagenProducer extends MachineDriver<TileMutagenProducer> {
    public static final String COMPONENT = "mutagen_producer";

    public DriverMutagenProducer() {
        super(TileMutagenProducer.class, MachineSpec.<TileMutagenProducer>named(COMPONENT)
            .tanks(DriverMutagenProducer::tanks)
            .build());
    }

    private static Map<String, FluidTank> tanks(TileMutagenProducer tile) {
        LinkedHashMap<String, FluidTank> tanks = new LinkedHashMap<>();
        tanks.put("output", tile.tank());

        return tanks;
    }
}
