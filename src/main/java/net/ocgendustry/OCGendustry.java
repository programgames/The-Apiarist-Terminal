package net.ocgendustry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import net.ocgendustry.driver.DriverRegistry;
import net.ocgendustry.command.OcGendustryCommand;


@Mod(
    modid = Tags.MODID,
    name = Tags.MODNAME,
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:opencomputers;required-after:gendustry",
    guiFactory = "net.ocgendustry.client.GuiFactory"
)
public class OCGendustry {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Load config first
        Config.init(event);
        // Register drivers in preInit, but AFTER OpenComputers preInit via dependency ordering
        LOGGER.info("Registering drivers in preInit (after OC preInit)");
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

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        // Register root command with subcommands (gated by config inside the command itself)
        event.registerServerCommand(new OcGendustryCommand());
    }
}
