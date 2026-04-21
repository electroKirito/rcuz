package net.bear.rcuz.gameWorld.rooms.model;

import java.util.List;
import java.util.Set;

public record RoomTemplate(
        String id,
        int sizeX, int sizeY, int sizeZ,
        int weight,
        int maxPerDimension, /// -1 = без лимита
        int maxDistance,
        int minDistance,
        Set<String> tags,
        List<RoomMobSpawners> mobSpawners,
        List<DoorSocket> doors
) {}
