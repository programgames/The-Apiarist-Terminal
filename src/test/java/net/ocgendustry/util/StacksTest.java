package net.ocgendustry.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * The output signature decides whether a component raises {@code <name>_output}, so what it does
 * and does not distinguish is the whole behaviour of that signal.
 *
 * Only the Minecraft-free half is tested here: {@link Stacks#signature(String, int, int, int)}.
 * Reading the four fields off an ItemStack needs a game; deciding what they mean does not.
 */
public class StacksTest {

    @Test
    public void anEmptySlotSignsAsSomethingNoStackCanProduce() {
        // The comparison is a plain string equality, so "nothing here" has to be unmistakable.
        assertThat(Stacks.EMPTY_SIGNATURE).isEqualTo("-");
        assertThat(Stacks.signature("forestry:bee_drone_ge", 0, 1, 0)).isNotEqualTo(Stacks.EMPTY_SIGNATURE);
    }

    @Test
    public void theSameStackAlwaysSignsTheSame() {
        assertThat(Stacks.signature("forestry:bee_drone_ge", 0, 1, 12345))
            .isEqualTo(Stacks.signature("forestry:bee_drone_ge", 0, 1, 12345));
    }

    @Test
    public void twoBeesOfDifferentSpeciesDoNotSignTheSame() {
        // The defect this test exists for: the signature used to be name and count only. Two
        // different bees share a registry name, a metadata value and a count -- everything that
        // tells them apart lives in the NBT. An output slot going straight from one to the other
        // looked unchanged, and no signal was raised, on machines whose entire product is a genome.
        String forest = Stacks.signature("forestry:bee_drone_ge", 0, 1, 111);
        String meadows = Stacks.signature("forestry:bee_drone_ge", 0, 1, 222);

        assertThat(forest).isNotEqualTo(meadows);
    }

    @Test
    public void aChangedCountIsAChange() {
        assertThat(Stacks.signature("minecraft:stone", 0, 1, 0))
            .isNotEqualTo(Stacks.signature("minecraft:stone", 0, 2, 0));
    }

    @Test
    public void aChangedMetadataIsAChange() {
        // Gene samples and templates are distinguished by metadata on some Gendustry items.
        assertThat(Stacks.signature("gendustry:gene_sample", 0, 1, 0))
            .isNotEqualTo(Stacks.signature("gendustry:gene_sample", 1, 1, 0));
    }

    @Test
    public void differentItemsDoNotCollide() {
        assertThat(Stacks.signature("gendustry:labware", 0, 1, 0))
            .isNotEqualTo(Stacks.signature("gendustry:gene_sample", 0, 1, 0));
    }

    @Test
    public void fieldsCannotBleedIntoEachOther() {
        // A separator-free format would make ("a", 1, 23, 0) and ("a", 12, 3, 0) collide.
        assertThat(Stacks.signature("a", 1, 23, 0)).isNotEqualTo(Stacks.signature("a", 12, 3, 0));
    }
}
