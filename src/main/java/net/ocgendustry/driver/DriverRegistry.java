package net.ocgendustry.driver;

import li.cil.oc.api.Driver;

import net.ocgendustry.OCGendustry;


public final class DriverRegistry {
    private DriverRegistry() {}
    private static boolean registered = false;

    public static void registerAll() {
        if (registered) {
            OCGendustry.LOGGER.info("Driver registration skipped (already registered)");
            return;
        }

        // Register each machine driver here.
        Driver.add(new DriverAdvMutatron());
        Driver.add(new DriverApiary());
        registered = true;

        OCGendustry.LOGGER.info("Registered OpenComputers drivers: DriverAdvMutatron, DriverApiary");
    }
}
