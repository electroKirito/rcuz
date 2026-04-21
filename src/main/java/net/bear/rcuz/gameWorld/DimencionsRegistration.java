package net.bear.rcuz.gameWorld;

import net.bear.rcuz.RCUZ;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;

import java.util.OptionalLong;

public class DimencionsRegistration {
    public static final RegistryKey<DimensionOptions> GAME_WORLD_KEY = RegistryKey.of(RegistryKeys.DIMENSION,
            new Identifier(RCUZ.MOD_ID, "Game_world"));
    public static final RegistryKey<World> GAME_WORLD_LEVEL_KEY = RegistryKey.of(RegistryKeys.WORLD,
            new Identifier(RCUZ.MOD_ID, "Game_world"));
    public static final RegistryKey<DimensionType> GAME_WORLD_DIM_TYPE = RegistryKey.of(RegistryKeys.DIMENSION_TYPE,
            new Identifier(RCUZ.MOD_ID, "Game_world_type"));

    public static void bootstrapType(Registerable<DimensionType> contex) {
        contex.register(GAME_WORLD_DIM_TYPE, new DimensionType(
                OptionalLong.of(12000),
                false,
                false,
                false,
                true,
                1.0,
                true,
                false,
                -1024,
                2048,
                2048,
                BlockTags.INFINIBURN_OVERWORLD,
                DimensionTypes.OVERWORLD_ID,
                1.0f,
                new DimensionType.MonsterSettings(false, false, UniformIntProvider.create(0,0), 0)
        ));
    }
}
