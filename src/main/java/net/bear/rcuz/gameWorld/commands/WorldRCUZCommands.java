package net.bear.rcuz.gameWorld.commands;

import com.mojang.brigadier.context.CommandContext;
import net.bear.rcuz.gameWorld.DimencionsRegistration;
import net.bear.rcuz.gameWorld.GameWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class WorldRCUZCommands {
     public static int teleportToGameDimension(CommandContext<ServerCommandSource> ctx, String id) {
         ServerCommandSource source = ctx.getSource();
         PlayerEntity player = source.getPlayer();
         assert player != null;

         Optional<GameWorld> a =  GameWorld.gameWorldList.stream().filter(world ->
                         Objects.equals(id, String.valueOf(world.getWorldId()))
                 ).findFirst();
         if (a.isEmpty()) return 0;
         GameWorld b = a.get();

         player.teleport(
                 b.getWorld(),
                 0.5,2, 0.5,
                 null, 0, 0
         );


        return 1;
     }

    public static int create(CommandContext<ServerCommandSource> ctx, String name) throws IOException {
         GameWorld newWorld = new GameWorld(ctx.getSource().getServer(), name);
         ctx.getSource().sendFeedback(
                () -> Text.literal("Новый мир: " + newWorld.getWorldId()),
                false);
         return 1;
    }

    public static int getWorldList(CommandContext<ServerCommandSource> ctx) {

         if (GameWorld.gameWorldList.isEmpty()) {
             ctx.getSource().sendFeedback(
                     () -> Text.literal("Нет миров которые были созданы.. \"/rcuz world create_dim\""),
                     false);
         }

         for (GameWorld world : GameWorld.gameWorldList) {
             ctx.getSource().sendFeedback(() -> Text.literal("WorldId: %s | %s".formatted(world.getWorldId(), world.getDisplayName())), false);
         }
         return 1;
    }
}
