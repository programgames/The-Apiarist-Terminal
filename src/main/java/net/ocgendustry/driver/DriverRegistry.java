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

        // Register each machine driver here.
        Driver.add(new DriverAdvMutatron());
        Driver.add(new DriverApiary());
        registered = true;

        Log.info("Registered OpenComputers drivers: DriverAdvMutatron, DriverApiary");
    }
}
