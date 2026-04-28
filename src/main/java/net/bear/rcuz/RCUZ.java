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
    public static final Path mobTagPresetsDir = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("rcuz")
            .resolve("mobTagPresets");
    public static final Path rulesDir = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("rcuz")
            .resolve("rules");
    public static final Path gameWorldRulesFile = rulesDir.resolve("gameWorld.json");

	@Override
	public void onInitialize() {
        try {
            Files.createDirectories(structureDir);
            Files.createDirectories(roomDir);
            Files.createDirectories(mobTagPresetsDir);
            Files.createDirectories(rulesDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        RCUZBlocks.init();
        LOGGER.info("RCUZ HERE!!!!!!");
        CommandRegistry.start();


//        ServerTickEvents.END_SERVER_TICK.register(minecraftServer -> {
//            for (GameWorld gameWorld : GameWorld.gameWorldList) {
//                gameWorld.getRoomGenerator().tick();
//            }
//        });


	}
}
