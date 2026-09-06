package net.ocgendustry.driver;

import net.bdew.gendustry.machines.sampler.TileSampler;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * A MachineSpec says what a machine can answer, and {@link MachineEnvironment} turns "no check
 * declared" into {@code false, "not supported by this machine"}. That contract decides what a
 * script sees, so it is worth pinning down.
 *
 * The builder's defaults ignore the tile they are given, which is what lets these run without a
 * game: nothing here constructs a Gendustry tile, it only names one as a type parameter.
 */
public class MachineSpecTest {

    @Test
    public void aMachineDescribedWithNothingStillAnswers() {
        MachineSpec<TileSampler> spec = MachineSpec.<TileSampler>named("mutagen_producer").build();

        assertThat(spec.componentName()).isEqualTo("mutagen_producer");
        assertThat(spec.namedSlots(null)).isEmpty();
        assertThat(spec.tanks(null)).isEmpty();
        assertThat(spec.outputSlots(null)).isEmpty();
    }

    @Test
    public void anUndeclaredCheckIsReportedAsAbsent() {
        // The three fluid machines: Gendustry declares no pre-flight check for them, so the
        // component answers false plus a reason rather than pretending.
        MachineSpec<TileSampler> spec = MachineSpec.<TileSampler>named("dna_extractor").build();

        assertThat(spec.hasCanStart()).isFalse();
        assertThat(spec.hasInputCheck()).isFalse();
    }

    @Test
    public void adeclaredCheckIsReportedAsPresentAndIsCalled() {
        MachineSpec<TileSampler> spec = MachineSpec.<TileSampler>named("genetic_sampler")
            .canStart(tile -> true)
            .build();

        assertThat(spec.hasCanStart()).isTrue();
        assertThat(spec.canStart(null)).isTrue();
        assertThat(spec.hasInputCheck()).isFalse();
    }

    @Test
    public void anInputCheckReportsNullWhenTheInputsAreFine() {
        // The Genetic Transposer's contract: null means "nothing wrong", a string is the reason.
        MachineSpec<TileSampler> ok = MachineSpec.<TileSampler>named("genetic_transposer")
            .inputProblem(tile -> null)
            .build();
        MachineSpec<TileSampler> broken = MachineSpec.<TileSampler>named("genetic_transposer")
            .inputProblem(tile -> "missing template")
            .build();

        assertThat(ok.hasInputCheck()).isTrue();
        assertThat(ok.inputProblem(null)).isNull();
        assertThat(broken.inputProblem(null)).isEqualTo("missing template");
    }

    @Test
    public void slotsAreReadFromTheMachineOnEveryCall() {
        // The point of holding functions rather than values: a slot layout is never frozen at
        // construction, so a Gendustry update that reorders slots cannot leave stale indices.
        int[] calls = { 0 };
        MachineSpec<TileSampler> spec = MachineSpec.<TileSampler>named("genetic_sampler")
            .slots(tile -> {
                calls[0]++;
                Map<String, Integer> slots = new LinkedHashMap<>();
                slots.put("inIndividual", calls[0]);

                return slots;
            })
            .build();

        assertThat(spec.namedSlots(null)).containsEntry("inIndividual", 1);
        assertThat(spec.namedSlots(null)).containsEntry("inIndividual", 2);
        assertThat(calls[0]).isEqualTo(2);
    }

    @Test
    public void namedSlotsKeepTheOrderTheyWereDeclaredIn() {
        // The map becomes a Lua table shown to a user, so the order is visible.
        MachineSpec<TileSampler> spec = MachineSpec.<TileSampler>named("genetic_sampler")
            .slots(tile -> {
                Map<String, Integer> slots = new LinkedHashMap<>();
                slots.put("inIndividual", 2);
                slots.put("inSampleBlank", 0);
                slots.put("inLabware", 1);
                slots.put("outSample", 3);

                return slots;
            })
            .build();

        assertThat(spec.namedSlots(null).keySet())
            .containsExactly("inIndividual", "inSampleBlank", "inLabware", "outSample");
    }

    @Test
    public void outputSlotsDriveWhetherTheOutputSignalCanExist() {
        // No output slot means the component never raises _output: tank levels change on almost
        // every tick and would turn the signal into noise.
        MachineSpec<TileSampler> fluid = MachineSpec.<TileSampler>named("protein_liquifier").build();
        MachineSpec<TileSampler> items = MachineSpec.<TileSampler>named("genetic_sampler")
            .outputs(tile -> new int[]{ 3 })
            .build();

        assertThat(fluid.outputSlots(null)).isEmpty();
        assertThat(items.outputSlots(null)).containsExactly(3);
    }
}
