package net.bear.rcuz.gameWorld.rooms.gen;

import net.bear.rcuz.RCUZ;
import net.bear.rcuz.gameWorld.ConfigStructureLoader;
import net.bear.rcuz.gameWorld.rooms.model.*;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;

import java.util.*;

public class RoomGenerator {


    /// ТУНЕЛЬЧИК
    private static final int CONNECT_MAX_FORWARD = 20;
    private static final int CONNECT_SEARCH_H = 10;
    private static final int CONNECT_SEARCH_V = 10;
    private static final int TUNNEL_RX = 4;
    private static final int TUNNEL_RY = 4;
    private static final int TUNNEL_RZ = 4;
    /// КОНЕЦ ТУНЕЛЬЧИКА :(


    private final Map<String, RoomTemplate> templatesById;
    private final Random random;
    private final Map<String, Integer> usedCount = new HashMap<>();
    private final List<PlacedRoom> placedRooms = new ArrayList<>();
    private final Deque<OpenSocket> frontier = new ArrayDeque<>();
    private ConfigStructureLoader configStructureLoader;

    private int tickCounter = 0;
    private static final int GENERATE_EVERY_TICKS = 5;
    private static final int MAX_ROOMS_PER_TRIGGER = 1;
    private static final int MAX_ROOMS_TO_TRY_PER_SOCKET = 20;
    private static final int MAX_ENTRANCES_TO_TRY_PER_ROOM = 6;

    private final Map<Long, List<Bounds3i>> placedBoundsByChunk = new HashMap<>();


    private final ServerWorld world;

    private static final int ACTIVATE_RADIUS = 100;
    private static final int ACTIVATE_RADIUS_SQ = ACTIVATE_RADIUS * ACTIVATE_RADIUS;

    public RoomGenerator(List<RoomTemplate> templates, Random random, ServerWorld world, ConfigStructureLoader configStructureLoader) {
        this.configStructureLoader = configStructureLoader;
        this.world = world;
        this.templatesById = new HashMap<>();
        this.random = random;

        for (RoomTemplate t : templates) {
            this.templatesById.put(t.id(), t);
        }
    }

    public List<PlacedRoom> getPlacedRooms() {
        return Collections.unmodifiableList(placedRooms);
    }

    public void addLobbyRoom(BlockPos origin, Vec3i templateSize) {
        PlacedRoom lobby = new PlacedRoom("lobby",
                new IVec3(origin.getX(), origin.getY(), origin.getZ()),
                0,
                templateSize,
                null);
        placedRooms.add(lobby);
        indexPlacedRoomBounds(computeBounds(origin, templateSize, BlockRotation.NONE));

        frontier.add(new OpenSocket(origin.add(0, 1, 15), Dir.WEST, new ArrayList<>()));
        frontier.add(new OpenSocket(origin.add(25, 1, 0), Dir.NORTH, new ArrayList<>()));
        frontier.add(new OpenSocket(origin.add(32, 8, 3), Dir.EAST, new ArrayList<>()));
    }

    public void start() {
//        for (int i = 0; i < 3; i++) {
//            OpenSocket socket = pollActiveSocket();
//            if (socket == null) {
//                return;
//            }
//            generateRoom(socket);
//        }
    }

    private boolean tryGenerateAtSocket(OpenSocket socket) {
        List<RoomTemplate> candidates = new ArrayList<>(templatesById.values());

        int roomTryes = 0;
        while (!candidates.isEmpty() && roomTryes < MAX_ROOMS_TO_TRY_PER_SOCKET) {
            roomTryes++;

            RoomTemplate room = pickRoomWeighted(candidates);
            if (room == null) break;

            boolean placed = tryPlaceRoomVariantes(room, socket);
            if (placed) return true;

            candidates.remove(room);
        }
        return false;
    }

    private boolean tryPlaceRoomVariantes(RoomTemplate room, OpenSocket socket) {
        StructureTemplate template = configStructureLoader.get(room.id());
        if (template == null) return false;

        List<DoorSocket> entrances = getEntrances(room); /// Все входы
        Collections.shuffle(entrances, new java.util.Random(random.nextLong())); /// Перемешиваем входы 😈

        int entrancesTryCount = 0;
        for (DoorSocket doorSocket : entrances) {
            if (entrancesTryCount++ >= MAX_ENTRANCES_TO_TRY_PER_ROOM) break;

            Placement placement = computePlacement(socket, doorSocket);
            if (!canPlaceRoomAt(template, placement.origin, placement.rotation)) continue;

            placeRoom(room, template, doorSocket, placement.origin, placement.rotation);
            return true;
        }

        if (tryConnectToNearbySocket(socket)) {
            return true;
        }
        sealSocketWithBricks(socket);
        return false;
    }

    private boolean tryConnectToNearbySocket(OpenSocket source) {
        OpenSocket target = scanForwardForTarget(source);
        if (target == null) {
            return false;
        }

        BlockPos from = source.worldPos().offset(toDirection(source.facing()));
        BlockPos to = target.worldPos().offset(toDirection(target.facing()));

        carveNoisyTunnel(from, to);

        frontier.remove(target);
        return true;
    }

    private void carveNoisyTunnel(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps == 0) return;

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;

            int cx = (int) Math.round(from.getX() + dx * t);
            int cy = (int) Math.round(from.getY() + dy * t);
            int cz = (int) Math.round(from.getZ() + dz * t);

            carveNoisySection(new BlockPos(cx, cy, cz));
        }
    }

    private void carveNoisySection(BlockPos center) {
        for (int ox = -TUNNEL_RX; ox <= TUNNEL_RX; ox++) {
            for (int oy = -TUNNEL_RY; oy <= TUNNEL_RY; oy++) {
                for (int oz = -TUNNEL_RZ; oz <= TUNNEL_RZ; oz++) {

                    double nx = (double) ox / TUNNEL_RX;
                    double ny = (double) oy / TUNNEL_RY;
                    double nz = (double) oz / TUNNEL_RZ;
                    double dist = nx * nx + ny * ny + nz * nz;

                    BlockPos p = center.add(ox, oy, oz);
                    double noise = noise01(p.getX(), p.getY(), p.getZ()); // 0..1
                    double threshold = 1.0 + (noise - 0.5) * 0.45;        // неровности

                    if (dist <= threshold) {
                        world.setBlockState(p, Blocks.CAVE_AIR.getDefaultState());
                    } else if (dist <= threshold + 0.20) {
                        // тонкая каменная "стенка" рядом с вырезом
                        if (world.getBlockState(p).isAir()) {
                            world.setBlockState(p, Blocks.STONE.getDefaultState());
                        }
                    }
                }
            }
        }
    }

    private double noise01(int x, int y, int z) {
        long h = 1469598103934665603L;
        h ^= x * 0x9E3779B185EBCA87L;
        h ^= y * 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x165667B19E3779F9L;
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);

        long v = h & 0x7fffffffffffffffL;
        return (double) v / (double) Long.MAX_VALUE;
    }

    private OpenSocket scanForwardForTarget(OpenSocket source) {
        Direction direction = toDirection(source.facing());
        Dir opositeFacing = opposite(source.facing());

        for (int forward = 1; forward <= CONNECT_MAX_FORWARD; forward++) {
            BlockPos center = source.worldPos().offset(direction, forward);

            OpenSocket found = findSocketInSection(center, source, opositeFacing);
            if (found != null) return found; ///Найдена дверь
            if (isScanSectionAir(center, direction)) return null; ///Не пусто :<


        }

        return null;
    }

    private OpenSocket findSocketInSection(BlockPos center, OpenSocket source, Dir requiredFacing) {
        Direction forwardDir = toDirection(source.facing());
        OpenSocket best = null;

        int bestScore = Integer.MAX_VALUE;

        for (OpenSocket candidate : frontier) {
            if (candidate.facing() != requiredFacing) continue;

            BlockPos pos = candidate.worldPos();
            int dx = pos.getX() - center.getX();
            int dy = pos.getY() - center.getY();
            int dz = pos.getZ() - center.getZ();

            if (Math.abs(dy) > CONNECT_SEARCH_V) continue;
            if (Math.abs(dx) > CONNECT_SEARCH_H || Math.abs(dz) > CONNECT_SEARCH_H) continue;

            int score = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);

            int globalForward = projectForward(source.worldPos(), pos, forwardDir);
            if (globalForward <= 0) continue;

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private int projectForward(BlockPos from, BlockPos to, Direction forwardDir) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();

        return switch (forwardDir) {
            case EAST -> dx;
            case WEST -> -dx;
            case SOUTH -> dz;
            case NORTH -> -dz;
            default -> 0;
        };
    }

    private boolean isScanSectionAir(BlockPos center, Direction forwardDir) {
        if (forwardDir == Direction.EAST || forwardDir == Direction.WEST) {
            /// Плоскость Y-Z, X фиксирован
            int x = center.getX();
            for (int dy = -CONNECT_SEARCH_V; dy <= CONNECT_SEARCH_V; dy++) {
                for (int dz = -CONNECT_SEARCH_H; dz <= CONNECT_SEARCH_H; dz++) {
                    BlockPos p = new BlockPos(x, center.getY() + dy, center.getZ() + dz);
                    if (!world.getBlockState(p).isAir()) return false;
                }
            }
        } else {
            /// Плоскость Y-X, Z фиксирован
            int z = center.getZ();
            for (int dy = -CONNECT_SEARCH_V; dy <= CONNECT_SEARCH_V; dy++) {
                for (int dx = -CONNECT_SEARCH_H; dx <= CONNECT_SEARCH_H; dx++) {
                    BlockPos p = new BlockPos(center.getX() + dx, center.getY() + dy, z);
                    if (!world.getBlockState(p).isAir()) return false;
                }
            }
        }
        return true;
    }


    private void placeRoom(
            RoomTemplate roomTemplate,
            StructureTemplate template,
            DoorSocket selectedEntrance,
            BlockPos origin,
            BlockRotation rotation
    ) {
        Vec3i templateSize = template.getSize();

        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(rotation)
                .setIgnoreEntities(false);

        template.place(world, origin, origin, placementData, world.getRandom(), 2);

        PlacedRoom placed = new PlacedRoom(
                roomTemplate.id(),
                new IVec3(origin.getX(), origin.getY(), origin.getZ()),
                rotationToDegrees(rotation),
                templateSize,
                selectedEntrance
        );

        placedRooms.add(placed);
        indexPlacedRoomBounds(computeBounds(origin, templateSize, rotation));

        addNewSocketsFromPlacedRoom(
                roomTemplate,
                selectedEntrance,
                origin,
                rotation,
                templateSize.getX(),
                templateSize.getZ()
        );
    }

    private boolean canPlaceRoomAt(StructureTemplate template, BlockPos origin, BlockRotation rotation) {
        Bounds3i bounds = computeBounds(origin, template.getSize(), rotation);

        if (intersectsPlacedRooms(bounds)) return false;

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!world.getBlockState(p).isAir()) return false;
                }
            }
        }
        return true;
    }

    private void sealSocketWithBricks(OpenSocket socket) {
        BlockPos p = socket.worldPos();
        Map<Block, Integer> nearBlocks = new HashMap<>();

        for (BlockPos blockPos : BlockPos.iterate(
                p.getX() - 1, p.getY() - 1, p.getZ() - 1,
                p.getX() + 1, p.getY() + 2, p.getZ() + 1
        )) {
            Block curBlock = world.getBlockState(blockPos).getBlock();
            if (curBlock == Blocks.AIR) continue;
            nearBlocks.merge(curBlock, 1, Integer::sum);
        }

        Block fill = nearBlocks.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Blocks.BRICKS);

        world.setBlockState(p, fill.getDefaultState());
        world.setBlockState(p.add(0,1,0), fill.getDefaultState());
    }

    private boolean intersectsPlacedRooms(Bounds3i bounds) {
        int minChunkX = bounds.minX >> 4;
        int maxChunkX = bounds.maxX >> 4;
        int minChunkZ = bounds.minZ >> 4;
        int maxChunkZ = bounds.maxZ >> 4;

        Set<Bounds3i> candidates = new HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                List<Bounds3i> list = placedBoundsByChunk.get(chunkKey(cx, cz));
                if (list != null) candidates.addAll(list);
            }
        }

        for (Bounds3i placed : candidates) {
            boolean overlapX = bounds.minX <= placed.maxX && bounds.maxX >= placed.minX;
            boolean overlapY = bounds.minY <= placed.maxY && bounds.maxY >= placed.minY;
            boolean overlapZ = bounds.minZ <= placed.maxZ && bounds.maxZ >= placed.minZ;

            if (overlapX && overlapY && overlapZ) return true;
        }

        return false;
    }

    private void indexPlacedRoom(PlacedRoom room) {
        BlockPos origin = new BlockPos(room.origin().x(), room.origin().y(), room.origin().z());
        BlockRotation rotation = rotationFromDegrees(room.rotY());
        indexPlacedRoomBounds(computeBounds(origin, room.size(), rotation));
    }

    private void indexPlacedRoomBounds(Bounds3i bounds) {
        int minChunkX = bounds.minX >> 4;
        int maxChunkX = bounds.maxX >> 4;
        int minChunkZ = bounds.minZ >> 4;
        int maxChunkZ = bounds.maxZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long key = chunkKey(cx, cz);
                placedBoundsByChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(bounds);
            }
        }
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private Bounds3i computeBounds(BlockPos origin, Vec3i size, BlockRotation rotation) {
        int maxX = size.getX() - 1;
        int maxY = size.getY() - 1;
        int maxZ = size.getZ() - 1;

        int minBx = Integer.MAX_VALUE;
        int minBy = Integer.MAX_VALUE;
        int minBz = Integer.MAX_VALUE;
        int maxBx = Integer.MIN_VALUE;
        int maxBy = Integer.MIN_VALUE;
        int maxBz = Integer.MIN_VALUE;

        for (int x : new int[]{0, maxX}) {
            for (int y : new int[]{0, maxY}) {
                for (int z : new int[]{0, maxZ}) {
                    IVec3 r = rotateLocalPos(new IVec3(x, y, z), rotation);
                    int wx = origin.getX() + r.x();
                    int wy = origin.getY() + r.y();
                    int wz = origin.getZ() + r.z();

                    if (wx < minBx) minBx = wx;
                    if (wy < minBy) minBy = wy;
                    if (wz < minBz) minBz = wz;
                    if (wx > maxBx) maxBx = wx;
                    if (wy > maxBy) maxBy = wy;
                    if (wz > maxBz) maxBz = wz;
                }
            }
        }

        return new Bounds3i(minBx, minBy, minBz, maxBx, maxBy, maxBz);
    }

    private RoomTemplate pickRoomWeighted(List<RoomTemplate> rooms) {
        int total = 0;
        for (RoomTemplate r : rooms) {
            if (r.weight() > 0) total += r.weight();
        }
        if (total <= 0) return null;

        int roll = random.nextInt(total);
        int acc = 0;
        for (RoomTemplate r : rooms) {
            if (r.weight() <= 0) continue;
            acc += r.weight();
            if (roll < acc) return r;
        }
        return null;
    }

    public boolean generateRoom(OpenSocket openSocket) {
        RoomTemplate roomTemplate = getRandomRoomTemplete();
        List<DoorSocket> entrances = getEntrances(roomTemplate);
        if (entrances.isEmpty()) {
            RCUZ.LOGGER.warn("Room '{}' has no entrance sockets", roomTemplate.id());
            return false;
        }

        DoorSocket selectedEntrance = entrances.get(random.nextInt((int) entrances.stream().filter(DoorSocket::entrance).count())); ///Я сделал фильтр чтоб не выбиралась дверь, которая не может быть входом
//        Identifier templateId = new Identifier("rcuz", roomTemplate.id());
//        Optional<StructureTemplate> optionalStructureTemplate = world.getStructureTemplateManager().getTemplate(templateId);

//        if (optionalStructureTemplate.isEmpty()) {
//            RCUZ.LOGGER.warn("No structure template for '{}'", roomTemplate.id());
//            return false;
//        }

        StructureTemplate template = configStructureLoader.get(roomTemplate.id());
        if (template == null) {
            RCUZ.LOGGER.warn("No config for '{}'", roomTemplate.id());
            return false;
        }


        ///  Большой ад с ротацией блоков
        BlockRotation rotation = getRotationToConnect(selectedEntrance.facing(), openSocket.facing());
        Vec3i templateSize = template.getSize();
        IVec3 rotatedEntryLocalPos = rotateLocalPos(
                selectedEntrance.localPos(),
                rotation
        );
        BlockPos entryAnchor = openSocket.worldPos().offset(toDirection(openSocket.facing()));

        BlockPos origin = entryAnchor.add(
                -rotatedEntryLocalPos.x(),
                -rotatedEntryLocalPos.y(),
                -rotatedEntryLocalPos.z()
        );

        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(rotation)
                .setIgnoreEntities(false);

        template.place(world, origin, origin, placementData, world.getRandom(), 2);

        PlacedRoom placedRoom = new PlacedRoom(
                roomTemplate.id(),
                new IVec3(origin.getX(), origin.getY(), origin.getZ()),
                rotationToDegrees(rotation),
                templateSize,
                selectedEntrance
        );

        indexPlacedRoom(placedRoom);
        placedRooms.add(placedRoom);

        addNewSocketsFromPlacedRoom(
                roomTemplate,
                selectedEntrance,
                origin,
                rotation,
                templateSize.getX(),
                templateSize.getZ()
        );
        return true;
    }

    public boolean isSocketActive(OpenSocket socket) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(
                    socket.worldPos().getX() + 0.5,
                    socket.worldPos().getY() + 0.5,
                    socket.worldPos().getZ() + 0.5
            ) <= ACTIVATE_RADIUS_SQ) {
                return true;
            }
        }
        return false;
    }

    public RoomTemplate getRandomRoomTemplete() {

        if (RoomLoader.totalWitght <= 0) {
            throw new Error("РС‚РѕРіРѕРІРѕРµ РІРµСЃРѕРІРѕРµ С‡РёСЃР»Рѕ РєРѕРјРЅР°С‚ СЂР°РІРЅРѕРµ 0! РСЃРїСЂР°РІСЊ :<");
        }

        int roll = random.nextInt(RoomLoader.totalWitght);
        int acc = 0;

        for (RoomTemplate roomTemplate : templatesById.values()) {
            if (roomTemplate.weight() <= 0) continue;
            acc += roomTemplate.weight();
            if (roll < acc) return roomTemplate;
        }

        throw new Error("Huh? СЌС‚Рѕ РїРѕСЃР»РµРґРЅСЏСЏ СЃС‚СЂРѕРєР° РІ RoomGenerator.java.. РїРѕРёРґРµРё РѕРЅР° РЅРµ РјРѕР¶РµС‚ Р±С‹С‚СЊ РІС‹Р·РІР°РЅР°");
    }

    private OpenSocket pollActiveSocket() {
        int checks = frontier.size();
        for (int i = 0; i < checks; i++) {
            OpenSocket socket = frontier.pollFirst();
            if (socket == null) {
                return null;
            }
            if (isSocketActive(socket)) {
                return socket;
            }
            frontier.addLast(socket);
        }
        return null;
    }

    private List<DoorSocket> getEntrances(RoomTemplate roomTemplate) {
        if (roomTemplate.doors() == null) {
            return List.of();
        }
        List<DoorSocket> result = new ArrayList<>();
        for (DoorSocket door : roomTemplate.doors()) {
            if (door.entrance()) {
                result.add(door);
            }
        }
        return result;
    }

    private void addNewSocketsFromPlacedRoom(
            RoomTemplate roomTemplate,
            DoorSocket usedEntry,
            BlockPos origin,
            BlockRotation rotation,
            int templateSizeX,
            int templateSizeZ
    ) {
        if (roomTemplate.doors() == null) {
            return;
        }

        for (DoorSocket door : roomTemplate.doors()) {
            if (!door.exit()) {
                continue;
            }
            if (door.id() == usedEntry.id()) {
                continue;
            }

            IVec3 rotatedDoorLocalPos = rotateLocalPos(
                    door.localPos(),
                    rotation
            );
            BlockPos doorWorldPos = origin.add(
                    rotatedDoorLocalPos.x(),
                    rotatedDoorLocalPos.y(),
                    rotatedDoorLocalPos.z()
            );
            Dir doorWorldFacing = rotateDir(door.facing(), rotation);

            frontier.addLast(new OpenSocket(
                    doorWorldPos,
                    doorWorldFacing,
                    door.continuationBoostByTags()
            ));
        }
    }

    private BlockRotation getRotationToConnect(Dir roomEntranceFacing, Dir openSocketFacing) {
        Dir desiredEntranceFacing = opposite(openSocketFacing);
        BlockRotation[] rotations = new BlockRotation[]{
                BlockRotation.NONE,
                BlockRotation.CLOCKWISE_90,
                BlockRotation.CLOCKWISE_180,
                BlockRotation.COUNTERCLOCKWISE_90
        };

        for (BlockRotation rotation : rotations) {
            if (rotateDir(roomEntranceFacing, rotation) == desiredEntranceFacing) {
                return rotation;
            }
        }
        return BlockRotation.NONE;
    }

    private IVec3 rotateLocalPos(IVec3 localPos, BlockRotation rotation) {
        int x = localPos.x();
        int y = localPos.y();
        int z = localPos.z();

        return switch (rotation) {
            case NONE -> new IVec3(x, y, z);
            case CLOCKWISE_90 -> new IVec3(-z, y, x);
            case CLOCKWISE_180 -> new IVec3(-x, y, -z);
            case COUNTERCLOCKWISE_90 -> new IVec3(z, y, -x);
        };
    }

    private Dir rotateDir(Dir dir, BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> dir;
            case CLOCKWISE_90 -> switch (dir) {
                case NORTH -> Dir.EAST;
                case EAST -> Dir.SOUTH;
                case SOUTH -> Dir.WEST;
                case WEST -> Dir.NORTH;
            };
            case CLOCKWISE_180 -> switch (dir) {
                case NORTH -> Dir.SOUTH;
                case EAST -> Dir.WEST;
                case SOUTH -> Dir.NORTH;
                case WEST -> Dir.EAST;
            };
            case COUNTERCLOCKWISE_90 -> switch (dir) {
                case NORTH -> Dir.WEST;
                case EAST -> Dir.NORTH;
                case SOUTH -> Dir.EAST;
                case WEST -> Dir.SOUTH;
            };
        };
    }

    private Dir opposite(Dir dir) {
        return switch (dir) {
            case NORTH -> Dir.SOUTH;
            case SOUTH -> Dir.NORTH;
            case EAST -> Dir.WEST;
            case WEST -> Dir.EAST;
        };
    }

    private Direction toDirection(Dir dir) {
        return switch (dir) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
        };
    }

    private int rotationToDegrees(BlockRotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
        };
    }

    private BlockRotation rotationFromDegrees(int degrees) {
        return switch (degrees) {
            case 90 -> BlockRotation.CLOCKWISE_90;
            case 180 -> BlockRotation.CLOCKWISE_180;
            case 270 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    public void tick() {
        tickCounter++;
        if (tickCounter < GENERATE_EVERY_TICKS) {
            return;
        }
        tickCounter = 0;

        int generated = 0;
        while (generated < MAX_ROOMS_PER_TRIGGER) {
            OpenSocket socket = pollActiveSocket();
            if (socket == null) {
                return;
            }

            if (tryGenerateAtSocket(socket)) {
                generated++;
            }
        }
    }

    private record Bounds3i(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}
    private record Placement(BlockPos origin, BlockRotation rotation) {}

    private Placement computePlacement(OpenSocket socket, DoorSocket entrance) {
        BlockRotation rotation = getRotationToConnect(entrance.facing(), socket.facing());
        IVec3 rotatedEntryLocalPos = rotateLocalPos(entrance.localPos(), rotation);
        BlockPos entryAnchor = socket.worldPos().offset(toDirection(socket.facing()));

        BlockPos origin = entryAnchor.add(
                -rotatedEntryLocalPos.x(),
                -rotatedEntryLocalPos.y(),
                -rotatedEntryLocalPos.z()
        );
        return new Placement(origin, rotation);
    }
}
