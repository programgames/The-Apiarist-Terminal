package net.ocgendustry;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.ocgendustry.driver.DriverRegistry;

@Mod(modid = OCGendustryMod.MODID, name = OCGendustryMod.NAME, version = OCGendustryMod.VERSION, acceptedMinecraftVersions = "[1.12,1.12.2]", dependencies = "required-after:forge@[14.23.5.2847,);after:opencomputers;required-after:gendustry")
public class OCGendustryMod {
    public static final String MODID = "ocgendustry";
    public static final String NAME = "The Apiarist Terminal";
    public static final String VERSION = "0.1.0";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Register drivers in preInit, but AFTER OpenComputers preInit via dependency ordering
        Log.info("Registering drivers in preInit (after OC preInit)");
        DriverRegistry.registerAll();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // No-op - drivers already registered
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // No-op
    }
}
