package net.ocgendustry.driver;

import net.bdew.gendustry.machines.mproducer.TileMutagenProducer;
import net.minecraftforge.fluids.FluidTank;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mutagen Producer: turns organic matter into mutagen. Gendustry declares no named slot for it,
 * so listSlots() only reports the inventory size; use it with the generic item transfer as usual.
 */
public final class DriverMutagenProducer extends MachineDriver<TileMutagenProducer> {
    public static final String COMPONENT = "mutagen_producer";

    public DriverMutagenProducer() {
        super(TileMutagenProducer.class, COMPONENT);
    }

    @Override
    protected MachineEnvironment<TileMutagenProducer> createEnvironment(TileMutagenProducer tile) {
        return new Environment(tile);
    }

    public static final class Environment extends MachineEnvironment<TileMutagenProducer> {
        public Environment(TileMutagenProducer tile) {
            super(tile, COMPONENT);
        }

        @Override
        protected Map<String, Integer> namedSlots() {
            return Collections.emptyMap();
        }

        @Override
        protected Map<String, FluidTank> tanks() {
            LinkedHashMap<String, FluidTank> tanks = new LinkedHashMap<>();
            tanks.put("output", tile.tank());

            return tanks;
        }
    }
}
