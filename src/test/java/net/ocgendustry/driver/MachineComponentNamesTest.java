package net.ocgendustry.driver;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Guards the component names of the processing machine drivers.
 *
 * These names are what scripts type as component.<name>, so a duplicate or a rename is a breaking
 * change. The list is also the reason the Genetic Transposer is not simply called "transposer":
 * OpenComputers ships its own Transposer block under that name.
 */
public class MachineComponentNamesTest {

    private static final List<String> COMPONENTS = Arrays.asList(
        DriverMutatron.COMPONENT,
        DriverSampler.COMPONENT,
        DriverImprinter.COMPONENT,
        DriverReplicator.COMPONENT,
        DriverTransposer.COMPONENT,
        DriverExtractor.COMPONENT,
        DriverLiquifier.COMPONENT,
        DriverMutagenProducer.COMPONENT
    );

    @Test
    public void componentNamesAreUnique() {
        assertThat(COMPONENTS).doesNotHaveDuplicates();
    }

    @Test
    public void componentNamesUseLowerSnakeCase() {
        assertThat(COMPONENTS).allMatch(name -> name.matches("[a-z][a-z0-9_]*"));
    }

    @Test
    public void componentNamesDoNotClashWithOpenComputersBlocks() {
        // "transposer" and "inventory_controller" are OpenComputers' own components.
        assertThat(COMPONENTS).doesNotContain("transposer", "inventory_controller");
        assertThat(DriverTransposer.COMPONENT).isEqualTo("genetic_transposer");
    }

    @Test
    public void componentNamesDoNotClashWithTheHandWrittenDrivers() {
        assertThat(COMPONENTS).doesNotContain("advmutatron", "industrial_apiary");
    }
}
