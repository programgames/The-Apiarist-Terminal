package net.ocgendustry.util;

/**
 * Clamping helper for the one tunable the components expose, the signal interval. Pure Java so it
 * stays unit-testable without a Minecraft bootstrap.
 *
 * The wait-step clamp that used to live here went with the blocking callbacks it served: a
 * callback cannot wait, so nothing needs a cooperative step any more.
 */
public final class Tuning {
    private Tuning() {}

    /** Clamp signal interval to [1..max]. */
    public static int clampSignalInterval(int desired, int max) {
        if (max < 1) max = 1;
        if (desired < 1) return 1;

        return Math.min(desired, max);
    }

}
