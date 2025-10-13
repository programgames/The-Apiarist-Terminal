package net.ocgendustry.driver;

import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;
import net.bdew.gendustry.machines.advmutatron.TileMutatronAdv;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.ocgendustry.Log;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DriverAdvMutatron extends DriverSidedTileEntity {
    
    @Override
    public Class<?> getTileEntityClass() {
        return TileMutatronAdv.class;
    }

    @Override
    public boolean worksWith(World world, BlockPos pos, EnumFacing side) {
        return world.getTileEntity(pos) instanceof TileMutatronAdv;
    }

    @Override
    public ManagedEnvironment createEnvironment(World world, BlockPos pos, EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileMutatronAdv) {
            return new Environment((TileMutatronAdv) te);
        }
        return null;
    }

    public static final class Environment extends AbstractManagedEnvironment implements NamedBlock {
        private final TileMutatronAdv tile;
        private final String componentName = "advmutatron";

        public Environment(TileMutatronAdv tile) {
            this.tile = tile;
            setNode(Network.newNode(this, Visibility.Network)
                .withComponent(componentName, Visibility.Network)
                .create());
        }

        @Override
        public String preferredName() {
            return componentName;
        }

        @Override
        public int priority() {
            return 10;  // Higher than generic energy driver (0)
        }

        private Map<Integer, ItemStack> getPossibleMutations() {
            return tile.getPossibleMutations();
        }

        private void setMutation(int key) {
            tile.setMutation(key);
        }

        private boolean tryStart() {
            return tile.tryStart();
        }

        @Callback(doc = "function():table -- Returns a table of possible mutations keyed by 1..N with fields {index,key,name,label?,nbt?}.")
        public Object[] listMutations(Context ctx, Arguments args) {
            try {
                Map<Integer, ItemStack> map = getPossibleMutations();
                LinkedHashMap<Integer, Object> out = new LinkedHashMap<>();

                if (map != null) {
                    int i = 0;

                    for (Map.Entry<Integer, ItemStack> e : map.entrySet()) {
                        ItemStack stack = e.getValue();
                        if (stack == null || stack.isEmpty()) continue;

                        i++;

                        LinkedHashMap<String, Object> info = new LinkedHashMap<>();
                        info.put("index", i);
                        info.put("key", e.getKey());

                        try {
                            if (stack.getItem() != null && stack.getItem().getRegistryName() != null) {
                                info.put("name", stack.getItem().getRegistryName().toString());
                            }

                            // Avoid calling getDisplayName() on genetic items with no genome NBT, which causes Forestry to log spam.
                            if (stack.hasTagCompound() && stack.getTagCompound() != null) {
                                info.put("label", stack.getDisplayName());
                                info.put("nbt", stack.getTagCompound().toString());
                            }
                        } catch (Throwable ignored) {}

                        out.put(i, info);
                    }
                }

                return new Object[]{ out };
            } catch (Throwable t) {
                return new Object[]{ new LinkedHashMap<>() };
            }
        }

        @Callback(doc = "function(n:number):boolean,string? -- Select a mutation by 1-based index (from listMutations) or by raw key (slot index), and start the process if possible.")
        public Object[] setMutation(Context ctx, Arguments args) {
            try {
                int n = args.checkInteger(0);

                Map<Integer, ItemStack> map = getPossibleMutations();
                if (map == null || map.isEmpty()) {
                    return new Object[]{ false, "no mutations available" };
                }

                int size = map.size();
                int keyToUse = n;
                if (n >= 1 && n <= size) {
                    int i = 0;
                    for (Integer k : map.keySet()) {
                        i++;

                        if (i == n) {
                            keyToUse = k;
                            break;
                        }
                    }
                }

                if (!map.containsKey(keyToUse)) {
                    return new Object[]{ false, "invalid index/key" };
                }

                setMutation(keyToUse);

                return new Object[]{ true };
            } catch (Throwable t) {
                return new Object[]{ false, t.toString() };
            }
        }

        @Callback(doc = "function():boolean -- Try to start processing immediately; returns true if started.")
        public Object[] start(Context ctx, Arguments args) {
            try {
                boolean started = tryStart();

                return new Object[]{ started };
            } catch (Throwable t) {
                return new Object[]{ false };
            }
        }
    }
}
