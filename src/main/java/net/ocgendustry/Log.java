package net.ocgendustry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Log {
    private static final Logger LOGGER = LogManager.getLogger(OCGendustryMod.MODID);

    private Log() {}

    public static void info(String msg) {
        LOGGER.info("[ApiaristTerminal] " + msg);
    }

    public static void warn(String msg) {
        LOGGER.warn("[ApiaristTerminal] " + msg);
    }

    public static void error(String msg, Throwable t) {
        LOGGER.error("[ApiaristTerminal] " + msg, t);
    }
}
