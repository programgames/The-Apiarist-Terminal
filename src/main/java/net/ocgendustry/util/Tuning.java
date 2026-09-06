package net.ocgendustry.util;

/**
 * Clamping helpers shared by every driver for the tunables exposed to OpenComputers
 * (signal interval and cooperative wait step). Pure Java so they stay unit-testable
 * without a Minecraft bootstrap.
 */
public final class Tuning {
    /** Lower bound (seconds) for the cooperative wait step used by blocking callbacks. */
    public static final double MIN_WAIT_STEP = 0.05;

    /** Upper bound (seconds) for the cooperative wait step used by blocking callbacks. */
    public static final double MAX_WAIT_STEP = 5.0;

    private Tuning() {}

    /** Clamp signal interval to [1..max]. */
    public static int clampSignalInterval(int desired, int max) {
        if (max < 1) max = 1;
        if (desired < 1) return 1;

        return Math.min(desired, max);
    }

    /** Clamp wait step to [MIN_WAIT_STEP..MAX_WAIT_STEP]. */
    public static double clampWaitStep(double seconds) {
        if (seconds < MIN_WAIT_STEP) return MIN_WAIT_STEP;
        if (seconds > MAX_WAIT_STEP) return MAX_WAIT_STEP;

        return seconds;
    }
}
