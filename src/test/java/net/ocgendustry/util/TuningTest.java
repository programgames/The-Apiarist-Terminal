package net.ocgendustry.util;

import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

public class TuningTest {

    @Test
    public void clampSignalInterval_boundsAreRespected() {
        assertThat(Tuning.clampSignalInterval(0, 40)).isEqualTo(1);
        assertThat(Tuning.clampSignalInterval(1, 40)).isEqualTo(1);
        assertThat(Tuning.clampSignalInterval(41, 40)).isEqualTo(40);
        assertThat(Tuning.clampSignalInterval(10, 5)).isEqualTo(5);
    }

    @Test
    public void clampSignalInterval_degenerateMaxStillYieldsOne() {
        assertThat(Tuning.clampSignalInterval(10, 0)).isEqualTo(1);
        assertThat(Tuning.clampSignalInterval(-5, 0)).isEqualTo(1);
    }

    @Test
    public void clampWaitStep_boundsAreRespected() {
        assertThat(Tuning.clampWaitStep(0.0)).isEqualTo(0.05);
        assertThat(Tuning.clampWaitStep(0.05)).isEqualTo(0.05);
        assertThat(Tuning.clampWaitStep(2.0)).isEqualTo(2.0);
        assertThat(Tuning.clampWaitStep(10.0)).isEqualTo(5.0);
    }
}
