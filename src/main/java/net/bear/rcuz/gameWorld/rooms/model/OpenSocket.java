package net.bear.rcuz.gameWorld.rooms.model;

import net.minecraft.util.math.BlockPos;

import java.util.List;

public record OpenSocket(
        BlockPos worldPos,
        Dir facing,
        boolean entrance,
        DoorType doorType,
        boolean onlyBoostedTags,
        List<ContinuationBoostByTag> continuationBoostByTags,
        List<BlockPos> sealedBlocks
) {}
