package net.ocgendustry.driver;

import li.cil.oc.api.machine.Callback;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;

/**
 * Doc-sanity tests: every callback shared by the processing machines must document itself in the
 * "function(args):ret -- description" form used across the project, since that string is what an
 * in-game computer sees when it inspects the component.
 */
public class MachineEnvironmentDocsTest {

    @Test
    public void sharedCallbacksAreDocumented() {
        assertCallbacksDocumented(MachineEnvironment.class);
    }

    @Test
    public void itemMachineCallbacksAreDocumented() {
        assertCallbacksDocumented(ItemMachineEnvironment.class);
    }

    @Test
    public void canStartIsOnlyOfferedByItemMachines() throws Exception {
        // Machines that only fill a tank must not advertise a pre-flight check they cannot answer.
        assertThat(declaredCallbackNames(MachineEnvironment.class)).doesNotContain("canStart");
        assertThat(ItemMachineEnvironment.class.getDeclaredMethod("canStart",
            li.cil.oc.api.machine.Context.class, li.cil.oc.api.machine.Arguments.class)).isNotNull();
    }

    @Test
    public void waitingCallbackMentionsItsTimeout() throws Exception {
        String doc = MachineEnvironment.class
            .getDeclaredMethod("waitForFinish",
                li.cil.oc.api.machine.Context.class, li.cil.oc.api.machine.Arguments.class)
            .getAnnotation(Callback.class).doc();

        assertThat(doc).contains("timeout");
    }

    private static void assertCallbacksDocumented(Class<?> type) {
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

        assertThat(found).as("callbacks found on %s", type.getSimpleName()).isPositive();
    }

    private static java.util.List<String> declaredCallbackNames(Class<?> type) {
        java.util.List<String> names = new java.util.ArrayList<>();

        for (Method m : type.getDeclaredMethods()) {
            if (m.getAnnotation(Callback.class) != null) names.add(m.getName());
        }

        return names;
    }
}
