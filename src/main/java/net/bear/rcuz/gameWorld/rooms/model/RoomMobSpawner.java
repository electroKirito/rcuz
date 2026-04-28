package net.bear.rcuz.gameWorld.rooms.model;

import java.util.List;

public record RoomMobSpawner(
        MobSpawnMode mode,
        IVec3 pos,
        int rolls,
        List<MobSpawnPoolEntry> pool
) {}
