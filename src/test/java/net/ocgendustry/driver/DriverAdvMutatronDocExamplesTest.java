package net.ocgendustry.driver;

import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Doc-sanity tests: ensure callback docs include certain phrases/features.
 * These are lightweight text checks to avoid regressions in documentation.
 */
public class DriverAdvMutatronDocExamplesTest {

    @Test
    public void callbackDocsMentionSignalAndWait() throws Exception {
        // Ensure the strings we expect in the README/docs exist here in code docs too
        String setSignalDoc = DriverAdvMutatron.Environment.class
                .getDeclaredMethod("setSignalInterval", li.cil.oc.api.machine.Context.class, li.cil.oc.api.machine.Arguments.class)
                .getAnnotation(li.cil.oc.api.machine.Callback.class).doc();
        String setWaitDoc = DriverAdvMutatron.Environment.class
                .getDeclaredMethod("setWaitInterval", li.cil.oc.api.machine.Context.class, li.cil.oc.api.machine.Arguments.class)
                .getAnnotation(li.cil.oc.api.machine.Callback.class).doc();

        assertThat(setSignalDoc).contains("signals");
        assertThat(setWaitDoc).contains("wait");
    }
}
