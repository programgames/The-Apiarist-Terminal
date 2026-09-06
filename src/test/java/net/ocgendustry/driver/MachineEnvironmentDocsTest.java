package net.ocgendustry.driver;

import li.cil.oc.api.machine.Callback;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.*;

/**
 * Guards two properties of the shared processing-machine component.
 *
 * The doc strings, because that string is what an in-game computer sees when it inspects the
 * component. And the fact that every callback is declared on the concrete environment class,
 * because OpenComputers dispatches a callback by comparing
 * {@code environment.getClass()} with {@code method.getDeclaringClass()} for exact identity as
 * soon as several drivers share a block — which is always the case for these machines, since
 * OpenComputers' generic energy driver binds to their Forge Energy capability. A callback
 * inherited from a superclass is still listed by component.methods() but fails every call with
 * "no such method", which is silent and very hard to trace back.
 */
public class MachineEnvironmentDocsTest {

    @Test
    public void sharedCallbacksAreDocumented() {
        int found = 0;

        for (Method m : MachineEnvironment.class.getDeclaredMethods()) {
            Callback callback = m.getAnnotation(Callback.class);
            if (callback == null) continue;

            found++;
            assertThat(callback.doc())
                .as("doc of MachineEnvironment.%s", m.getName())
                .startsWith("function(")
                .contains(" -- ");
        }

        assertThat(found).as("callbacks found on MachineEnvironment").isPositive();
    }

    @Test
    public void everyCallbackIsDeclaredOnTheConcreteClass() {
        for (Method m : MachineEnvironment.class.getMethods()) {
            if (m.getAnnotation(Callback.class) == null) continue;

            assertThat(m.getDeclaringClass())
                .as("%s must be declared on MachineEnvironment itself, not inherited: "
                    + "OpenComputers only dispatches callbacks whose declaring class is exactly "
                    + "the environment's class", m.getName())
                .isEqualTo(MachineEnvironment.class);
        }
    }

    @Test
    public void theEnvironmentCannotBeSubclassed() {
        // A subclass would become the runtime class of the component and none of the callbacks
        // declared here would be dispatched any more.
        assertThat(Modifier.isFinal(MachineEnvironment.class.getModifiers()))
            .as("MachineEnvironment must stay final")
            .isTrue();
    }

    @Test
    public void machinesWithoutAPreflightCheckStillAnswerCanStart() throws Exception {
        // canStart and isValidInputs exist on every machine and answer false plus a reason where
        // Gendustry declares no such check, rather than being absent on some components.
        assertThat(MachineEnvironment.class.getDeclaredMethod("canStart",
            li.cil.oc.api.machine.Context.class, li.cil.oc.api.machine.Arguments.class)).isNotNull();
        assertThat(MachineEnvironment.class.getDeclaredMethod("isValidInputs",
            li.cil.oc.api.machine.Context.class, li.cil.oc.api.machine.Arguments.class)).isNotNull();
    }

    @Test
    public void noCallbackBlocksWaitingForTheMachine() {
        // A callback cannot wait for a cycle: Context.pause() does not suspend the call, it only
        // schedules a pause for after it returns, so a loop around it busy-waits and holds the
        // server thread. Waiting belongs in Lua, on the _finished signal. waitForFinish used to
        // live here and froze the server for the whole of its timeout.
        for (Method m : MachineEnvironment.class.getDeclaredMethods()) {
            if (m.getAnnotation(Callback.class) == null) continue;

            assertThat(m.getName())
                .as("callbacks must return promptly; wait on the _finished signal from Lua instead")
                .isNotEqualTo("waitForFinish");
        }
    }
}
