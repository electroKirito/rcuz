package net.bear.rcuz.gameWorld.commands;

import com.mojang.brigadier.context.CommandContext;
import net.bear.rcuz.gameWorld.GameWorld;
import net.minecraft.server.command.ServerCommandSource;

public class DebugRCUZCommand {
    public static int createRoom(CommandContext<ServerCommandSource> ctx) {
        for (GameWorld gameWorld : GameWorld.gameWorldList) {
            for (int i = 0; i < 5; i++) gameWorld.getRoomGenerator().tick();
        }
        return 1;
    }
}
