package net.ocgendustry;

import li.cil.oc.api.FileSystem;
import li.cil.oc.api.Items;
import net.minecraft.item.EnumDyeColor;

/**
 * Registers the floppy that carries this mod's Lua library and example programs.
 *
 * Without it the files sit inside the jar, where an in-game computer cannot reach them: a player
 * would have to open the jar with a zip tool and copy them onto a hard drive by hand. The disk
 * turns that into the same gesture as any other OpenComputers program — take the floppy, put it in
 * a drive, run {@code install}.
 *
 * The layout under {@code assets/ocgendustry/disk} is the floppy's filesystem, verbatim:
 * {@code usr/lib/apiarist.lua} lands where {@code package.path} already looks, and
 * {@code usr/bin/*.lua} where the shell already looks, so OpenOS's {@code install} copies them to
 * {@code /usr} and everything is reachable with no path fiddling. The {@code .prop} file names the
 * disk.
 *
 * Only what ships goes on it. The acceptance tooling in {@code dev/scripts/} is not in the jar and
 * has no business on a player's disk.
 */
public final class LootDisk {

    /** The floppy's filesystem root, resolved as {@code /assets/<modid>/<ROOT>/} inside the jar. */
    private static final String ROOT = "disk";

    private LootDisk() {
    }

    /**
     * Registers the disk with OpenComputers, which then offers it in the creative inventory and in
     * the wrench cycling other loot disks take part in.
     *
     * Call once, from init: the driver registration in preInit is a different concern, and
     * OpenComputers is only ready to take items registered here by then.
     */
    public static void register() {
        // The factory is called every time a disk is put in a drive, not once here, so a new
        // read-only filesystem is handed out per drive rather than one shared handle.
        Items.registerFloppy(
            "Apiarist Terminal",
            EnumDyeColor.YELLOW,
            () -> FileSystem.fromClass(OCGendustryMod.class, OCGendustryMod.MODID, ROOT),
            true);

        Log.info("Registered the Apiarist Terminal floppy (apiarist.lua and the example programs)");
    }
}
