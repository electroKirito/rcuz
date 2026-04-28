package net.bear.rcuz.gameWorld.rooms.gen;

import net.bear.rcuz.RCUZ;
import net.bear.rcuz.RCUZBlocks;
import net.bear.rcuz.gameWorld.ConfigStructureLoader;
import net.bear.rcuz.gameWorld.rooms.model.*;
import net.bear.rcuz.gameWorld.util.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
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


    /// РўРЈРќР•Р›Р¬Р§РРљ
    private static final int CONNECT_MAX_FORWARD = 20;
    private static final int CONNECT_SEARCH_H = 10;
    private static final int CONNECT_SEARCH_V = 10;
    private static final int TUNNEL_RX = 4;
    private static final int TUNNEL_RY = 4;
    private static final int TUNNEL_RZ = 4;
    /// РљРћРќР•Р¦ РўРЈРќР•Р›Р¬Р§РРљРђ :(


    private final Map<String, RoomTemplate> templatesById;
    private final Map<String, List<MobSpawnEntry>> mobTagPresets;
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
    private final Set<BlockPos> archivedBPitMarkers = new HashSet<>();


    private final ServerWorld world;

    private static final int ACTIVATE_RADIUS = 100;
    private static final int ACTIVATE_RADIUS_SQ = ACTIVATE_RADIUS * ACTIVATE_RADIUS;
    private static final boolean ENABLE_PITS = true;
    private static final int PIT_MAX_DROP = 24;
    private static final int PIT_MIN_RADIUS = 2;
    private static final int PIT_MAX_RADIUS = 6;

    public RoomGenerator(List<RoomTemplate> templates, Random random, ServerWorld world, ConfigStructureLoader configStructureLoader, Map<String, List<MobSpawnEntry>> mobTagPresets) {
        this.configStructureLoader = configStructureLoader;
        this.world = world;
        this.templatesById = new HashMap<>();
        this.random = random;
        this.mobTagPresets = new HashMap<>();
        this.mobTagPresets.putAll(mobTagPresets);

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

        frontier.add(new OpenSocket(origin.add(0, 1, 15), Dir.WEST, true, DoorType.DEFAULT, false, new ArrayList<>(), new ArrayList<>()));
        frontier.add(new OpenSocket(origin.add(25, 1, 0), Dir.NORTH, true, DoorType.DEFAULT, false, new ArrayList<>(), new ArrayList<>()));
        frontier.add(new OpenSocket(origin.add(32, 8, 3), Dir.EAST, true, DoorType.DEFAULT, false, new ArrayList<>(), new ArrayList<>()));
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
        Map<RoomTemplate, Integer> effectiveWeights = new HashMap<>();

        /// Filtering candidates
        candidates.removeIf(roomTemplate -> !candidateFiltering(roomTemplate, socket));

        candidates.removeIf(roomTemplate -> {
            int boostedWeight = computeEffectiveWeight(roomTemplate, socket);
            if (boostedWeight <= 0) {
                return true;
            }
            effectiveWeights.put(roomTemplate, boostedWeight);
            return false;
        });

        if (tryResolveAdjacentSocket(socket)) {
            return true;
        }

        int roomTryes = 0;
        while (!candidates.isEmpty() && roomTryes < MAX_ROOMS_TO_TRY_PER_SOCKET) {
            roomTryes++;

            RoomTemplate room = pickRoomWeighted(candidates, effectiveWeights);
            if (room == null) break;

            PlacedRoomLittle placed = tryPlaceRoomVariantes(room, socket);
            if (placed.placed()) {
                openSocketSeal(socket);
                usedCount.merge(room.id(), 1, Integer::sum);



                return true;
            }

            candidates.remove(room);
            effectiveWeights.remove(room);
        }

        if (tryConnectToNearbySocket(socket)) {
            return true;
        }

        sealSocketWithBricks(socket);
        return false;
    }

    private void generateMobs(RoomTemplate room, BlockPos roomOrigin, Vec3i roomSize, BlockRotation roomRotation) {
        if (room.mobSpawners() == null || room.mobSpawners().isEmpty()) {
            return;
        }

        Bounds3i roomBounds = computeBounds(roomOrigin, roomSize, roomRotation);
        List<Vec3d> roomSpawnPoints = collectRoomSpawnPoints(roomBounds);

        for (RoomMobSpawner spawner : room.mobSpawners()) {
            if (spawner == null || spawner.pool() == null || spawner.pool().isEmpty()) {
                continue;
            }

            int rolls = Math.max(spawner.rolls(), 0);
            for (int i = 0; i < rolls; i++) {
                MobSpawnPoolEntry currentPull = pickMobSpawnPoolEntryWeighted(spawner.pool());
                if (currentPull == null || currentPull.empty()) {
                    continue;
                }

                List<MobSpawnEntry> spawnEntries = resolveSpawnEntries(currentPull, room);
                if (spawnEntries.isEmpty()) {
                    continue;
                }

                for (MobSpawnEntry entry : spawnEntries) {
                    if (entry == null) {
                        continue;
                    }

                    Vec3d pos = resolveSpawnPosition(spawner, roomOrigin, roomRotation, roomSpawnPoints);
                    if (pos == null) {
                        continue;
                    }

                    if (entry.id() == null) {
                        continue;
                    }

                    int count = 1;
                    if (entry.count() != null) {
                        count = Math.max(entry.count().getRandCount(this.random), 0);
                    }
                    if (count <= 0) {
                        continue;
                    }

                    spawnMobInRoom(entry, pos, count);
                }
            }
        }
    }

    private List<MobSpawnEntry> resolveSpawnEntries(MobSpawnPoolEntry poolEntry, RoomTemplate room) {
        List<MobSpawnEntry> result = new ArrayList<>();

        String presetId = poolEntry.tagPreset();
        if (presetId != null && !presetId.isBlank()) {
            result.addAll(resolvePresetEntries(presetId, room));
        }

        if (poolEntry.spawn() != null) {
            for (MobSpawnEntry entry : poolEntry.spawn()) {
                if (entry != null) result.add(entry);
            }
        }

        return result;
    }

    private List<MobSpawnEntry> resolvePresetEntries(String presetId, RoomTemplate room) {
        if ("*".equals(presetId)) {
            if (room.tags() == null || room.tags().isEmpty()) {
                return List.of();
            }

            List<String> candidates = new ArrayList<>();
            for (String tag : room.tags()) {
                if (mobTagPresets.containsKey(tag)) {
                    candidates.add(tag);
                }
            }
            if (candidates.isEmpty()) {
                return List.of();
            }

            String selectedTag = candidates.get(random.nextInt(candidates.size()));
            List<MobSpawnEntry> preset = mobTagPresets.get(selectedTag);
            return preset == null ? List.of() : preset;
        }

        List<MobSpawnEntry> preset = mobTagPresets.get(presetId);
        return preset == null ? List.of() : preset;
    }

    private Vec3d resolveSpawnPosition(
            RoomMobSpawner spawner,
            BlockPos roomOrigin,
            BlockRotation roomRotation,
            List<Vec3d> roomSpawnPoints
    ) {
        if (spawner.mode() == MobSpawnMode.POSITION) {
            if (spawner.pos() == null) {
                return null;
            }

            IVec3 rotatedLocal = rotateLocalPos(spawner.pos(), roomRotation);
            BlockPos base = roomOrigin.add(rotatedLocal.x(), rotatedLocal.y(), rotatedLocal.z());
            if (!isValidSpawnBlock(base)) {
                return null;
            }
            return Vec3d.ofBottomCenter(base);
        }

        if (roomSpawnPoints.isEmpty()) {
            return null;
        }
        return roomSpawnPoints.get(random.nextInt(roomSpawnPoints.size()));
    }

    private List<Vec3d> collectRoomSpawnPoints(Bounds3i bounds) {
        List<Vec3d> points = new ArrayList<>();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!isValidSpawnBlock(pos)) {
                        continue;
                    }
                    points.add(Vec3d.ofBottomCenter(pos));
                }
            }
        }
        return points;
    }

    private boolean isValidSpawnBlock(BlockPos pos) {
        BlockState feet = world.getBlockState(pos);
        BlockState head = world.getBlockState(pos.up());
        BlockState floor = world.getBlockState(pos.down());

        if (!feet.isAir() || !head.isAir()) {
            return false;
        }
        if (floor.isAir() || floor.getFluidState().isEmpty() == false) {
            return false;
        }
        return floor.isSideSolidFullSquare(world, pos.down(), Direction.UP);
    }

    private void spawnMobInRoom(MobSpawnEntry entry, Vec3d position, int count) {
        for (int m = 0; m < count; m++) {
            Entity entity = Registries.ENTITY_TYPE.get(entry.id()).create(world);
            if (entity == null) {
                continue;
            }
            if (entry.tag() != null) {
                entity.readNbt(entry.tag());
            }
            entity.setPosition(position);
            world.spawnEntity(entity);
        }
    }

    private MobSpawnPoolEntry pickMobSpawnPoolEntryWeighted(List<MobSpawnPoolEntry> poolEntries) {
        int total = 0;
        for (MobSpawnPoolEntry poolEntry : poolEntries) {
            int weight = poolEntry.weight();
            if (weight > 0) total += weight;
        }
        if (total <= 0) return null;

        int roll = random.nextInt(total);
        int acc = 0;
        for (MobSpawnPoolEntry poolEntry : poolEntries) {
            int weight = poolEntry.weight();
            if (weight <= 0) continue;
            acc += weight;
            if (roll < acc) return poolEntry;
        }
        return null;
    }

    private int computeEffectiveWeight(RoomTemplate roomTemplate, OpenSocket socket) {
        Set<String> roomTags = roomTemplate.tags() == null ? Set.of() : roomTemplate.tags();
        List<ContinuationBoostByTag> boosts = socket.continuationBoostByTags();

        int effectiveWeight = roomTemplate.weight();
        boolean hasMatchingBoost = false;

        if (boosts != null) {
            for (ContinuationBoostByTag boost : boosts) {
                if (boost == null || boost.tags() == null) {
                    continue;
                }
                if (!roomTags.containsAll(boost.tags())) {
                    continue;
                }

                hasMatchingBoost = true;
                effectiveWeight = switch (boost.boostType()) {
                    case ADD -> effectiveWeight + Math.round(boost.value());
                    case SET -> Math.round(boost.value());
                    case MULT -> Math.round(effectiveWeight * boost.value());
                };
            }
        }

        if (socket.onlyBoostedTags() && !hasMatchingBoost) {
            return 0;
        }

        return Math.max(effectiveWeight, 0);
    }

    private boolean candidateFiltering(RoomTemplate roomTemplate, OpenSocket socket) {
        boolean hasMatchingDoorType = false;
        for (DoorSocket door : roomTemplate.doors()) {
            if (!door.entrance()) continue;
            if (door.doorType() == socket.doorType()) {
                hasMatchingDoorType = true;
                break;
            }
        }
        if (!hasMatchingDoorType) return false;

        int mxd = roomTemplate.maxDistance();
        int mnd = roomTemplate.minDistance();

        int mxy = roomTemplate.yMax();
        int mny = roomTemplate.yMin();



        int maxPerDim = roomTemplate.maxPerDimension();
        Integer curPerDim = usedCount.get(roomTemplate.id());
        if (curPerDim == null) curPerDim = 0;
        boolean isFree = (maxPerDim < 0) || (curPerDim < maxPerDim);
        Vec3d curPos = socket.worldPos().toCenterPos();
        int curPosY = (int) curPos.getY();

        int distSq = WorldHelper.getDificultDistanceSquared(curPos);

        boolean meetsMin = (mnd <= 0) || (distSq >= mnd * mnd);
        boolean meetsMax = (mxd == -1) || (distSq < mxd * mxd);
        boolean yMax = (curPosY < mxy) || (mxy < -9999);
        boolean yMin = (curPosY >= mny) || (mny < -9999);

        return meetsMin && meetsMax && isFree && yMin && yMax;
    }


    private PlacedRoomLittle tryPlaceRoomVariantes(RoomTemplate room, OpenSocket socket) {
        StructureTemplate template = configStructureLoader.get(room.id());
        if (template == null) {
            return new PlacedRoomLittle(null, null, false);
        }

        List<DoorSocket> entrances = getEntrances(room).stream()
                .filter(door -> door.doorType() == socket.doorType())
                .toList(); /// Entrances with matching door type
        if (entrances.isEmpty()) {
            return new PlacedRoomLittle(null, null, false);
        }

        List<DoorSocket> shuffledEntrances = new ArrayList<>(entrances);
        Collections.shuffle(shuffledEntrances, new java.util.Random(random.nextLong()));

        int entrancesTryCount = 0;
        for (DoorSocket doorSocket : shuffledEntrances) {
            if (entrancesTryCount++ >= MAX_ENTRANCES_TO_TRY_PER_ROOM) break;

            Placement placement = computePlacement(socket, doorSocket);
            if (!canPlaceRoomAt(template, placement.origin, placement.rotation)) continue;

            placeRoom(room, template, doorSocket, placement.origin, placement.rotation);
            generateMobs(room, placement.origin, template.getSize(), placement.rotation);
            return new PlacedRoomLittle(template.getSize(), placement.origin, true);
        }

        return new PlacedRoomLittle(null, null, false);
    }

    private boolean tryConnectToNearbySocket(OpenSocket source) {
        if (source.doorType() != DoorType.DEFAULT) {
            return false;
        }

        OpenSocket adjacent = findAdjacentOppositeSocket(source);
        if (adjacent != null && adjacent.doorType() == DoorType.DEFAULT) {
            openSocketSeal(source);
            openSocketSeal(adjacent);
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
        openSocketSeal(source);
        openSocketSeal(target);
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
        openSocketSeal(source);
        openSocketSeal(adjacent);
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
            if (candidate.doorType() != DoorType.DEFAULT) {
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
        clearEntranceDoorSeal(origin, selectedEntrance, rotation);
        applyTemplateReplacements(roomTemplate, bounds);

        Set<BlockPos> extraMarkersToClean = Collections.emptySet();
        if (ENABLE_PITS) {
            extraMarkersToClean = tryCreatePit(bounds);
        }
        cleanupPitMarkers(bounds, extraMarkersToClean);

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

    private Set<BlockPos> tryCreatePit(Bounds3i roomBounds) {
        List<BlockPos> aMarkers = collectMarkersInBounds(roomBounds, RCUZBlocks.A_PIT);
        if (aMarkers.isEmpty()) {
            return Collections.emptySet();
        }

        BlockPos center = aMarkers.get(random.nextInt(aMarkers.size()));
        Set<BlockPos> aBlob = floodMarkerBlob(center, roomBounds, RCUZBlocks.A_PIT);
        if (aBlob.isEmpty()) {
            return Collections.emptySet();
        }

        Integer targetY = findNearestBLayerY(center, roomBounds);
        if (targetY == null) {
            return Collections.emptySet();
        }

        Set<BlockPos> bLayer = collectMarkersAtY(roomBounds, targetY, RCUZBlocks.B_PIT);
        if (bLayer.isEmpty()) {
            return Collections.emptySet();
        }

        int maxRadius = Math.min(PIT_MAX_RADIUS,
                Math.min(computeMaxSupportedRadius(center, aBlob, center.getY()), computeMaxSupportedRadius(center, bLayer, targetY)));
        if (maxRadius < PIT_MIN_RADIUS) {
            return Collections.emptySet();
        }

        int radius = random.nextBetween(PIT_MIN_RADIUS, maxRadius);
        Block wallBlock = pickPitWallBlock(center, roomBounds);
        Set<BlockPos> usedBMarkers = new HashSet<>();

        while (radius >= PIT_MIN_RADIUS) {
            if (!maskFits(center, aBlob, center.getY(), radius)) {
                radius--;
                continue;
            }
            if (!maskFits(center, bLayer, targetY, radius)) {
                radius--;
                continue;
            }

            carvePitColumn(center, targetY, radius, wallBlock);
            usedBMarkers.addAll(maskPoints(center, targetY, radius));
            break;
        }

        return usedBMarkers;
    }

    private List<BlockPos> collectMarkersInBounds(Bounds3i bounds, Block marker) {
        List<BlockPos> result = new ArrayList<>();
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (world.getBlockState(p).getBlock() == marker) {
                        result.add(p);
                    }
                }
            }
        }
        return result;
    }

    private Set<BlockPos> collectMarkersAtY(Bounds3i bounds, int y, Block marker) {
        Set<BlockPos> result = new HashSet<>();
        if (marker == RCUZBlocks.B_PIT) {
            for (BlockPos p : archivedBPitMarkers) {
                if (p.getY() != y) continue;
                if (p.getX() < bounds.minX || p.getX() > bounds.maxX) continue;
                if (p.getZ() < bounds.minZ || p.getZ() > bounds.maxZ) continue;
                result.add(p.toImmutable());
            }
        }
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                BlockPos p = new BlockPos(x, y, z);
                if (world.getBlockState(p).getBlock() == marker) {
                    result.add(p.toImmutable());
                }
            }
        }
        return result;
    }

    private Set<BlockPos> floodMarkerBlob(BlockPos start, Bounds3i bounds, Block marker) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        BlockPos startImm = start.toImmutable();
        if (world.getBlockState(startImm).getBlock() != marker) {
            return visited;
        }

        queue.add(startImm);
        visited.add(startImm);

        Direction[] dirs = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        while (!queue.isEmpty()) {
            BlockPos cur = queue.pollFirst();
            for (Direction dir : dirs) {
                BlockPos next = cur.offset(dir).toImmutable();
                if (next.getX() < bounds.minX || next.getX() > bounds.maxX) continue;
                if (next.getY() < bounds.minY || next.getY() > bounds.maxY) continue;
                if (next.getZ() < bounds.minZ || next.getZ() > bounds.maxZ) continue;
                if (visited.contains(next)) continue;
                if (world.getBlockState(next).getBlock() != marker) continue;

                visited.add(next);
                queue.addLast(next);
            }
        }

        return visited;
    }

    private Integer findNearestBLayerY(BlockPos center, Bounds3i bounds) {
        int minY = Math.max(bounds.minY - PIT_MAX_DROP, world.getBottomY());
        for (int y = center.getY() - 1; y >= minY; y--) {
            if (hasArchivedBMarkerAtY(bounds, y)) {
                return y;
            }
        }
        return null;
    }

    private boolean hasArchivedBMarkerAtY(Bounds3i bounds, int y) {
        for (BlockPos p : archivedBPitMarkers) {
            if (p.getY() != y) continue;
            if (p.getX() < bounds.minX || p.getX() > bounds.maxX) continue;
            if (p.getZ() < bounds.minZ || p.getZ() > bounds.maxZ) continue;
            return true;
        }
        return false;
    }

    private int computeMaxSupportedRadius(BlockPos center, Set<BlockPos> markerSet, int y) {
        int max = 0;
        for (int r = PIT_MIN_RADIUS; r <= PIT_MAX_RADIUS; r++) {
            if (!maskFits(center, markerSet, y, r)) {
                break;
            }
            max = r;
        }
        return max;
    }

    private boolean maskFits(BlockPos center, Set<BlockPos> markerSet, int y, int radius) {
        for (BlockPos p : maskPoints(center, y, radius)) {
            if (!markerSet.contains(p)) {
                return false;
            }
        }
        return true;
    }

    private Set<BlockPos> maskPoints(BlockPos center, int y, int radius) {
        Set<BlockPos> points = new HashSet<>();
        for (int ox = -radius; ox <= radius; ox++) {
            for (int oz = -radius; oz <= radius; oz++) {
                if (isInsidePitFitMask(ox, oz, radius)) {
                    points.add(new BlockPos(center.getX() + ox, y, center.getZ() + oz));
                }
            }
        }
        return points;
    }

    private boolean isInsidePitFitMask(int ox, int oz, int radius) {
        double nx = ox / (double) radius;
        double nz = oz / (double) radius;
        return (nx * nx + nz * nz) <= 1.0D;
    }

    private boolean isInsidePitMask(int ox, int oz, int radius) {
        double dx = ox + 0.5;
        double dz = oz + 0.5;
        double distSq = dx * dx + dz * dz;
        double r = radius + 0.5;
        return distSq <= r * r;
    }

    private void carvePitColumn(BlockPos center, int targetY, int radius, Block wallBlock) {
        int fromY = Math.max(targetY + 1, world.getBottomY());
        int toY = center.getY() - 1;
        if (fromY > toY) return;

        for (int y = toY; y >= fromY; y--) {
            for (int ox = -(radius + 1); ox <= radius + 1; ox++) {
                for (int oz = -(radius + 1); oz <= radius + 1; oz++) {
                    BlockPos p = new BlockPos(center.getX() + ox, y, center.getZ() + oz);
                    boolean inHole = isInsidePitMask(ox, oz, radius);
                    boolean inShell = isInsidePitMask(ox, oz, radius + 1) && !inHole;

                    if (inHole) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                    } else if (inShell) {
                        world.setBlockState(p, wallBlock.getDefaultState(), 2);
                    }
                }
            }
        }
    }

    private Block pickPitWallBlock(BlockPos around, Bounds3i bounds) {
        Map<Block, Integer> freq = new HashMap<>();
        int sampleRadius = 4;

        for (int x = Math.max(bounds.minX, around.getX() - sampleRadius); x <= Math.min(bounds.maxX, around.getX() + sampleRadius); x++) {
            for (int y = Math.max(bounds.minY, around.getY() - sampleRadius); y <= Math.min(bounds.maxY, around.getY() + sampleRadius); y++) {
                for (int z = Math.max(bounds.minZ, around.getZ() - sampleRadius); z <= Math.min(bounds.maxZ, around.getZ() + sampleRadius); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState st = world.getBlockState(p);
                    Block b = st.getBlock();
                    if (b == Blocks.AIR || b == RCUZBlocks.A_PIT || b == RCUZBlocks.B_PIT) {
                        continue;
                    }
                    if (!st.isSolidBlock(world, p)) {
                        continue;
                    }
                    freq.merge(b, 1, Integer::sum);
                }
            }
        }

        return freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Blocks.STONE);
    }

    private void cleanupPitMarkers(Bounds3i bounds, Set<BlockPos> extraMarkers) {
        for (int x = bounds.minX; x <= bounds.maxX; x++) {
            for (int y = bounds.minY; y <= bounds.maxY; y++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    Block b = world.getBlockState(p).getBlock();
                    if (b == RCUZBlocks.A_PIT || b == RCUZBlocks.B_PIT) {
                        if (b == RCUZBlocks.B_PIT) {
                            archivedBPitMarkers.add(p.toImmutable());
                        }
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
        }

        for (BlockPos p : extraMarkers) {
            archivedBPitMarkers.remove(p);
            Block b = world.getBlockState(p).getBlock();
            if (b == RCUZBlocks.A_PIT || b == RCUZBlocks.B_PIT) {
                world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
            }
        }
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

    private void openSocketSeal(OpenSocket socket) {
        List<BlockPos> sealed = socket.sealedBlocks();
        if (sealed == null || sealed.isEmpty()) {
            return;
        }
        for (BlockPos pos : sealed) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        }
    }

    private List<BlockPos> sealSocketWithBricks(OpenSocket socket) {
        BlockPos start = socket.worldPos();
        if (!isDoorSealBlock(world.getBlockState(start).getBlock())) {
            return List.of();
        }

        Set<BlockPos> cluster = collectDoorSealCluster(start);
        if (cluster.isEmpty()) {
            return List.of();
        }

        Map<Block, Integer> nearBlocks = new HashMap<>();
        for (BlockPos sealPos : cluster) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        BlockPos neighbor = sealPos.add(dx, dy, dz);
                        if (cluster.contains(neighbor)) continue;

                        BlockState state = world.getBlockState(neighbor);
                        Block block = state.getBlock();
                        if (block == Blocks.AIR || block == RCUZBlocks.A_PIT || block == RCUZBlocks.B_PIT || block == RCUZBlocks.DOOR_SEAL || isDoorSealBlock(block)) {
                            continue;
                        }
                        if (!state.isOpaqueFullCube(world, neighbor)) {
                            continue;
                        }

                        nearBlocks.merge(block, 1, Integer::sum);
                    }
                }
            }
        }

        int maxCount = nearBlocks.values().stream().mapToInt(v -> v).max().orElse(-1);
        List<Block> bestCandidates = nearBlocks.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .toList();

        Block fill = bestCandidates.isEmpty()
                ? Blocks.STONE
                : bestCandidates.get(random.nextInt(bestCandidates.size()));

        for (BlockPos sealPos : cluster) {
            world.setBlockState(sealPos, fill.getDefaultState(), 2);
        }
        return new ArrayList<>(cluster);
    }

    private Set<BlockPos> collectDoorSealCluster(BlockPos start) {
        Set<BlockPos> cluster = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos root = start.toImmutable();

        cluster.add(root);
        queue.add(root);

        while (!queue.isEmpty()) {
            BlockPos cur = queue.pollFirst();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        BlockPos next = cur.add(dx, dy, dz).toImmutable();
                        if (cluster.contains(next)) continue;

                        if (!isDoorSealBlock(world.getBlockState(next).getBlock())) continue;
                        cluster.add(next);
                        queue.add(next);
                    }
                }
            }
        }

        return cluster;
    }

    private void clearEntranceDoorSeal(BlockPos roomOrigin, DoorSocket selectedEntrance, BlockRotation rotation) {
        IVec3 rotatedEntrance = rotateLocalPos(selectedEntrance.localPos(), rotation);
        BlockPos entranceWorldPos = roomOrigin.add(rotatedEntrance.x(), rotatedEntrance.y(), rotatedEntrance.z());
        if (!isDoorSealBlock(world.getBlockState(entranceWorldPos).getBlock())) {
            return;
        }

        Set<BlockPos> cluster = collectDoorSealCluster(entranceWorldPos);
        for (BlockPos pos : cluster) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        }
    }

    private boolean isDoorSealBlock(Block block) {
        return Registries.BLOCK.getId(block).equals(new Identifier(RCUZ.MOD_ID, "door_seal"));
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

    private RoomTemplate pickRoomWeighted(List<RoomTemplate> rooms, Map<RoomTemplate, Integer> effectiveWeights) {
        int total = 0;
        for (RoomTemplate r : rooms) {
            int weight = effectiveWeights.getOrDefault(r, r.weight());
            if (weight > 0) total += weight;
        }
        if (total <= 0) return null;

        int roll = random.nextInt(total);
        int acc = 0;
        for (RoomTemplate r : rooms) {
            int weight = effectiveWeights.getOrDefault(r, r.weight());
            if (weight <= 0) continue;
            acc += weight;
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

        DoorSocket selectedEntrance = entrances.get(random.nextInt((int) entrances.stream().filter(DoorSocket::entrance).count())); ///РЇ СЃРґРµР»Р°Р» С„РёР»СЊС‚СЂ С‡С‚РѕР± РЅРµ РІС‹Р±РёСЂР°Р»Р°СЃСЊ РґРІРµСЂСЊ, РєРѕС‚РѕСЂР°СЏ РЅРµ РјРѕР¶РµС‚ Р±С‹С‚СЊ РІС…РѕРґРѕРј
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


        ///  Р‘РѕР»СЊС€РѕР№ Р°Рґ СЃ СЂРѕС‚Р°С†РёРµР№ Р±Р»РѕРєРѕРІ
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
            throw new Error("Р ВРЎвЂљР С•Р С–Р С•Р Р†Р С•Р Вµ Р Р†Р ВµРЎРѓР С•Р Р†Р С•Р Вµ РЎвЂЎР С‘РЎРѓР В»Р С• Р С”Р С•Р СР Р…Р В°РЎвЂљ РЎР‚Р В°Р Р†Р Р…Р С•Р Вµ 0! Р ВРЎРѓР С—РЎР‚Р В°Р Р†РЎРЉ :<");
        }

        int roll = random.nextInt(RoomLoader.totalWitght);
        int acc = 0;

        for (RoomTemplate roomTemplate : templatesById.values()) {
            if (roomTemplate.weight() <= 0) continue;
            acc += roomTemplate.weight();
            if (roll < acc) return roomTemplate;
        }

        throw new Error("Huh? РЎРЊРЎвЂљР С• Р С—Р С•РЎРѓР В»Р ВµР Т‘Р Р…РЎРЏРЎРЏ РЎРѓРЎвЂљРЎР‚Р С•Р С”Р В° Р Р† RoomGenerator.java.. Р С—Р С•Р С‘Р Т‘Р ВµР С‘ Р С•Р Р…Р В° Р Р…Р Вµ Р СР С•Р В¶Р ВµРЎвЂљ Р В±РЎвЂ№РЎвЂљРЎРЉ Р Р†РЎвЂ№Р В·Р Р†Р В°Р Р…Р В°");
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

            OpenSocket newSocket = new OpenSocket(
                    doorWorldPos,
                    doorWorldFacing,
                    door.entrance(),
                    door.doorType(),
                    door.onlyBoostedTags(),
                    door.continuationBoostByTags(),
                    new ArrayList<>()
            );
            List<BlockPos> sealedBlocks = sealSocketWithBricks(newSocket);
            frontier.addLast(new OpenSocket(
                    newSocket.worldPos(),
                    newSocket.facing(),
                    newSocket.entrance(),
                    newSocket.doorType(),
                    newSocket.onlyBoostedTags(),
                    newSocket.continuationBoostByTags(),
                    sealedBlocks
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


