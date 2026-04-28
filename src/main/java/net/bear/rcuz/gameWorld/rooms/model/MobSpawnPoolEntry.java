package net.bear.rcuz.gameWorld.rooms.model;

import java.util.List;

public record MobSpawnPoolEntry(
        int weight,
        boolean empty,
        String tagPreset,
        List<MobSpawnEntry> spawn
) {}
