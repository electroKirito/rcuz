package net.bear.rcuz.gameWorld.rooms.model;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.List;

public record RoomMobSpawners(
    MobSpawnerTypes type,
    List<RoomEntity> mobSpawners
) {}
