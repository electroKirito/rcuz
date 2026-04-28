package net.bear.rcuz.gameWorld.rooms.model;

import java.util.List;

public record DoorSocket(
        int id,
        IVec3 localPos,
        Dir facing,
        boolean exit,
        boolean entrance,
        DoorType doorType,
        boolean onlyBoostedTags,
        List<ContinuationBoostByTag> continuationBoostByTags
) {}
