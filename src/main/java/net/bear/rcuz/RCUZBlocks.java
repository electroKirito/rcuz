package net.bear.rcuz;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class RCUZBlocks {
    public static final Block A_PIT = register("a_pit",
            new Block(FabricBlockSettings.create().strength(0.5f).nonOpaque()));
    public static final Block B_PIT = register("b_pit",
            new Block(FabricBlockSettings.create().strength(0.5f).nonOpaque()));
    public static final Block DOOR_SEAL = register("door_seal",
            new Block(FabricBlockSettings.create().strength(0.5f).nonOpaque()));

    private static Block register(String id, Block block) {
        Registry.register(Registries.BLOCK, new Identifier(RCUZ.MOD_ID, id), block);
        Registry.register(Registries.ITEM, new Identifier(RCUZ.MOD_ID, id), new BlockItem(block, new FabricItemSettings()));
        return block;
    }

    public static void init() {}
}
