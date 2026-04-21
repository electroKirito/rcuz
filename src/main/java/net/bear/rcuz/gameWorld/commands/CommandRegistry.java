package net.bear.rcuz.gameWorld.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.bear.rcuz.RCUZ;
import net.bear.rcuz.gameWorld.GameWorld;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.apache.logging.log4j.core.jmx.Server;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class CommandRegistry {
    public static void start() {
        CommandRegistrationCallback.EVENT.register((CommandRegistry::register));
    }

    private static final SuggestionProvider<ServerCommandSource> WORLD_ID_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestMatching(
                    GameWorld.gameWorldList.stream().map(w -> String.valueOf(w.getWorldId())),
                    builder
            );

    @FunctionalInterface
    private interface ThrowingCommand {
        int run(CommandContext<ServerCommandSource> ctx) throws Exception;
    }

    private static Command<ServerCommandSource> safeExec(String commandName, ThrowingCommand command) {
        return ctx -> {
            try {
                return command.run(ctx);
            } catch (Exception e) {
                RCUZ.LOGGER.error("Command failed: {} | input='{}'", commandName, ctx.getInput(), e);
                throw new RuntimeException(e);
            }
        };
    }

    private static void register(
            CommandDispatcher<ServerCommandSource> commandDispatcher,
            CommandRegistryAccess commandRegistryAccess,
            CommandManager.RegistrationEnvironment registrationEnvironment
    ) {
        LiteralArgumentBuilder<ServerCommandSource> createDimCommand = literal("create_dim")
                .then(argument("name", StringArgumentType.string())
                        .executes(safeExec("rcuz world create_dim", ctx ->
                                WorldRCUZCommands.create(
                                        ctx,
                                        StringArgumentType.getString(ctx, "name")
                                ))));

        LiteralArgumentBuilder<ServerCommandSource> getDimListCommand = literal("get_dim_list")
                .executes(safeExec("rcuz world get_dim_list", WorldRCUZCommands::getWorldList));

        LiteralArgumentBuilder<ServerCommandSource> tpCommand = literal("tp")
                .then(argument("id", StringArgumentType.word())
                        .suggests(WORLD_ID_SUGGESTIONS)
                        .executes(safeExec("rcuz world tp", ctx ->
                                WorldRCUZCommands.teleportToGameDimension(
                                        ctx,
                                        StringArgumentType.getString(ctx, "id")
                                ))));

        LiteralArgumentBuilder<ServerCommandSource> createRoom = literal("create_room")
                .executes(safeExec("rcuz debug create_room", DebugRCUZCommand::createRoom));

        LiteralArgumentBuilder<ServerCommandSource> worldCommand = literal("world")
                .then(createDimCommand)
                .then(getDimListCommand)
                .then(tpCommand);

        LiteralArgumentBuilder<ServerCommandSource> debugCommand = literal("debug")
                .then(createRoom);

        commandDispatcher.register(
                literal("rcuz")
                        .then(worldCommand)
                        .then(debugCommand)
        );
    }
}
