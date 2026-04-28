package net.bear.rcuz.gameWorld.rooms.model;

import java.util.List;
import java.util.Set;

public record RoomTemplate(
        String id,
        int weight,
        int maxPerDimension, /// -1 = без лимита
        int maxDistance,
        int minDistance,
        int yMax,
        int yMin,
        Set<String> tags,
        List<RoomMobSpawner> mobSpawners,
        List<DoorSocket> doors,
        List<ReplaceRule> replacements
) {
    /**
     * Создает копию шаблона с измененным весом.
     */
    public RoomTemplate withWeight(int newWeight) {
        return new RoomTemplate(
                this.id,
                newWeight,
                this.maxPerDimension,
                this.maxDistance,
                this.minDistance,
                this.yMax,
                this.yMin,
                this.tags,
                this.mobSpawners,
                this.doors,
                this.replacements
        );
    }

}
