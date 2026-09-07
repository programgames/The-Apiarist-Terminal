package net.ocgendustry.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.ocgendustry.Config;
import net.bdew.gendustry.machines.advmutatron.TileMutatronAdv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.util.ResourceLocation;
import net.minecraft.init.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;

import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.apiculture.IBeeRoot;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.ISpeciesRoot;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.inventory.IInventory;

public class OcGendustryCommand extends CommandBase {
    @Override
    public String getName() { return "ocgendustry"; }

    @Override
    public String getUsage(ICommandSender sender) { return "/ocgendustry test advmutatron [fresh|reuse|all]"; }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayerMP)) return;

        EntityPlayerMP player = (EntityPlayerMP) sender;
        World world = player.world;

        if (args.length == 0 || !"test".equalsIgnoreCase(args[0])) {
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Usage: /ocgendustry test advmutatron"));
            return;
        }

        if (!Config.enableIntegrationHarness) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Integration harness disabled in config."));
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "Enable via: Mods -> The Apiarist Terminal -> Config -> integration_test -> enable"));
            return;
        }

        if (args.length < 2 || !"advmutatron".equalsIgnoreCase(args[1])) {
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Usage: /ocgendustry test advmutatron [fresh|reuse|all]"));
            return;
        }

        // Locate a nearby Advanced Mutatron
        int r = Config.integrationSearchRadius;
        BlockPos center = player.getPosition();

        TileMutatronAdv found = null;
        for (int dx = -r; dx <= r && found == null; dx++) {
            for (int dy = -r; dy <= r && found == null; dy++) {
                for (int dz = -r; dz <= r && found == null; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (world.isBlockLoaded(p) && world.getTileEntity(p) instanceof TileMutatronAdv) {
                        found = (TileMutatronAdv) world.getTileEntity(p);
                        break;
                    }

                    if (found != null) break;
                }

                if (found != null) break;
            }

            if (found != null) break;
        }

        if (found == null) {
            player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "No Advanced Mutatron found within " + r + " blocks."));
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "Place one near you and run the command again."));

            return;
        }

        BlockPos tp = found.getPos();
        player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Found Advanced Mutatron at " + tp.getX() + "," + tp.getY() + "," + tp.getZ()));

        // Optionally auto-place a small rig (transposer + chest + adapter) around the Mutatron
        Map<String, String> sideReplacements = new HashMap<>();
        if (Config.allowAutoPlacement) {
            if (!(player.capabilities != null && player.capabilities.isCreativeMode)) {
                player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Auto-placement requires creative mode; skipping rig."));
            } else {
                BlockPos chestPos = setupRig(world, found.getPos(), sideReplacements);

                if (chestPos != null) {
                    boolean apiaryPlaced = sideReplacements.containsKey("{{APIARY}}");
                    player.sendMessage(new TextComponentString(TextFormatting.GREEN + "Placed transposer+chest+adapter" + (apiaryPlaced ? "+apiary " : " ") + "around the Mutatron."));

                    // Populate chest with parents and labware if possible
                    int inserted = populateChestWithParents(world, chestPos);
                    player.sendMessage(new TextComponentString(TextFormatting.AQUA + "Seeded chest with " + inserted + " stacks (parents/labware)."));
                } else {
                    player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Auto-placement failed (no free space); continuing."));
                }
            }
        }

        // Write ready-to-run OC Lua scripts from resources (patched with sides if rig placed)
        String variant = (args.length >= 3) ? args[2].toLowerCase(Locale.ROOT) : "all";
        boolean writeFresh = variant.equals("fresh") || variant.equals("all") || variant.isEmpty();
        boolean writeReuse = variant.equals("reuse") || variant.equals("all") || variant.isEmpty();

        int wrote = 0;
        try {
            File outDir = server.getFile("ocgendustry-test");

            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
            if (writeFresh) wrote += writeResource(outDir, "advmutatron_fresh.lua", "/assets/ocgendustry/harness/advmutatron_fresh.lua", sideReplacements);
            if (writeReuse) wrote += writeResource(outDir, "advmutatron_reuse.lua", "/assets/ocgendustry/harness/advmutatron_reuse.lua", sideReplacements);

            if (wrote == 0) {
                player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Nothing written; specify fresh, reuse or all"));
            } else {
                player.sendMessage(new TextComponentString(TextFormatting.AQUA + "Wrote " + wrote + " test script(s) to " + outDir.getAbsolutePath()));
                player.sendMessage(new TextComponentString(TextFormatting.GRAY + "Copy to an OC disk (e.g., /home/*.lua) and run it."));
            }
        } catch (IOException e) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "Failed to write test script(s): " + e));
        }
    }

    private int writeResource(File outDir, String outName, String resourcePath, Map<String, String> replacements) throws IOException {
        try (InputStream in = this.getClass().getResourceAsStream(resourcePath)) {
            if (in == null) return 0;

            File out = new File(outDir, outName);
            try (Writer fw = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
                Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
                String content = s.hasNext() ? s.next() : "";

                if (replacements != null && !replacements.isEmpty()) {
                    for (Map.Entry<String, String> e : replacements.entrySet()) {
                        content = content.replace(e.getKey(), e.getValue());
                    }

                    // Fallback: patch the script defaults if the placeholders weren't present
                    if (replacements.containsKey("{{SRC}}")) {
                        content = content.replace("local SRC = sides.east", "local SRC = " + replacements.get("{{SRC}}"));
                    }

                    if (replacements.containsKey("{{DST}}")) {
                        content = content.replace("local DST = sides.west", "local DST = " + replacements.get("{{DST}}"));
                    }

                    if (replacements.containsKey("{{APIARY}}")) {
                        content = content.replace("local APIARY = sides.north", "local APIARY = " + replacements.get("{{APIARY}}"));
                    }
                }

                fw.write(content);
            }

            return 1;
        }
    }

    private BlockPos setupRig(World world, BlockPos mutPos, Map<String, String> sideOut) {
        // Find a horizontal side for transposer and chest
        EnumFacing[] horiz = new EnumFacing[]{EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH};
        Block transposer = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("opencomputers", "transposer"));
        Block adapter = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("opencomputers", "adapter"));

        if (transposer == null || adapter == null) return null;

        for (EnumFacing face : horiz) {
            BlockPos tpPos = mutPos.offset(face);
            // Transposer front should face the mutatron, so use opposite of the offset direction
            EnumFacing transFront = face.getOpposite();

            if (!isPlaceable(world, tpPos)) continue;

            // Determine best chest position relative to transposer: prefer BACK, then LEFT, then RIGHT
            EnumFacing[] chestOrder = new EnumFacing[]{transFront.getOpposite(), transFront.rotateYCCW(), transFront.rotateY()};
            String[] chestOrderSides = new String[]{"sides.back", "sides.left", "sides.right"};
            BlockPos chosenChest = null;
            String chestSideStr = null;
            int chosenIdx = -1;
            for (int i = 0; i < chestOrder.length; i++) {
                EnumFacing rel = chestOrder[i];
                BlockPos cp = tpPos.offset(rel);
                if (isPlaceable(world, cp)) {
                    chosenChest = cp;
                    chestSideStr = chestOrderSides[i];
                    chosenIdx = i;
                    break;
                }
            }

            if (chosenChest == null) continue; // no spot for chest around this transposer position

            // Place transposer facing the mutatron (front toward mutatron)
            IBlockState tpState = withFacing(transposer.getDefaultState(), transFront);
            if (tpState == null) continue;

            // Clear replaceables if needed and place
            clearIfReplaceable(world, tpPos);
            world.setBlockState(tpPos, tpState, 3);

            clearIfReplaceable(world, chosenChest);
            world.setBlockState(chosenChest, Blocks.CHEST.getDefaultState(), 3);

            // Place adapter on a different free side
            for (EnumFacing f2 : horiz) {
                if (f2 == face || f2 == face.getOpposite()) continue;

                BlockPos adPos = mutPos.offset(f2);
                if (!isPlaceable(world, adPos)) continue;

                IBlockState adState = withFacing(adapter.getDefaultState(), f2);
                if (adState == null) continue;
                clearIfReplaceable(world, adPos);
                world.setBlockState(adPos, adState, 3);

                break;
            }

            // Scripts: front = mutatron side, SRC = chest relative side, DST = front
            sideOut.put("{{SRC}}", chestSideStr);
            sideOut.put("{{DST}}", "sides.front");

            // Try to place an Industrial Apiary adjacent to the transposer (left/right)
            Block apiary = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gendustry", "industrial_apiary"));
            if (apiary != null) {
                // Compute left/right relative to the transposer's front
                EnumFacing left = transFront.rotateYCCW();
                EnumFacing right = transFront.rotateY();

                BlockPos apiaryLeft = tpPos.offset(left);
                BlockPos apiaryRight = tpPos.offset(right);

                // If chest used LEFT, try RIGHT for apiary first; if used RIGHT, try LEFT first
                boolean chestUsedLeft = (chosenIdx == 1);
                boolean chestUsedRight = (chosenIdx == 2);

                if (!chestUsedLeft && isPlaceable(world, apiaryLeft)) {
                    clearIfReplaceable(world, apiaryLeft);
                    world.setBlockState(apiaryLeft, apiary.getDefaultState(), 3);
                    sideOut.put("{{APIARY}}", "sides.left");
                } else if (!chestUsedRight && isPlaceable(world, apiaryRight)) {
                    clearIfReplaceable(world, apiaryRight);
                    world.setBlockState(apiaryRight, apiary.getDefaultState(), 3);
                    sideOut.put("{{APIARY}}", "sides.right");
                }
            }

            return chosenChest;
        }
        return null;
    }

    private boolean isPlaceable(World world, BlockPos pos) {
        if (world.isAirBlock(pos)) return true;
        IBlockState state = world.getBlockState(pos);
        Block b = state.getBlock();

        return b.isReplaceable(world, pos);
    }

    private void clearIfReplaceable(World world, BlockPos pos) {
        if (!world.isAirBlock(pos)) {
            IBlockState state = world.getBlockState(pos);
            Block b = state.getBlock();

            if (b.isReplaceable(world, pos)) {
                // remove without drops; this runs under creative check earlier
                world.setBlockToAir(pos);
            }
        }
    }

    private IBlockState withFacing(IBlockState state, EnumFacing facing) {
        for (IProperty<?> p : state.getPropertyKeys()) {
            if (p instanceof PropertyDirection && "facing".equals(p.getName())) {
                PropertyDirection dir = (PropertyDirection) p;
                if (dir.getAllowedValues().contains(facing)) return state.withProperty(dir, facing);
            }
        }

        return null;
    }

    private int populateChestWithParents(World world, BlockPos chestPos) {
        TileEntity te = world.getTileEntity(chestPos);
        if (!(te instanceof IInventory)) return 0;

        IInventory inv = (IInventory) te;
        int inserted = 0;

        // Get bee root
        ISpeciesRoot root = AlleleManager.alleleRegistry.getSpeciesRoot("rootBees");

        if (!(root instanceof IBeeRoot)) return 0;
        IBeeRoot beeRoot = (IBeeRoot) root;

        // Helper to create stacks
        java.util.function.BiFunction<String, EnumBeeType, ItemStack> make = (speciesLower, type) -> {
            String keyName = speciesLower.substring(0,1).toUpperCase() + speciesLower.substring(1);
            String uid = "forestry.species" + keyName;
            IAllele allele = AlleleManager.alleleRegistry.getAllele(uid);
            if (!(allele instanceof IAlleleBeeSpecies)) return ItemStack.EMPTY;

            IAlleleBeeSpecies sp = (IAlleleBeeSpecies) allele;
            IAllele[] template = beeRoot.getTemplate(sp);
            if (template == null) return ItemStack.EMPTY;

            IBeeGenome genome = beeRoot.templateAsGenome(template);
            IBee bee = beeRoot.getBee(genome);
            switch (type) {
                case PRINCESS:
                    return beeRoot.getMemberStack(bee, EnumBeeType.PRINCESS);
                case DRONE:
                    return beeRoot.getMemberStack(bee, EnumBeeType.DRONE);
                case QUEEN:
                    return beeRoot.getMemberStack(bee, EnumBeeType.QUEEN);
                default:
                    return ItemStack.EMPTY;
            }
        };

        // Parents list
        String[][] pairs = new String[][]{
            {"noble", "majestic"},
            {"tropical", "exotic"},
            {"meadows", "diligent"},
            {"meadows", "common"},
            {"diligent", "unweary"},
            {"wintry", "industrious"}
        };

        // Insert some labware if present
        Item labwareItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("gendustry", "labware"));
        if (labwareItem != null) {
            ItemStack lab = new ItemStack(labwareItem, 32);
            inserted += putNext(inv, lab);
        }

        for (String[] pp : pairs) {
            ItemStack princess = make.apply(pp[0], EnumBeeType.PRINCESS);
            ItemStack drone = make.apply(pp[1], EnumBeeType.DRONE);

            if (!princess.isEmpty()) inserted += putNext(inv, princess);

            if (!drone.isEmpty()) {
                drone.setCount(4);
                inserted += putNext(inv, drone);
            }
        }

        if (inserted > 0) inv.markDirty();

        return inserted;
    }

    private int putNext(IInventory inv, ItemStack stack) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) {
                inv.setInventorySlotContents(i, stack);
                return 1;
            }
        }

        return 0;
    }
}
