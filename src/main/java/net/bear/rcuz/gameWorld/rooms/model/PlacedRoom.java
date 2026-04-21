package net.bear.rcuz.gameWorld.rooms.model;

public record PlacedRoom(
        String templateId,
        IVec3 origin,    /// world min corner
        int rotY,        /// 0, 90, 180, 270
        net.minecraft.util.math.Vec3i size,
        DoorSocket usedEntry
) {
}
