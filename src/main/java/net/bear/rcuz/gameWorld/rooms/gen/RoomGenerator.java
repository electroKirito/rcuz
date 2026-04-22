package net.bear.rcuz.gameWorld.rooms.gen;

import net.bear.rcuz.RCUZ;
import net.bear.rcuz.gameWorld.ConfigStructureLoader;
import net.bear.rcuz.gameWorld.rooms.model.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
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

        frontier.add(new OpenSocket(origin.add(0, 1, 15), Dir.WEST, true, new ArrayList<>()));
        frontier.add(new OpenSocket(origin.add(25, 1, 0), Dir.NORTH, true, new ArrayList<>()));
        frontier.add(new OpenSocket(origin.add(32, 8, 3), Dir.EAST, true, new ArrayList<>()));
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

        if (tryResolveAdjacentSocket(socket)) {
            return true;
        }

        if (Math.random() < 0.05)
            if (tryConnectToNearbySocket(socket)) {
                return true;
            }

        int roomTryes = 0;
        while (!candidates.isEmpty() && roomTryes < MAX_ROOMS_TO_TRY_PER_SOCKET) {
            roomTryes++;

            RoomTemplate room = pickRoomWeighted(candidates);
            if (room == null) break;

            boolean placed = tryPlaceRoomVariantes(room, socket);
            if (placed) return true;

            candidates.remove(room);
        }

        if (tryConnectToNearbySocket(socket)) {
            return true;
        }

        sealSocketWithBricks(socket);
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

        return false;
    }
    private boolean tryConnectToNearbySocket(OpenSocket source) {
        OpenSocket adjacent = findAdjacentOppositeSocket(source);
        if (adjacent != null) {
            openTunnelMouth(source);
            openTunnelMouth(adjacent);
            frontier.remove(adjacent);
            return true;
        }

        OpenSocket target = findNearestOppositeEntranceSocket(source);
        if (target == null) {
            return false;
        }

        BlockPos start = source.worldPos().offset(toDirection(source.facing()));
        BlockPos goal = target.worldPos().offset(toDirection(target.facing()));

        List<BlockPos> path = findPath(start, goal);
        if (path == null || path.isEmpty()) {
            return false;
        }

        carvePath(path);
        openTunnelMouth(source);
        openTunnelMouth(target);
        placeLaddersAlongPath(path, source.facing());

        frontier.remove(target);
        return true;
    }

    private boolean tryResolveAdjacentSocket(OpenSocket source) {
        OpenSocket adjacent = findAdjacentOppositeSocket(source);
        if (adjacent == null) {
            return false;
        }
        openTunnelMouth(source);
        openTunnelMouth(adjacent);
        frontier.remove(adjacent);
        return true;
    }

    private OpenSocket findAdjacentOppositeSocket(OpenSocket source) {
        BlockPos expected = source.worldPos().offset(toDirection(source.facing()));
        Dir requiredFacing = opposite(source.facing());

        for (OpenSocket candidate : frontier) {
            if (candidate.facing() != requiredFacing) {
                continue;
            }
            if (!candidate.worldPos().equals(expected)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private OpenSocket findNearestOppositeEntranceSocket(OpenSocket source) {
        Dir requiredFacing = opposite(source.facing());
        int maxSqDistance = CONNECT_MAX_FORWARD * CONNECT_MAX_FORWARD;

        OpenSocket best = null;
        int bestSqDist = Integer.MAX_VALUE;

        for (OpenSocket candidate : frontier) {
            if (!candidate.entrance()) {
                continue;
            }
            if (candidate.facing() != requiredFacing) {
                continue;
            }

            int dx = candidate.worldPos().getX() - source.worldPos().getX();
            int dy = candidate.worldPos().getY() - source.worldPos().getY();
            int dz = candidate.worldPos().getZ() - source.worldPos().getZ();
            int sqDist = dx * dx + dy * dy + dz * dz;

            if (sqDist > maxSqDistance) {
                continue;
            }
            if (sqDist < bestSqDist) {
                bestSqDist = sqDist;
                best = candidate;
            }
        }

        return best;
    }

    private List<BlockPos> findPath(BlockPos start, BlockPos goal) {
        int minX = Math.min(start.getX(), goal.getX()) - CONNECT_SEARCH_H;
        int maxX = Math.max(start.getX(), goal.getX()) + CONNECT_SEARCH_H;
        int minY = Math.min(start.getY(), goal.getY()) - CONNECT_SEARCH_V;
        int maxY = Math.max(start.getY(), goal.getY()) + CONNECT_SEARCH_V;
        int minZ = Math.min(start.getZ(), goal.getZ()) - CONNECT_SEARCH_H;
        int maxZ = Math.max(start.getZ(), goal.getZ()) + CONNECT_SEARCH_H;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        BlockPos startImm = start.toImmutable();
        BlockPos goalImm = goal.toImmutable();

        queue.add(startImm);
        visited.add(startImm);

        Direction[] dirs = new Direction[]{
                Direction.EAST,
                Direction.WEST,
                Direction.SOUTH,
                Direction.NORTH,
                Direction.UP,
                Direction.DOWN
        };

        while (!queue.isEmpty()) {
            BlockPos cur = queue.pollFirst();
            if (cur.equals(goalImm)) {
                return reconstructPath(parent, goalImm);
            }

            for (Direction dir : dirs) {
                BlockPos next = cur.offset(dir).toImmutable();
                if (visited.contains(next)) {
                    continue;
                }
                if (!canPathStep(next, startImm, goalImm, minX, maxX, minY, maxY, minZ, maxZ)) {
                    continue;
                }

                visited.add(next);
                parent.put(next, cur);
                queue.addLast(next);
            }
        }

        return null;
    }

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> parent, BlockPos goal) {
        LinkedList<BlockPos> path = new LinkedList<>();
        BlockPos cur = goal;
        path.addFirst(cur);
        while (parent.containsKey(cur)) {
            cur = parent.get(cur);
            path.addFirst(cur);
        }
        return path;
    }

    private boolean canPathStep(
            BlockPos center,
            BlockPos start,
            BlockPos goal,
            int minX, int maxX,
            int minY, int maxY,
            int minZ, int maxZ
    ) {
        if (center.getX() < minX || center.getX() > maxX) return false;
        if (center.getY() < minY || center.getY() > maxY) return false;
        if (center.getZ() < minZ || center.getZ() > maxZ) return false;

        if (center.equals(start) || center.equals(goal)) {
            return true;
        }

        Bounds3i clearance = corridorBounds(center);
        if (intersectsPlacedRooms(clearance)) {
            return false;
        }

        for (int x = clearance.minX; x <= clearance.maxX; x++) {
            for (int y = clearance.minY; y <= clearance.maxY; y++) {
                for (int z = clearance.minZ; z <= clearance.maxZ; z++) {
                    if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private Bounds3i corridorBounds(BlockPos center) {
        return new Bounds3i(
                center.getX() - 1,
                center.getY() - 1,
                center.getZ() - 1,
                center.getX() + 1,
                center.getY() + 2,
                center.getZ() + 1
        );
    }

    private void carvePath(List<BlockPos> path) {
        Set<BlockPos> core = new HashSet<>();
        Set<BlockPos> shell = new HashSet<>();

        for (BlockPos center : path) {
            collectCorridorSection(center, core, shell);
        }

        shell.removeAll(core);

        for (BlockPos p : shell) {
            if (world.getBlockState(p).isAir()) {
                world.setBlockState(p, Blocks.STONE.getDefaultState(), 2);
            }
        }

        for (BlockPos p : core) {
            world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
        }
    }

    private void collectCorridorSection(BlockPos center, Set<BlockPos> core, Set<BlockPos> shell) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 2; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    BlockPos p = center.add(ox, oy, oz).toImmutable();
                    boolean isCore = (ox == 0 && oz == 0 && oy >= 0 && oy <= 1);
                    if (isCore) core.add(p);
                    else shell.add(p);
                }
            }
        }
    }

    private void openTunnelMouth(OpenSocket socket) {
        Direction dir = toDirection(socket.facing());
        BlockPos p0 = socket.worldPos();
        BlockPos p1 = p0.up();
        BlockPos p2 = p0.offset(dir);
        BlockPos p3 = p2.up();

        world.setBlockState(p0, Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(p1, Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(p2, Blocks.AIR.getDefaultState(), 2);
        world.setBlockState(p3, Blocks.AIR.getDefaultState(), 2);
    }

    private void placeLaddersAlongPath(List<BlockPos> path, Dir fallbackFacing) {
        if (path == null || path.size() < 2) {
            return;
        }

        for (int i = 1; i < path.size(); i++) {
            BlockPos prev = path.get(i - 1);
            BlockPos cur = path.get(i);

            if (prev.getX() != cur.getX() || prev.getZ() != cur.getZ()) {
                continue;
            }
            if (prev.getY() == cur.getY()) {
                continue;
            }

            placeLadderColumn(path, i, fallbackFacing);
        }
    }

    private void placeLadderColumn(List<BlockPos> path, int verticalStepIndex, Dir fallbackFacing) {
        BlockPos a = path.get(verticalStepIndex - 1);
        BlockPos b = path.get(verticalStepIndex);

        int x = a.getX();
        int z = a.getZ();
        int minY = Math.min(a.getY(), b.getY());
        int maxY = Math.max(a.getY(), b.getY()) + 1;

        Direction lockedFacing = null;

        for (int y = minY; y <= maxY; y++) {
            BlockPos ladderPos = new BlockPos(x, y, z);
            if (lockedFacing != null) {
                placeSingleLadder(ladderPos, lockedFacing);
                continue;
            }

            Direction candidateFacing = chooseLadderFacing(path, verticalStepIndex, fallbackFacing, x, z, y);
            if (placeSingleLadder(ladderPos, candidateFacing)) {
                lockedFacing = candidateFacing;
            }
        }
    }

    private Direction chooseLadderFacing(
            List<BlockPos> path,
            int verticalStepIndex,
            Dir fallbackFacing,
            int x,
            int z,
            int yForCheck
    ) {
        LinkedHashSet<Direction> priorities = new LinkedHashSet<>();

        Direction aroundBefore = null;
        if (verticalStepIndex >= 2) {
            aroundBefore = horizontalDir(path.get(verticalStepIndex - 2), path.get(verticalStepIndex - 1));
        }
        Direction aroundAfter = null;
        if (verticalStepIndex + 1 < path.size()) {
            aroundAfter = horizontalDir(path.get(verticalStepIndex), path.get(verticalStepIndex + 1));
        }

        if (aroundBefore != null) priorities.add(aroundBefore);
        if (aroundAfter != null) priorities.add(aroundAfter);

        Direction fallback = toDirection(fallbackFacing);
        priorities.add(fallback);
        priorities.add(fallback.getOpposite());
        priorities.add(Direction.NORTH);
        priorities.add(Direction.SOUTH);
        priorities.add(Direction.WEST);
        priorities.add(Direction.EAST);

        BlockPos probe = new BlockPos(x, yForCheck, z);
        for (Direction dir : priorities) {
            if (!dir.getAxis().isHorizontal()) {
                continue;
            }
            if (hasLadderSupport(probe, dir)) {
                return dir;
            }
        }

        return fallback;
    }

    private boolean placeSingleLadder(BlockPos ladderPos, Direction facing) {
        BlockState current = world.getBlockState(ladderPos);
        if (!current.isAir()) {
            return false;
        }

        BlockState ladder = Blocks.LADDER.getDefaultState().with(net.minecraft.block.LadderBlock.FACING, facing);
        if (ladder.contains(Properties.WATERLOGGED)) {
            ladder = ladder.with(Properties.WATERLOGGED, false);
        }

        if (!hasLadderSupport(ladderPos, facing)) {
            return false;
        }

        world.setBlockState(ladderPos, ladder, 2);
        return true;
    }

    private boolean hasLadderSupport(BlockPos ladderPos, Direction facing) {
        BlockPos supportPos = ladderPos.offset(facing.getOpposite());
        BlockState supportState = world.getBlockState(supportPos);
        return supportState.isSideSolidFullSquare(world, supportPos, facing);
    }

    private Direction horizontalDir(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();

        if (Math.abs(dx) + Math.abs(dz) != 1) {
            return null;
        }
        if (dx == 1) return Direction.EAST;
        if (dx == -1) return Direction.WEST;
        if (dz == 1) return Direction.SOUTH;
        if (dz == -1) return Direction.NORTH;

        return null;
    }
    private void placeRoom(
            RoomTemplate roomTemplate,
            StructureTemplate template,
            DoorSocket selectedEntrance,
            BlockPos origin,
            BlockRotation rotation
    ) {
        Vec3i templateSize = template.getSize();
        Bounds3i bounds = computeBounds(origin, templateSize, rotation);

        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(rotation)
                .setIgnoreEntities(false);

        template.place(world, origin, origin, placementData, world.getRandom(), 2);
        applyTemplateReplacements(roomTemplate, bounds);

        PlacedRoom placed = new PlacedRoom(
                roomTemplate.id(),
                new IVec3(origin.getX(), origin.getY(), origin.getZ()),
                rotationToDegrees(rotation),
                templateSize,
                selectedEntrance
        );

        placedRooms.add(placed);
        indexPlacedRoomBounds(bounds);

        addNewSocketsFromPlacedRoom(
                roomTemplate,
                selectedEntrance,
                origin,
                rotation,
                templateSize.getX(),
                templateSize.getZ()
        );
    }

    private void applyTemplateReplacements(RoomTemplate roomTemplate, Bounds3i bounds) {
        List<ReplaceRule> rules = roomTemplate.replacements();
        if (rules == null || rules.isEmpty()) {
            return;
        }

        List<ResolvedReplaceRule> resolvedRules = new ArrayList<>();
        for (ReplaceRule rule : rules) {
            if (rule == null) {
                continue;
            }

            Identifier fromId = rule.from();
            Identifier toId = rule.to();
            if (fromId == null || toId == null) {
                continue;
            }

            if (!Registries.BLOCK.containsId(fromId) || !Registries.BLOCK.containsId(toId)) {
                RCUZ.LOGGER.warn("Skip replacement rule in room '{}': unknown block id from='{}' to='{}'",
                        roomTemplate.id(), fromId, toId);
                continue;
            }

            float chance = MathHelper.clamp(rule.chance(), 0.0F, 1.0F);
            if (chance <= 0.0F) {
                continue;
            }

            Block fromBlock = Registries.BLOCK.get(fromId);
            Block toBlock = Registries.BLOCK.get(toId);
            resolvedRules.add(new ResolvedReplaceRule(fromBlock, toBlock, chance));
        }

        if (resolvedRules.isEmpty()) {
            return;
        }

        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState currentState = world.getBlockState(pos);
                    Block currentBlock = currentState.getBlock();

                    for (ResolvedReplaceRule rule : resolvedRules) {
                        if (currentBlock != rule.fromBlock()) {
                            continue;
                        }

                        if (rule.chance() < 1.0F && random.nextFloat() >= rule.chance()) {
                            continue;
                        }

                        world.setBlockState(pos, rule.toBlock().getDefaultState(), 2);
                        break;
                    }
                }
            }
        }
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
                    door.entrance(),
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
    private record ResolvedReplaceRule(Block fromBlock, Block toBlock, float chance) {}
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
