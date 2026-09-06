package net.ocgendustry.util;

/**
 * Decides, tick by tick, which signals a machine component should raise.
 *
 * This is the part of a driver's update() that has no Minecraft in it, kept here so it can be
 * tested without a game bootstrap. Two defects lived in this logic before it was extracted, and
 * both are the reason it is worth its own class:
 *
 * <ul>
 *   <li>the working flag used to be sampled only once every {@code signalInterval} ticks, and the
 *       edges detected on that sample, so a cycle shorter than the interval started and finished
 *       between two samples and raised neither signal;</li>
 *   <li>the state started from values the machine had never held — not working, empty output — so
 *       the first tick after a chunk load raised a signal that described nothing.</li>
 * </ul>
 *
 * Hence: the working flag is sampled every tick, the caller primes the state from the machine, and
 * only the output scan — which walks the output slots and is therefore the expensive half — is
 * throttled by the interval.
 */
public final class SignalState {
    /** What the working flag did between the previous tick and this one. */
    public enum Edge {
        NONE,
        STARTED,
        FINISHED
    }

    private int signalInterval;
    private boolean lastWorking;
    private String lastSignature;
    private int ticksSinceScan;

    /**
     * @param signalInterval how many ticks between two output scans, clamped by the caller
     * @param working        the machine's working flag right now
     * @param signature      the machine's output signature right now, never null
     */
    public SignalState(int signalInterval, boolean working, String signature) {
        this.signalInterval = Math.max(1, signalInterval);
        this.lastWorking = working;
        this.lastSignature = signature == null ? "" : signature;
        this.ticksSinceScan = 0;
    }

    public int signalInterval() {
        return signalInterval;
    }

    public void setSignalInterval(int ticks) {
        signalInterval = Math.max(1, ticks);
    }

    /** Records the working flag for this tick and reports the transition, if any. */
    public Edge sample(boolean working) {
        if (working == lastWorking) return Edge.NONE;

        lastWorking = working;

        return working ? Edge.STARTED : Edge.FINISHED;
    }

    /**
     * True when enough ticks have passed to scan the output slots again. Call it exactly once per
     * tick: it counts the ticks itself, and counts up to the interval rather than taking a modulo
     * of an ever-growing counter, which would misbehave once that counter overflowed.
     */
    public boolean dueForOutputScan() {
        if (signalInterval <= 1) return true;

        ticksSinceScan++;
        if (ticksSinceScan < signalInterval) return false;

        ticksSinceScan = 0;

        return true;
    }

    /** True when the output signature differs from the last one seen, which it then remembers. */
    public boolean outputChanged(String signature) {
        String current = signature == null ? "" : signature;
        if (current.equals(lastSignature)) return false;

        lastSignature = current;

        return true;
    }
}
