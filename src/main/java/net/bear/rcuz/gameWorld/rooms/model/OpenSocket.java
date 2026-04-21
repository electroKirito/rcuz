package net.bear.rcuz.gameWorld.rooms.model;

import net.minecraft.util.math.BlockPos;

import java.util.List;

public record OpenSocket(
        BlockPos worldPos,      // мировая позиция центра/низа проёма
        Dir facing,          // куда смотрит дверь (из текущей комнаты наружу)
        List<ContinuationBoostByTag> continuationBoostByTags   //прибаф к весам
) {}
