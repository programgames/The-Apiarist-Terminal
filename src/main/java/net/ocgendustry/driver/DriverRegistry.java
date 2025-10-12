package net.ocgendustry.driver;

import li.cil.oc.api.Driver;
import net.ocgendustry.Log;

public final class DriverRegistry {
    private DriverRegistry() {}
    private static boolean registered = false;

    public static void registerAll() {
        if (registered) {
            try { Log.info("Driver registration skipped (already registered)"); } catch (Throwable ignored) {}
            return;
        }
        // Register each machine driver here.
        Driver.add(new DriverAdvMutatron());
        registered = true;
        try {
            Log.info("Registered OpenComputers driver: DriverAdvMutatron");
        } catch (Throwable ignored) {}
    }
}
