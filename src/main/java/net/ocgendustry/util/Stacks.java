package net.ocgendustry.util;

import net.minecraft.item.ItemStack;

import java.util.LinkedHashMap;

/**
 * Shared conversion of an {@link ItemStack} into a Lua-friendly table.
 *
 * Labels and NBT are only added when the stack actually carries a tag compound: calling
 * getDisplayName() on a Forestry genetic item that has no genome NBT makes Forestry spam the log.
 */
public final class Stacks {
    private Stacks() {}

    /** True when the stack is null or empty; both cases mean "nothing in that slot". */
    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    /**
     * Describes a stack as { name, label?, nbt?, count }, or an empty table when there is nothing.
     */
    public static LinkedHashMap<String, Object> info(ItemStack stack) {
        LinkedHashMap<String, Object> info = new LinkedHashMap<>();
        if (isEmpty(stack)) return info;

        if (stack.getItem() != null && stack.getItem().getRegistryName() != null) {
            info.put("name", stack.getItem().getRegistryName().toString());
        }

        if (stack.hasTagCompound() && stack.getTagCompound() != null) {
            info.put("label", stack.getDisplayName());
            info.put("nbt", stack.getTagCompound().toString());
        }

        info.put("count", stack.getCount());

        return info;
    }

    /**
     * Compact identity of a stack, used to detect output changes between two ticks.
     *
     * The NBT hash is part of it, and has to be: these machines produce genetic items whose whole
     * identity lives in their NBT, so two different bees share a registry name, a metadata value
     * and a count. Without it, an output slot going straight from one bee to another between two
     * samples would look unchanged and raise no signal. The hash is cheap — no display name
     * lookup, no NBT serialisation — which is the point of not simply comparing stacks.
     */
    public static String signature(ItemStack stack) {
        if (isEmpty(stack)) return "-";

        String name = (stack.getItem() != null && stack.getItem().getRegistryName() != null)
            ? stack.getItem().getRegistryName().toString() : "?";

        int nbt = stack.hasTagCompound() && stack.getTagCompound() != null
            ? stack.getTagCompound().hashCode() : 0;

        return name + "@" + stack.getItemDamage() + "@" + stack.getCount() + "@" + nbt;
    }
}
