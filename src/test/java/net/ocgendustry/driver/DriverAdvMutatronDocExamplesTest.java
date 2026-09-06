package net.ocgendustry.driver;

import li.cil.oc.api.machine.Callback;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;

/**
 * Doc-sanity tests for the two hand-written drivers.
 *
 * Beyond the wording, these guard the rule that cost the project two 60-second server freezes:
 * no callback may wait for the machine. OpenComputers runs a callback on the server thread unless
 * it is declared direct (Callback.direct() defaults to false), and Context.pause() does not
 * suspend the call — it only schedules a pause for after it returns. Looping on it therefore
 * blocks the tick loop for the whole timeout. Waiting belongs in Lua, on the signals these
 * drivers already emit.
 */
public class DriverAdvMutatronDocExamplesTest {

    private static final Class<?>[] HAND_WRITTEN = {
        DriverAdvMutatron.Environment.class,
        DriverApiary.Environment.class,
    };

    @Test
    public void signalIntervalIsDocumented() throws Exception {
        String doc = DriverAdvMutatron.Environment.class
            .getDeclaredMethod("setSignalInterval",
                li.cil.oc.api.machine.Context.class, li.cil.oc.api.machine.Arguments.class)
            .getAnnotation(Callback.class).doc();

        assertThat(doc).contains("signals");
    }

    @Test
    public void noCallbackWaitsForTheMachine() {
        for (Class<?> type : HAND_WRITTEN) {
            for (Method m : type.getDeclaredMethods()) {
                if (m.getAnnotation(Callback.class) == null) continue;

                assertThat(m.getName())
                    .as("%s.%s must return promptly; wait on a signal from Lua instead",
                        type.getSimpleName(), m.getName())
                    .isNotIn("waitForFinish", "waitForPrincess", "waitForOutput");
            }
        }
    }

    @Test
    public void noCallbackAdvertisesABlockingWait() {
        for (Class<?> type : HAND_WRITTEN) {
            for (Method m : type.getDeclaredMethods()) {
                Callback callback = m.getAnnotation(Callback.class);
                if (callback == null) continue;

                // "wait until ... finished" in a doc string is the signature of the old pattern.
                assertThat(callback.doc().toLowerCase())
                    .as("doc of %s.%s promises a wait that cannot be implemented safely",
                        type.getSimpleName(), m.getName())
                    .doesNotContain("wait until");
            }
        }
    }

    @Test
    public void callbackDocsKeepTheProjectFormat() {
        for (Class<?> type : HAND_WRITTEN) {
            int found = 0;

            for (Method m : type.getDeclaredMethods()) {
                Callback callback = m.getAnnotation(Callback.class);
                if (callback == null) continue;

                found++;
                assertThat(callback.doc())
                    .as("doc of %s.%s", type.getSimpleName(), m.getName())
                    .startsWith("function(")
                    .contains(" -- ");
            }

            assertThat(found).as("callbacks on %s", type.getSimpleName()).isPositive();
        }
    }
}
