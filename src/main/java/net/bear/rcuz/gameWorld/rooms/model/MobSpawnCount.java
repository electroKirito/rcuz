package net.bear.rcuz.gameWorld.rooms.model;

import net.minecraft.util.math.random.Random;

public record MobSpawnCount(
        int min,
        int max
) {
    public int getRandCount(Random random) {
        return random.nextBetween(this.min(), this.max());
    }
}
