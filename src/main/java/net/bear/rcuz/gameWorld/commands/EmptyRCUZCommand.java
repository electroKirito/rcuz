package net.bear.rcuz.gameWorld.commands;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class EmptyRCUZCommand {
    public static int run(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(() -> Text.literal("§cCalled /rcuz with no arguments :(§r"), false);
        return 1;
    }
}
