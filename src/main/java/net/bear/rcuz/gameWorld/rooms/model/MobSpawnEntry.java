package net.bear.rcuz.gameWorld.rooms.model;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

public record MobSpawnEntry(
        Identifier id,
        NbtCompound tag,
        MobSpawnCount count
) {

}
