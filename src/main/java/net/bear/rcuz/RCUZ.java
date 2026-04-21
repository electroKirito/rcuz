package net.bear.rcuz;

import net.bear.rcuz.gameWorld.GameWorld;
import net.bear.rcuz.gameWorld.commands.CommandRegistry;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RCUZ implements ModInitializer {
	public static final String MOD_ID = "rcuz";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Path structureDir = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("rcuz")
            .resolve("structures");
    public static final Path roomDir = FabricLoader.getInstance()
                                        .getConfigDir()
                                        .resolve("rcuz")
                                        .resolve("rooms");

	@Override
	public void onInitialize() {
        try {
            Files.createDirectories(roomDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("RCUZ HERE!!!!!!");
        CommandRegistry.start();

//        ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> {
//            for (GameWorld gameWorld : GameWorld.gameWorldList) {
//                gameWorld.getRoomGenerator().tick();
//            }
//        });


	}
}