package net.ocgendustry.driver;

import net.bdew.gendustry.apiimpl.TileWorker;
import net.bdew.lib.power.TileBaseProcessor;
import net.minecraftforge.fluids.FluidTank;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Description of one Gendustry processing machine, consumed by {@link MachineEnvironment}.
 *
 * The machines differ only in their name, their named slots, their tanks and whether Gendustry
 * declares a pre-flight check for them, so they are described with data rather than with a
 * subclass. That is not a matter of taste: OpenComputers resolves which environment owns a
 * callback with {@code environment.getClass().equals(method.getDeclaringClass())} whenever several
 * drivers share a block, so a callback inherited from a superclass is never dispatched. Every
 * {@code @Callback} therefore has to live on the one concrete environment class, and the per
 * machine differences have to arrive as values.
 *
 * @param <T> the Gendustry tile this describes
 */
public final class MachineSpec<T extends TileBaseProcessor & TileWorker> {
    /** Machines that only output fluid have no output item slot to watch. */
    static final int[] NO_SLOTS = new int[0];

    private final String componentName;
    private final Function<T, Map<String, Integer>> namedSlots;
    private final Function<T, int[]> outputSlots;
    private final Function<T, Map<String, FluidTank>> tanks;
    private final Predicate<T> canStart;
    private final Function<T, String> inputProblem;

    private MachineSpec(Builder<T> builder) {
        this.componentName = builder.componentName;
        this.namedSlots = builder.namedSlots;
        this.outputSlots = builder.outputSlots;
        this.tanks = builder.tanks;
        this.canStart = builder.canStart;
        this.inputProblem = builder.inputProblem;
    }

    /** Starts describing the machine exposed under {@code componentName}. */
    public static <T extends TileBaseProcessor & TileWorker> Builder<T> named(String componentName) {
        return new Builder<>(componentName);
    }

    /** The OpenComputers component name, e.g. {@code genetic_sampler}. */
    public String componentName() {
        return componentName;
    }

    /** Item slots keyed by the name Gendustry itself uses (inTemplate, outCopy...). */
    public Map<String, Integer> namedSlots(T tile) {
        return namedSlots.apply(tile);
    }

    /** Output item slots watched for the {@code <component>_output} signal. */
    public int[] outputSlots(T tile) {
        return outputSlots.apply(tile);
    }

    /** Tanks keyed by role (input, output, dna, protein). */
    public Map<String, FluidTank> tanks(T tile) {
        return tanks.apply(tile);
    }

    /** True when this machine declares a pre-flight check; the three fluid machines do not. */
    public boolean hasCanStart() {
        return canStart != null;
    }

    /** The machine's own canStart(); only call it when {@link #hasCanStart()} is true. */
    public boolean canStart(T tile) {
        return canStart.test(tile);
    }

    /** True when this machine can explain why its loaded inputs are unusable. */
    public boolean hasInputCheck() {
        return inputProblem != null;
    }

    /** Reason the loaded inputs cannot be processed, or null when they are fine. */
    public String inputProblem(T tile) {
        return inputProblem.apply(tile);
    }

    /** Describes one machine; every part except the name is optional. */
    public static final class Builder<T extends TileBaseProcessor & TileWorker> {
        private final String componentName;

        private Function<T, Map<String, Integer>> namedSlots = tile -> Collections.emptyMap();
        private Function<T, int[]> outputSlots = tile -> NO_SLOTS;
        private Function<T, Map<String, FluidTank>> tanks = tile -> Collections.emptyMap();
        private Predicate<T> canStart;
        private Function<T, String> inputProblem;

        private Builder(String componentName) {
            this.componentName = componentName;
        }

        public Builder<T> slots(Function<T, Map<String, Integer>> namedSlots) {
            this.namedSlots = namedSlots;

            return this;
        }

        public Builder<T> outputs(Function<T, int[]> outputSlots) {
            this.outputSlots = outputSlots;

            return this;
        }

        public Builder<T> tanks(Function<T, Map<String, FluidTank>> tanks) {
            this.tanks = tanks;

            return this;
        }

        public Builder<T> canStart(Predicate<T> canStart) {
            this.canStart = canStart;

            return this;
        }

        public Builder<T> inputProblem(Function<T, String> inputProblem) {
            this.inputProblem = inputProblem;

            return this;
        }

        public MachineSpec<T> build() {
            return new MachineSpec<>(this);
        }
    }
}
