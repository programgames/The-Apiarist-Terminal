package net.ocgendustry.util;

import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;

public class MutatronLogicTest {

    @Test
    public void resolveSelectionKey_usesIndexWhenInRange() {
        Integer key = MutatronLogic.resolveSelectionKey(2, Arrays.asList(4, 5, 6));
        assertThat(key).isEqualTo(5);
    }

    @Test
    public void resolveSelectionKey_acceptsRawKey() {
        Integer key = MutatronLogic.resolveSelectionKey(9, Arrays.asList(4, 5, 9));
        assertThat(key).isEqualTo(9);
    }

    @Test
    public void resolveSelectionKey_invalidReturnsNull() {
        assertThat(MutatronLogic.resolveSelectionKey(0, Arrays.asList(1,2))).isNull();
        assertThat(MutatronLogic.resolveSelectionKey(3, Arrays.asList(1,2))).isNull();
        assertThat(MutatronLogic.resolveSelectionKey(7, Arrays.asList(4,5,6))).isNull();
        assertThat(MutatronLogic.resolveSelectionKey(1, Collections.emptyList())).isNull();
    }

    @Test
    public void clampSignalInterval_boundsAreRespected() {
        assertThat(MutatronLogic.clampSignalInterval(0, 40)).isEqualTo(1);
        assertThat(MutatronLogic.clampSignalInterval(1, 40)).isEqualTo(1);
        assertThat(MutatronLogic.clampSignalInterval(41, 40)).isEqualTo(40);
        assertThat(MutatronLogic.clampSignalInterval(10, 5)).isEqualTo(5);
    }

    @Test
    public void clampWaitStep_boundsAreRespected() {
        assertThat(MutatronLogic.clampWaitStep(0.0)).isEqualTo(0.05);
        assertThat(MutatronLogic.clampWaitStep(0.05)).isEqualTo(0.05);
        assertThat(MutatronLogic.clampWaitStep(2.0)).isEqualTo(2.0);
        assertThat(MutatronLogic.clampWaitStep(10.0)).isEqualTo(5.0);
    }
}
