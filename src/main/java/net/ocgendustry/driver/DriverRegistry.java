package net.ocgendustry.driver;

import li.cil.oc.api.Driver;
import net.ocgendustry.Log;

public final class DriverRegistry {
    private DriverRegistry() {}
    private static boolean registered = false;

    public static void registerAll() {
        if (registered) {
            Log.info("Driver registration skipped (already registered)");
            return;
        }

        // Hand written drivers: these two machines expose state no generic driver can reach.
        Driver.add(new DriverAdvMutatron());
        Driver.add(new DriverApiary());

        // Processing machines: same shape, described by MachineDriver subclasses.
        Driver.add(new DriverMutatron());
        Driver.add(new DriverSampler());
        Driver.add(new DriverImprinter());
        Driver.add(new DriverReplicator());
        Driver.add(new DriverTransposer());
        Driver.add(new DriverExtractor());
        Driver.add(new DriverLiquifier());
        Driver.add(new DriverMutagenProducer());

        registered = true;

        Log.info("Registered OpenComputers drivers for 10 Gendustry machines");
    }
}
