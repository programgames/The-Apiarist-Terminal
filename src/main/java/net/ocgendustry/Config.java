package net.ocgendustry;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import net.ocgendustry.Tags;


@Mod.EventBusSubscriber(modid = Tags.MODID)
public final class Config {
    public static Configuration config;

    // General category keys
    public static final String CAT_GENERAL = "General";
    public static final String CAT_ADV_MUTATRON = "Advanced Mutatron";
    public static final String CAT_APIARY = "Industrial Apiary";
    public static final String CAT_INTEGRATION = "Integration Test";

    // Advanced Mutatron defaults
    public static int advMutatronSignalInterval = 2;     // ticks
    public static double advMutatronWaitInterval = 0.2;  // seconds
    public static int advMutatronSignalIntervalMax = 40; // cap safety

    // Apiary defaults
    public static int apiarySignalInterval = 2;          // ticks
    public static double apiaryWaitInterval = 0.2;       // seconds
    public static int apiarySignalIntervalMax = 40;      // cap safety

    // Per-device default for events (applied on environment creation / applyDefaultTuning)
    public static boolean advMutatronDefaultEventsEnabled = true;
    public static boolean apiaryDefaultEventsEnabled = true;

    // Integration test harness (disabled by default; gated for safety)
    public static boolean enableIntegrationHarness = false;
    public static boolean allowAutoPlacement = false;
    public static int integrationSearchRadius = 16;

    // Global switch to enable/disable OpenComputers event emissions from drivers
    public static boolean enableEvents = true;

    public static void init(FMLPreInitializationEvent event) {
        File cfgFile = new File(event.getModConfigurationDirectory(), Tags.MODID + ".cfg");
        config = new Configuration(cfgFile);
        syncFromFile();
    }

    public static void syncFromFile() {
        if (config == null) return;

        config.load();
        readValues(config);

        if (config.hasChanged()) config.save();
    }

    // Read current values from the existing Configuration object (no reload), then save.
    public static void syncFromGui() {
        if (config == null) return;

        // Do NOT call load() here; the GUI has already applied changes to this instance.
        readValues(config);

        if (config.hasChanged()) config.save();
    }

    private static void readValues(Configuration cfg) {
        // Category comments
        cfg.setCategoryComment(CAT_GENERAL, "General settings for The Apiarist Terminal.");
        cfg.setCategoryComment(CAT_ADV_MUTATRON, "Advanced Mutatron driver defaults.");
        cfg.setCategoryComment(CAT_APIARY, "Industrial Apiary driver defaults.");
        cfg.setCategoryComment(CAT_INTEGRATION, "In-game integration test harness (creative only; keep disabled on normal worlds).");

        // General
        enableEvents = cfg.getBoolean(
            "enableEvents",
            CAT_GENERAL,
            enableEvents,
            "Enable OpenComputers event emissions (started/finished/output). Disable to disable ticking."
        );

        // Advanced Mutatron
        advMutatronSignalInterval = cfg.getInt(
            "signalIntervalTicks",
            CAT_ADV_MUTATRON,
            advMutatronSignalInterval,
            1,
            advMutatronSignalIntervalMax,
            "Time between checks for OC signals (in ticks). Lower = more responsive, higher = less overhead."
        );
        advMutatronWaitInterval = cfg.getFloat(
            "waitStepSeconds",
            CAT_ADV_MUTATRON,
            (float) advMutatronWaitInterval,
            0.05f,
            5.0f,
            "Time between checks for blocking wait steps (in seconds)."
        );
        advMutatronDefaultEventsEnabled = cfg.getBoolean(
            "defaultEventsEnabled",
            CAT_ADV_MUTATRON,
            advMutatronDefaultEventsEnabled,
            "Default per-device eventsEnabled state for newly created Adv Mutatron components."
        );

        // Apiary
        apiarySignalInterval = cfg.getInt(
            "signalIntervalTicks",
            CAT_APIARY,
            apiarySignalInterval,
            1,
            apiarySignalIntervalMax,
            "Time between checks for OC signals (in ticks). Lower = more responsive, higher = less overhead."
        );
        apiaryWaitInterval = cfg.getFloat(
            "waitStepSeconds",
            CAT_APIARY,
            (float) apiaryWaitInterval,
            0.05f,
            5.0f,
            "Time between checks for blocking wait steps (in seconds)."
        );
        apiaryDefaultEventsEnabled = cfg.getBoolean(
            "defaultEventsEnabled",
            CAT_APIARY,
            apiaryDefaultEventsEnabled,
            "Default per-device eventsEnabled state for newly created Industrial Apiary components."
        );

        // Integration
        enableIntegrationHarness = cfg.getBoolean(
            "enable",
            CAT_INTEGRATION,
            enableIntegrationHarness,
            "Enable a gated test command for running integration checks in-game."
        );
        allowAutoPlacement = cfg.getBoolean(
            "allowAutoPlacement",
            CAT_INTEGRATION,
            allowAutoPlacement,
            "Allow the harness to place blocks/items in a small test area (creative recommended)."
        );
        integrationSearchRadius = cfg.getInt(
            "searchRadius",
            CAT_INTEGRATION,
            integrationSearchRadius,
            4,
            64,
            "Radius around the player to search for test targets (e.g., Mutatron)."
        );
    }

    // Re-sync values when changed via the Forge config GUI
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (Tags.MODID.equals(event.getModID())) {
            // Re-sync from the GUI-modified Configuration without reloading defaults from disk
            syncFromGui();
        }
    }
}
