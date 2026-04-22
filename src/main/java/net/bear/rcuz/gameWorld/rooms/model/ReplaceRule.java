package net.bear.rcuz.gameWorld.rooms.model;

import net.minecraft.util.Identifier;

public record ReplaceRule(
        Identifier from,
        Identifier to,
        float chance
) {
}
