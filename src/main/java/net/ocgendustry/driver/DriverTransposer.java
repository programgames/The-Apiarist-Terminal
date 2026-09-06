package net.ocgendustry.driver;

import net.bdew.gendustry.machines.transposer.TileTransposer;
import net.minecraft.item.ItemStack;
import net.ocgendustry.util.Stacks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Genetic Transposer: copies a genetic template onto a blank gene sample, consuming labware.
 *
 * Not to be confused with OpenComputers' own Transposer block, which is why the component is
 * named genetic_transposer: a component named "transposer" would collide with it on the network.
 */
public final class DriverTransposer extends MachineDriver<TileTransposer> {
    public static final String COMPONENT = "genetic_transposer";

    public DriverTransposer() {
        super(TileTransposer.class, MachineSpec.<TileTransposer>named(COMPONENT)
            .slots(DriverTransposer::slots)
            .outputs(tile -> new int[]{ tile.slots().outCopy() })
            .canStart(TileTransposer::canStart)
            .inputProblem(DriverTransposer::inputProblem)
            .build());
    }

    private static Map<String, Integer> slots(TileTransposer tile) {
        LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

        slots.put("inTemplate", tile.slots().inTemplate());
        slots.put("inBlank", tile.slots().inBlank());
        slots.put("inLabware", tile.slots().inLabware());
        slots.put("outCopy", tile.slots().outCopy());

        return slots;
    }

    /**
     * Why the loaded pair cannot be copied, or null when it can. Worth checking before committing
     * labware, which is consumed on every run.
     *
     * The verdict itself comes from Gendustry: what exactly it accepts in the template slot is its
     * business, not ours, and it is answered by delegating rather than by reimplementing a rule
     * that would drift.
     */
    private static String inputProblem(TileTransposer tile) {
        ItemStack template = tile.getStackInSlot(tile.slots().inTemplate());
        if (Stacks.isEmpty(template)) return "missing template";

        ItemStack blank = tile.getStackInSlot(tile.slots().inBlank());
        if (Stacks.isEmpty(blank)) return "missing blank sample";

        // Argument order matters and is (blank, template): Gendustry's own isItemValidForSlot calls
        // it that way round from both slots. Passing (template, blank) reports every pair as
        // incompatible, including pairs the machine is happily processing.
        if (!tile.isValidInputs(blank, template)) return "incompatible inputs";

        return null;
    }
}
