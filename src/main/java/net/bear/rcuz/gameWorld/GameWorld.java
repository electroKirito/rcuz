package net.bear.rcuz.gameWorld;

import net.bear.rcuz.RCUZ;
import net.bear.rcuz.gameWorld.chunkGenerator.EmptyChunkGenerator;
import net.bear.rcuz.gameWorld.rooms.gen.MobLoader;
import net.bear.rcuz.gameWorld.rooms.gen.RoomGenerator;
import net.bear.rcuz.gameWorld.rooms.gen.RoomLoader;
import net.bear.rcuz.gameWorld.rooms.model.MobSpawnEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.dimension.DimensionTypes;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeWorldConfig;
import xyz.nucleoid.fantasy.RuntimeWorldHandle;

import java.io.IOException;
import java.util.*;

public class GameWorld {

    public static final List<GameWorld> gameWorldList = new ArrayList<>();

    private MinecraftServer server;
    private int worldId;
    private ServerWorld world;
    private int logCount;
    private String displayName;
    private RoomGenerator roomGenerator;
    private final ConfigStructureLoader structureLoader = new ConfigStructureLoader();

    public GameWorld(MinecraftServer server, String name) throws IOException {

        this.worldId = MathHelper.nextInt(Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getRandom(), 1000000000, Integer.MAX_VALUE - 10);
        this.server = server;
        this.createWorld();
        structureLoader.reload(server);
        Map<String, List<MobSpawnEntry>> mobTagPresets = new MobLoader().loadAll();
        roomGenerator = new RoomGenerator(new RoomLoader().loadAll(), Random.create(), world, structureLoader, mobTagPresets);
        logCount = 0;
        this.setDisplayName(name);
        this.placeLobby();
        gameWorldList.add(this);
    }

    private void createWorld() {
        Fantasy fantasy = Fantasy.get(this.server);

        BiomeSource overwolrdBiomeSource = Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getChunkManager().getChunkGenerator().getBiomeSource();

        RuntimeWorldConfig config = new RuntimeWorldConfig()
                .setDimensionType(DimensionTypes.OVERWORLD)
                .setGenerator(new EmptyChunkGenerator(overwolrdBiomeSource))
                .setShouldTickTime(false);

        Identifier worldId = new Identifier(RCUZ.MOD_ID, "rcuz_" + this.getWorldId());
        RuntimeWorldHandle worldHandle = fantasy.getOrOpenPersistentWorld(worldId, config);

        try {
            world = worldHandle.asWorld();
            RCUZ.LOGGER.info("after asWorld");
            applyWorldRulesFromConfig();
        } catch (Throwable e) {
            RCUZ.LOGGER.error("createWorld failed", e);
            throw new RuntimeException(e);
        }
    }

    private void applyWorldRulesFromConfig() throws IOException {
        GameWorldRulesLoader loader = new GameWorldRulesLoader();
        Map<String, String> rules = loader.loadAll(world.getGameRules());
        if (rules.isEmpty()) {
            return;
        }

        GameRules worldRules = world.getGameRules();
        Map<String, GameRules.Key<?>> keys = loader.collectRuleKeys();

        for (Map.Entry<String, String> entry : rules.entrySet()) {
            String rule = entry.getKey();
            String value = entry.getValue();
            if (rule == null || rule.isBlank() || value == null || value.isBlank()) {
                continue;
            }

            GameRules.Key<?> key = keys.get(rule);
            if (key == null) {
                RCUZ.LOGGER.warn("Unknown gamerule '{}' in {}", rule, RCUZ.gameWorldRulesFile);
                continue;
            }

            GameRules.Rule<?> targetRule = worldRules.get(key);
            try {
                if (targetRule instanceof GameRules.BooleanRule booleanRule) {
                    booleanRule.set(Boolean.parseBoolean(value), server);
                } else if (targetRule instanceof GameRules.IntRule intRule) {
                    intRule.set(Integer.parseInt(value), server);
                }
            } catch (Exception ex) {
                RCUZ.LOGGER.warn("Failed to apply gamerule '{}'='{}': {}", rule, value, ex.getMessage());
            }
        }
    }


    public RoomGenerator getRoomGenerator() {
        return roomGenerator;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public int getWorldId() {
        return worldId;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void nameTagWorld(String displayName) {
        setDisplayName(displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    private void log() {
        logCount++;
        RCUZ.LOGGER.info("Create Dim Pass %s".formatted(logCount));
    }

    private void placeLobby() {
        /// Инициализация
        Identifier structureId = new Identifier(RCUZ.MOD_ID, "lobby");
        StructureTemplate template = this.getStructureTemplate(structureId);
        StructurePlacementData placementData = new StructurePlacementData();

        /// Asserts _ N1
        assert template != null;


        /// Настройка структуры
        //Размещение со смещением
        Vec3i templateSize = template.getSize();
        BlockPos origin = new BlockPos(-templateSize.getX() / 2,0,-templateSize.getZ() / 2); // Ровно по центру

        // Настройка установки
        placementData
                .setMirror(BlockMirror.NONE)
                .setRotation(BlockRotation.NONE)
                .setIgnoreEntities(false);

        /// Установка Структуры
        template.place(this.world, origin, origin, placementData, this.world.getRandom(), 2);

        /// Сохранение структуры
        roomGenerator.addLobbyRoom(
               origin, templateSize
        );

        /// Просто чтоб спавн был там где нужно
        this.world.setSpawnPos(new BlockPos(0,2,0), 0.0f);
    }

    private StructureTemplate getStructureTemplate(Identifier structureId) {
        Optional<StructureTemplate> templateOpt = this.world.getStructureTemplateManager().getTemplate(structureId);

        if (templateOpt.isEmpty()) {
            RCUZ.LOGGER.error("UHH.. are u ok?, no structure {}", structureId);
            return null;
        }

        return templateOpt.get();
    }

}
