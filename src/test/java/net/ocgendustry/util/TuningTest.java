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

}
