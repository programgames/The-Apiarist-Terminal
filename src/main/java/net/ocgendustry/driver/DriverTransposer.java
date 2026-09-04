package net.ocgendustry.driver;

import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import net.bdew.gendustry.machines.transposer.TileTransposer;
import net.minecraft.item.ItemStack;

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
        super(TileTransposer.class, COMPONENT);
    }

    @Override
    protected MachineEnvironment<TileTransposer> createEnvironment(TileTransposer tile) {
        return new Environment(tile);
    }

    public static final class Environment extends ItemMachineEnvironment<TileTransposer> {
        public Environment(TileTransposer tile) {
            super(tile, COMPONENT);
        }

        @Override
        protected Map<String, Integer> namedSlots() {
            LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();

            slots.put("inTemplate", tile.slots().inTemplate());
            slots.put("inBlank", tile.slots().inBlank());
            slots.put("inLabware", tile.slots().inLabware());
            slots.put("outCopy", tile.slots().outCopy());

            return slots;
        }

        @Override
        protected int[] outputSlots() {
            return new int[]{ tile.slots().outCopy() };
        }

        @Override
        protected boolean machineCanStart() {
            return tile.canStart();
        }

        @Callback(doc = "function():boolean,string? -- Checks the pair currently loaded: true when the template can be copied onto the blank sample, false plus a reason when a slot is empty or the two do not belong to the same species root (bees, trees, butterflies).")
        public Object[] isValidInputs(Context ctx, Arguments args) {
            ItemStack template = tile.getStackInSlot(tile.slots().inTemplate());
            ItemStack blank = tile.getStackInSlot(tile.slots().inBlank());

            if (template == null || template.isEmpty()) return new Object[]{ false, "missing template" };
            if (blank == null || blank.isEmpty()) return new Object[]{ false, "missing blank sample" };

            if (!tile.isValidInputs(template, blank)) return new Object[]{ false, "incompatible inputs" };

            return new Object[]{ true };
        }
    }
}
