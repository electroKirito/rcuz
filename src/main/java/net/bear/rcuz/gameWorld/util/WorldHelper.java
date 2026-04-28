package net.bear.rcuz.gameWorld.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class WorldHelper {
    public static int getDificultDistanceSquared(Vec3d pos) {
        // Просто сумма квадратов, без Math.sqrt
        return (int) (pos.x * pos.x + pos.y * pos.y + pos.z * pos.z);
    }
}
