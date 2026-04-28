package net.bear.rcuz.gameWorld.rooms.model;

import java.util.List;

public record ContinuationBoostByTag(
        List<String> tags,
        BoostType boostType,
        float value
) {}
