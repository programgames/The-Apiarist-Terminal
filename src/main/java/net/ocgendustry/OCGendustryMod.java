package net.ocgendustry;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.ocgendustry.driver.DriverRegistry;
import net.ocgendustry.Config;
import net.ocgendustry.command.OcGendustryCommand;

@Mod(modid = OCGendustryMod.MODID, name = OCGendustryMod.NAME, version = OCGendustryMod.VERSION, acceptedMinecraftVersions = "[1.12,1.12.2]", dependencies = "required-after:forge@[14.23.5.2847,);required-after:opencomputers;required-after:gendustry", guiFactory = "net.ocgendustry.client.GuiFactory")
public class OCGendustryMod {
    public static final String MODID = "ocgendustry";
    public static final String NAME = "The Apiarist Terminal";
    public static final String VERSION = "0.2.0";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Load config first
        Config.init(event);
        // Register drivers in preInit, but AFTER OpenComputers preInit via dependency ordering
        Log.info("Registering drivers in preInit (after OC preInit)");
        DriverRegistry.registerAll();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // The floppy that carries apiarist.lua and the example programs. In init rather than
        // preInit: driver registration has to happen early, item registration does not, and
        // OpenComputers is ready for this by now.
        LootDisk.register();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // No-op
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Register root command with subcommands (gated by config inside the command itself)
        event.registerServerCommand(new OcGendustryCommand());
    }
}
