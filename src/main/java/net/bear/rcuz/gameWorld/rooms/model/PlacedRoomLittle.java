package net.bear.rcuz.gameWorld.rooms.model;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

public record PlacedRoomLittle(
        Vec3i size,
        BlockPos pos,
        boolean placed
) {
}
