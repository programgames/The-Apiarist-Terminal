package net.ocgendustry.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;
import java.util.Collections;

public class MutatronLogicTest {

    @Test
    public void resolveSelectionKey_usesIndexWhenInRange() {
        Integer key = MutatronLogic.resolveSelectionKey(2, Arrays.asList(4, 5, 6));
        Assertions.assertEquals(5, key);
    }

    @Test
    public void resolveSelectionKey_acceptsRawKey() {
        Integer key = MutatronLogic.resolveSelectionKey(9, Arrays.asList(4, 5, 9));
        Assertions.assertEquals(9, key);
    }

    @Test
    public void resolveSelectionKey_invalidReturnsNull() {
        Assertions.assertNull(MutatronLogic.resolveSelectionKey(0, Arrays.asList(1,2)));
        Assertions.assertNull(MutatronLogic.resolveSelectionKey(3, Arrays.asList(1,2)));
        Assertions.assertNull(MutatronLogic.resolveSelectionKey(7, Arrays.asList(4,5,6)));
        Assertions.assertNull(MutatronLogic.resolveSelectionKey(1, Collections.emptyList()));
    }

    @Test
    public void clampSignalInterval_boundsAreRespected() {
        Assertions.assertEquals(1, MutatronLogic.clampSignalInterval(0, 40));
        Assertions.assertEquals(1, MutatronLogic.clampSignalInterval(1, 40));
        Assertions.assertEquals(40, MutatronLogic.clampSignalInterval(41, 40));
        Assertions.assertEquals(5, MutatronLogic.clampSignalInterval(10, 5));
    }

    @Test
    public void clampWaitStep_boundsAreRespected() {
        Assertions.assertEquals(0.05, MutatronLogic.clampWaitStep(0.0));
        Assertions.assertEquals(0.05, MutatronLogic.clampWaitStep(0.05));
        Assertions.assertEquals(2.0, MutatronLogic.clampWaitStep(2.0));
        Assertions.assertEquals(5.0, MutatronLogic.clampWaitStep(10.0));
    }
}
