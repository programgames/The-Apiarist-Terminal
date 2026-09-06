package net.ocgendustry.util;

import net.ocgendustry.util.SignalState.Edge;
import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Behavioural tests for the signal logic.
 *
 * Every case here is a defect that reached the game before this logic was extracted and tested:
 * transitions dropped at a coarse interval, phantom signals on the first tick, an output scan that
 * fired every tick regardless of the interval. They are what a doc-string test cannot catch.
 */
public class SignalStateTest {

    @Test
    public void aCycleShorterThanTheIntervalStillRaisesBothEdges() {
        // The Genetic Sampler case: 40 ticks between output scans, a cycle lasting three ticks.
        SignalState state = new SignalState(40, false, "-");

        assertThat(state.sample(true)).isEqualTo(Edge.STARTED);
        assertThat(state.sample(true)).isEqualTo(Edge.NONE);
        assertThat(state.sample(false)).isEqualTo(Edge.FINISHED);
    }

    @Test
    public void aSteadyMachineRaisesNothing() {
        SignalState state = new SignalState(1, true, "-");

        for (int i = 0; i < 100; i++) {
            assertThat(state.sample(true)).isEqualTo(Edge.NONE);
        }
    }

    @Test
    public void primingFromARunningMachineRaisesNoPhantomStart() {
        // A component created while its machine is already working: the first sample must be quiet.
        SignalState state = new SignalState(2, true, "minecraft:stone@0@1@0");

        assertThat(state.sample(true)).isEqualTo(Edge.NONE);
        assertThat(state.outputChanged("minecraft:stone@0@1@0")).isFalse();
    }

    @Test
    public void primingFromAnEmptyOutputRaisesNoPhantomOutput() {
        SignalState state = new SignalState(2, false, "-");

        assertThat(state.outputChanged("-")).isFalse();
    }

    @Test
    public void outputScanIsThrottledToTheInterval() {
        SignalState state = new SignalState(4, false, "-");

        assertThat(state.dueForOutputScan()).isFalse();
        assertThat(state.dueForOutputScan()).isFalse();
        assertThat(state.dueForOutputScan()).isFalse();
        assertThat(state.dueForOutputScan()).isTrue();

        // ... and the counter restarts rather than growing without bound.
        assertThat(state.dueForOutputScan()).isFalse();
        assertThat(state.dueForOutputScan()).isFalse();
        assertThat(state.dueForOutputScan()).isFalse();
        assertThat(state.dueForOutputScan()).isTrue();
    }

    @Test
    public void anIntervalOfOneScansEveryTick() {
        SignalState state = new SignalState(1, false, "-");

        for (int i = 0; i < 10; i++) {
            assertThat(state.dueForOutputScan()).isTrue();
        }
    }

    @Test
    public void aDegenerateIntervalIsTreatedAsEveryTick() {
        assertThat(new SignalState(0, false, "-").signalInterval()).isEqualTo(1);
        assertThat(new SignalState(-5, false, "-").signalInterval()).isEqualTo(1);
    }

    @Test
    public void changingTheIntervalTakesEffect() {
        SignalState state = new SignalState(1, false, "-");
        state.setSignalInterval(3);

        assertThat(state.signalInterval()).isEqualTo(3);
        assertThat(state.dueForOutputScan()).isFalse();
        assertThat(state.dueForOutputScan()).isFalse();
        assertThat(state.dueForOutputScan()).isTrue();
    }

    @Test
    public void outputChangeIsReportedOncePerChange() {
        SignalState state = new SignalState(1, false, "-");

        assertThat(state.outputChanged("bee@0@1@111")).isTrue();
        assertThat(state.outputChanged("bee@0@1@111")).isFalse();

        // Same item, same count, different genome: this is the case a name-and-count signature
        // could not see, and the reason Stacks.signature hashes the NBT.
        assertThat(state.outputChanged("bee@0@1@222")).isTrue();
    }

    @Test
    public void aNullSignatureIsTreatedAsEmptyRatherThanThrowing() {
        SignalState state = new SignalState(1, false, null);

        assertThat(state.outputChanged(null)).isFalse();
        assertThat(state.outputChanged("something")).isTrue();
    }
}
