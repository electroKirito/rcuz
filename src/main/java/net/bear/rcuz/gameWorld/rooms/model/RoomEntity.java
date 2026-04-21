package net.bear.rcuz.gameWorld.rooms.model;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

public record RoomEntity(
        Identifier entityId,
        NbtCompound entityNbt
) {}
