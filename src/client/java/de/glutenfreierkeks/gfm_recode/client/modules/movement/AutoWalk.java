package de.glutenfreierkeks.gfm_recode.client.modules.movement;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.*;

public class AutoWalk extends Module {

    public final EnumSetting<WalkMode> mode = register(
            new EnumSetting<>("Mode", "Walking mode", WalkMode.FORWARD)
    );

    public final EnumSetting<WalkType> walkType = register(
            new EnumSetting<>("WalkType", "Smart find target mode", WalkType.RANDOM)
    );

    public final de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting targetX = register(
            new de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting("Target X", "Coordinate X to pathfind to", 0, -10000, 10000)
    );

    public final de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting targetZ = register(
            new de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting("Target Z", "Coordinate Z to pathfind to", 0, -10000, 10000)
    );

    public enum WalkMode {
        FORWARD,
        SMART
    }

    public enum WalkType {
        RANDOM,
        COORDINATE
    }

    // ── Internal mode mirror ──────────────────────────────────────────────────
    private WalkMode activeMode = WalkMode.FORWARD;

    // ── Direction ─────────────────────────────────────────────────────────────
    private int chosenDirection = -1;
    private static final String[] DIR_NAMES = { "North", "East", "South", "West" };
    private static final int[]    DIR_DX    = {  0,  1,  0, -1 };
    private static final int[]    DIR_DZ    = { -1,  0,  1,  0 };

    // ── A* config ─────────────────────────────────────────────────────────────
    private static final int TARGET_DISTANCE = 128;
    private static final int MAX_ITERATIONS  = 8192;

    // ── Path state ────────────────────────────────────────────────────────────
    private final List<BlockPos> currentPath = new ArrayList<>();
    private int pathIndex = 0;

    private static final double OFF_ROUTE_DISTANCE = 3.5;
    private static final int    REPLAN_COOLDOWN    = 40;
    private int replanCooldown = 0;

    // ── Continuous lookahead replanning (every 10 s = 200 ticks) ─────────────
    /** Pending path that was computed in the background; swapped in smoothly. */
    private final List<BlockPos> pendingPath = new ArrayList<>();
    private static final int LOOKAHEAD_INTERVAL = 200; // ticks (10 s)
    private int lookaheadTimer = 0;

    // ── Stuck detection (3 s = 60 ticks) ─────────────────────────────────────
    private static final int    STUCK_TICKS    = 60;
    private static final double STUCK_MIN_DIST = 1.0; // blocks moved in window
    private int     stuckTimer       = 0;
    private Vec3d   stuckRefPos      = null;
    private boolean stuckJustHandled = false; // avoid re-triggering every tick

    // ── Exploration-path render data (set by A* on every planning call) ───────
    /**
     * Explored nodes captured during the last A* run, used for the orange
     * "thinking" overlay.  Only a sample (up to EXPLORE_RENDER_CAP) is kept
     * to avoid frame-rate impact.
     */
    private final List<BlockPos> exploredNodes = new ArrayList<>();
    private static final int EXPLORE_RENDER_CAP = 512;

    // ── Movement helpers ──────────────────────────────────────────────────────
    private int jumpCooldown       = 0;
    private int sprintJumpCooldown = 0;

    // ── Render colours ────────────────────────────────────────────────────────
    // Exploration / "thinking" paths
    private static final float[] COL_EXPLORE     = { 1.0f, 0.55f, 0.0f, 0.18f }; // dim orange
    private static final float[] COL_EXPLORE_SEG = { 1.0f, 0.65f, 0.0f, 0.12f };

    // Final path – done / next / ahead
    private static final float[] COL_DONE  = { 0.2f, 0.8f, 0.2f, 0.35f };
    private static final float[] COL_AHEAD = { 0.1f, 0.9f, 1.0f, 0.65f };
    private static final float[] COL_NEXT  = { 1.0f, 0.9f, 0.1f, 0.85f };

    // Pending (lookahead) path – lighter cyan
    private static final float[] COL_PENDING = { 0.4f, 0.8f, 1.0f, 0.30f };

    private static final double NODE_HALF = 0.13;

    // ─────────────────────────────────────────────────────────────────────────
    public AutoWalk() {
        super("AutoWalk", "Automatically walks forward or smart-pathfinds", Category.PLAYER);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        activeMode = mode.getValue() != null ? mode.getValue() : WalkMode.FORWARD;
        if (activeMode == WalkMode.SMART) {
            initSmartState();
        }
    }

    @Override
    public void onDisable() {
        releaseAll();
    }

    private void initSmartState() {
        if (chosenDirection < 0) chosenDirection = new Random().nextInt(4);
        currentPath.clear();
        pendingPath.clear();
        exploredNodes.clear();
        pathIndex         = 0;
        replanCooldown    = 0;
        lookaheadTimer    = 0;
        stuckTimer        = 0;
        stuckRefPos       = null;
        stuckJustHandled  = false;
    }

    public void resetDirection() { chosenDirection = -1; }

    // ── onTick ────────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        WalkMode current = mode.getValue();
        if (current != null && current != activeMode) {
            activeMode = current;
            if (activeMode == WalkMode.SMART) initSmartState();
        }

        if (activeMode == WalkMode.FORWARD) {
            mc.options.forwardKey.setPressed(true);
            return;
        }

        tickSmart();
    }

    // ── render3D ──────────────────────────────────────────────────────────────

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (activeMode != WalkMode.SMART) return;
        if (mc.player == null) return;

        Vec3d cam = camera.getCameraPos();
        VertexConsumer vc = RenderUtil.beginBatch();

        // 1) Exploration nodes (orange, very transparent) ─────────────────────
        for (int i = 0; i < exploredNodes.size(); i++) {
            BlockPos bp = exploredNodes.get(i);
            double rx = bp.getX() + 0.5 - cam.x;
            double ry = bp.getY()       - cam.y;
            double rz = bp.getZ() + 0.5 - cam.z;

            Box nb = new Box(rx - NODE_HALF * 0.7, ry, rz - NODE_HALF * 0.7,
                    rx + NODE_HALF * 0.7, ry + NODE_HALF * 1.4, rz + NODE_HALF * 0.7);
            RenderUtil.batchFilledBox(vc, posMatrix, nb,
                    COL_EXPLORE[0], COL_EXPLORE[1], COL_EXPLORE[2], COL_EXPLORE[3]);

            if (i + 1 < exploredNodes.size()) {
                BlockPos next = exploredNodes.get(i + 1);
                double nx = next.getX() + 0.5 - cam.x;
                double ny = next.getY()       - cam.y;
                double nz = next.getZ() + 0.5 - cam.z;
                drawSegment(vc, posMatrix,
                        rx, ry + NODE_HALF * 0.7, rz,
                        nx, ny + NODE_HALF * 0.7, nz,
                        COL_EXPLORE_SEG[0], COL_EXPLORE_SEG[1], COL_EXPLORE_SEG[2], COL_EXPLORE_SEG[3]);
            }
        }

        // 2) Pending / lookahead path (light cyan) ────────────────────────────
        for (int i = 0; i < pendingPath.size(); i++) {
            BlockPos bp = pendingPath.get(i);
            double rx = bp.getX() + 0.5 - cam.x;
            double ry = bp.getY()       - cam.y;
            double rz = bp.getZ() + 0.5 - cam.z;

            Box nb = new Box(rx - NODE_HALF, ry, rz - NODE_HALF,
                    rx + NODE_HALF, ry + 2 * NODE_HALF, rz + NODE_HALF);
            RenderUtil.batchFilledBox(vc, posMatrix, nb,
                    COL_PENDING[0], COL_PENDING[1], COL_PENDING[2], COL_PENDING[3]);

            if (i + 1 < pendingPath.size()) {
                BlockPos next = pendingPath.get(i + 1);
                double nx = next.getX() + 0.5 - cam.x;
                double ny = next.getY()       - cam.y;
                double nz = next.getZ() + 0.5 - cam.z;
                drawSegment(vc, posMatrix,
                        rx, ry + NODE_HALF, rz, nx, ny + NODE_HALF, nz,
                        COL_PENDING[0], COL_PENDING[1], COL_PENDING[2], COL_PENDING[3]);
            }
        }

        // 3) Active / final path (green → yellow → cyan) ──────────────────────
        for (int i = 0; i < currentPath.size(); i++) {
            BlockPos bp = currentPath.get(i);
            double rx = bp.getX() + 0.5 - cam.x;
            double ry = bp.getY()       - cam.y;
            double rz = bp.getZ() + 0.5 - cam.z;

            float[] col;
            if      (i < pathIndex)  col = COL_DONE;
            else if (i == pathIndex) col = COL_NEXT;
            else                     col = COL_AHEAD;

            Box nodeBox = new Box(rx - NODE_HALF, ry, rz - NODE_HALF,
                    rx + NODE_HALF, ry + 2 * NODE_HALF, rz + NODE_HALF);
            RenderUtil.batchFilledBox(vc, posMatrix, nodeBox,
                    col[0], col[1], col[2], col[3]);

            if (i + 1 < currentPath.size()) {
                BlockPos next = currentPath.get(i + 1);
                double nx = next.getX() + 0.5 - cam.x;
                double ny = next.getY()       - cam.y;
                double nz = next.getZ() + 0.5 - cam.z;
                float[] lc = (i < pathIndex) ? COL_DONE : COL_AHEAD;
                drawSegment(vc, posMatrix,
                        rx, ry + NODE_HALF, rz, nx, ny + NODE_HALF, nz,
                        lc[0], lc[1], lc[2], lc[3]);
            }
        }

        RenderUtil.endBatch();
    }

    private void drawSegment(VertexConsumer vc, Matrix4f m,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             float r, float g, float b, float a) {
        double dx = bx - ax, dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return;

        float hw = 0.04f;
        float px = (float)(  dz / len * hw);
        float pz = (float)(-dx / len * hw);

        float x1 = (float)(ax + px), z1 = (float)(az + pz);
        float x2 = (float)(ax - px), z2 = (float)(az - pz);
        float x3 = (float)(bx - px), z3 = (float)(bz - pz);
        float x4 = (float)(bx + px), z4 = (float)(bz + pz);
        float ya = (float) ay, yb = (float) by;
        int light = 15728880;

        vc.vertex(m, x1, ya, z1).color(r, g, b, a).light(light);
        vc.vertex(m, x2, ya, z2).color(r, g, b, a).light(light);
        vc.vertex(m, x3, yb, z3).color(r, g, b, a).light(light);
        vc.vertex(m, x4, yb, z4).color(r, g, b, a).light(light);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SMART tick
    // ══════════════════════════════════════════════════════════════════════════

    private void tickSmart() {
        releaseAll();
        if (replanCooldown > 0) replanCooldown--;

        // ── Stuck detection ───────────────────────────────────────────────────
        tickStuckDetection();

        // ── Off-route check ───────────────────────────────────────────────────
        if (!currentPath.isEmpty() && isOffRoute()) {
            currentPath.clear();
            pendingPath.clear();
            exploredNodes.clear();
        }

        // ── Initial path planning ─────────────────────────────────────────────
        if (currentPath.isEmpty() && replanCooldown == 0) {
            if (walkType.getValue() == WalkType.RANDOM) {
                chosenDirection = new Random().nextInt(4);
            }
            planPath(mc.player.getBlockPos());
            replanCooldown = REPLAN_COOLDOWN;
            lookaheadTimer = LOOKAHEAD_INTERVAL; // reset lookahead after full replan
        }

        // ── Continuous lookahead replanning (every 10 s) ──────────────────────
        if (!currentPath.isEmpty()) {
            lookaheadTimer--;
            if (lookaheadTimer <= 0) {
                lookaheadTimer = LOOKAHEAD_INTERVAL;
                scheduleLookahead();
            }
        }

        // ── Swap in pending path when current path is almost consumed ─────────
        if (!pendingPath.isEmpty() && pathIndex >= currentPath.size() - 8) {
            // Append pending path seamlessly to the end of current path
            currentPath.addAll(pendingPath);
            pendingPath.clear();
        }

        // ── Follow ────────────────────────────────────────────────────────────
        if (currentPath.isEmpty()) {
            mc.options.forwardKey.setPressed(true);
        } else {
            followPath();
        }
    }

    // ── Stuck detection ───────────────────────────────────────────────────────

    private void tickStuckDetection() {
        if (mc.player == null) return;
        Vec3d pos = mc.player.getEntityPos();

        if (stuckRefPos == null) {
            stuckRefPos = pos;
            stuckTimer  = 0;
            return;
        }

        stuckTimer++;
        double moved = pos.distanceTo(stuckRefPos);

        if (stuckTimer >= STUCK_TICKS) {
            if (moved < STUCK_MIN_DIST && !stuckJustHandled) {
                // Player is stuck – force immediate replan from current position
                handleStuck();
                stuckJustHandled = true;
            } else {
                stuckJustHandled = false;
            }
            // Reset window
            stuckRefPos = pos;
            stuckTimer  = 0;
        }
    }

    private void handleStuck() {
        currentPath.clear();
        pendingPath.clear();
        exploredNodes.clear();
        pathIndex      = 0;
        replanCooldown = 0;

        // Pick a fresh direction when using RANDOM to avoid replanning the same blocked path
        if (walkType.getValue() == WalkType.RANDOM) {
            chosenDirection = new Random().nextInt(4);
        }
    }

    // ── Lookahead: plan the NEXT segment from the tail of the current path ────

    /**
     * Computes the next path segment starting from the last node in currentPath
     * (or from the player if the path is empty).  Result goes into pendingPath
     * so the main path is not interrupted.
     */
    private void scheduleLookahead() {
        pendingPath.clear();

        BlockPos origin;
        if (!currentPath.isEmpty()) {
            origin = currentPath.get(currentPath.size() - 1);
        } else {
            if (mc.player == null) return;
            origin = mc.player.getBlockPos();
        }

        // For RANDOM mode, pick a new direction each lookahead so we keep exploring
        if (walkType.getValue() == WalkType.RANDOM) {
            chosenDirection = new Random().nextInt(4);
        }

        BlockPos target = findTarget(origin);
        if (target == null) return;

        List<BlockPos> path = astarWithExploreCapture(origin, target, false /* don't overwrite exploredNodes yet */);
        if (path != null && !path.isEmpty()) {
            // Store exploration data for render (overwrite with latest run)
            // We do a second, cheap call just for the snapshot – or reuse from last planPath
            pendingPath.addAll(path);
        }
    }

    // ── Path planning ─────────────────────────────────────────────────────────

    private void planPath(BlockPos start) {
        currentPath.clear();
        pathIndex = 0;

        BlockPos target = findTarget(start);
        if (target == null) return;

        List<BlockPos> path = astarWithExploreCapture(start, target, true);
        if (path != null && !path.isEmpty()) {
            currentPath.addAll(path);
        }
    }

    private BlockPos findTarget(BlockPos origin) {
        World world = mc.world;
        if (walkType.getValue() == WalkType.COORDINATE) {
            double dx = targetX.getValue() - origin.getX();
            double dz = targetZ.getValue() - origin.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 3.0) {
                // Near coordinate target – switch to RANDOM to keep exploring
                chosenDirection = new Random().nextInt(4);
                return findTarget_Random(origin, world);
            }
            double stepX = dx / len, stepZ = dz / len;
            for (int dist = TARGET_DISTANCE; dist >= 16; dist -= 4) {
                int actualDist = (int) Math.min(dist, Math.ceil(len));
                int tx = origin.getX() + (int) Math.round(stepX * actualDist);
                int tz = origin.getZ() + (int) Math.round(stepZ * actualDist);
                BlockPos candidate = findSafeY(world, new BlockPos(tx, origin.getY(), tz));
                if (candidate != null) return candidate;
            }
            return null;
        } else {
            return findTarget_Random(origin, world);
        }
    }

    private BlockPos findTarget_Random(BlockPos origin, World world) {
        int stepX = DIR_DX[chosenDirection];
        int stepZ = DIR_DZ[chosenDirection];
        for (int dist = TARGET_DISTANCE; dist >= 16; dist -= 4) {
            int tx = origin.getX() + stepX * dist;
            int tz = origin.getZ() + stepZ * dist;
            BlockPos candidate = findSafeY(world, new BlockPos(tx, origin.getY(), tz));
            if (candidate != null) return candidate;
        }
        return null;
    }

    private BlockPos findSafeY(World world, BlockPos xzBase) {
        for (int dy = 0; dy <= 12; dy++) {
            for (int sign : new int[]{ 0, 1, -1 }) {
                BlockPos check = xzBase.add(0, dy * sign, 0);
                if (isSafeStandingPos(world, check)) return check;
            }
        }
        return null;
    }

    // ── A* – with optional exploration node capture ────────────────────────────

    /**
     * @param captureExplored if true, overwrites {@link #exploredNodes} with a
     *                        sampled snapshot of all visited nodes so they can be
     *                        rendered as the "thinking" overlay.
     */
    private List<BlockPos> astarWithExploreCapture(BlockPos start, BlockPos goal,
                                                   boolean captureExplored) {
        World world = mc.world;
        Map<BlockPos, Float>    gCost  = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        List<BlockPos>          visited = captureExplored ? new ArrayList<>() : null;

        PriorityQueue<BlockPos> open = new PriorityQueue<>(
                Comparator.comparingDouble(p ->
                        gCost.getOrDefault(p, Float.MAX_VALUE) + heuristic(p, goal))
        );

        gCost.put(start, 0f);
        open.add(start);
        Set<BlockPos> closed = new HashSet<>();

        BlockPos bestNode = start;
        float    bestDist = heuristic(start, goal);

        int iter = 0;
        while (!open.isEmpty() && iter++ < MAX_ITERATIONS) {
            BlockPos cur = open.poll();
            if (closed.contains(cur)) continue;
            closed.add(cur);

            if (captureExplored && visited != null) visited.add(cur);

            float d = heuristic(cur, goal);
            if (d < bestDist) { bestDist = d; bestNode = cur; }

            if (cur.isWithinDistance(new Vec3d(goal.getX(), goal.getY(), goal.getZ()), 2.5)) {
                if (captureExplored) sampleExplored(visited);
                return reconstructPath(parent, cur);
            }

            for (BlockPos nb : getNeighbors(world, cur)) {
                if (closed.contains(nb)) continue;
                float g = gCost.getOrDefault(cur, Float.MAX_VALUE) + moveCost(world, cur, nb);
                if (g < gCost.getOrDefault(nb, Float.MAX_VALUE)) {
                    gCost.put(nb, g);
                    parent.put(nb, cur);
                    open.add(nb);
                }
            }
        }

        // Fell through max iterations – return best partial path
        if (captureExplored) sampleExplored(visited);
        if (!parent.containsKey(bestNode) && !bestNode.equals(start)) return null;
        return reconstructPath(parent, bestNode);
    }

    /** Stores a random sample of visited nodes (≤ EXPLORE_RENDER_CAP) for rendering. */
    private void sampleExplored(List<BlockPos> visited) {
        exploredNodes.clear();
        if (visited == null || visited.isEmpty()) return;
        if (visited.size() <= EXPLORE_RENDER_CAP) {
            exploredNodes.addAll(visited);
            return;
        }
        // Reservoir-style: evenly spaced sample
        double step = (double) visited.size() / EXPLORE_RENDER_CAP;
        for (int i = 0; i < EXPLORE_RENDER_CAP; i++) {
            exploredNodes.add(visited.get((int)(i * step)));
        }
    }

    private float heuristic(BlockPos a, BlockPos b) {
        return (float) Math.sqrt(a.getSquaredDistance(b));
    }

    private float moveCost(World world, BlockPos from, BlockPos to) {
        float base   = (float) Math.sqrt(from.getSquaredDistance(to));
        float danger = 0;
        for (BlockPos adj : adjacentBlocks(to)) {
            BlockState bs = world.getBlockState(adj);
            if (bs.isOf(Blocks.LAVA)             || bs.isOf(Blocks.MAGMA_BLOCK))  danger += 8f;
            if (bs.isOf(Blocks.FIRE)             || bs.isOf(Blocks.SOUL_FIRE))    danger += 5f;
            if (bs.isOf(Blocks.CACTUS)           || bs.isOf(Blocks.WITHER_ROSE))  danger += 3f;
            if (bs.isOf(Blocks.SWEET_BERRY_BUSH))                                 danger += 1f;
            if (bs.isOf(Blocks.WATER)            || bs.isOf(Blocks.BUBBLE_COLUMN)) danger += 12f;
        }
        int drop = from.getY() - to.getY();
        if (drop > 2) danger += drop * 2f;
        return base + danger;
    }

    private List<BlockPos> getNeighbors(World world, BlockPos pos) {
        List<BlockPos> result = new ArrayList<>(12);
        int[] hDelta = { -1, 0,  1, 0 };
        int[] zDelta = {  0, -1, 0, 1 };
        for (int i = 0; i < 4; i++) {
            int nx = pos.getX() + hDelta[i];
            int nz = pos.getZ() + zDelta[i];
            BlockPos flat = new BlockPos(nx, pos.getY(), nz);
            if (isSafeStandingPos(world, flat)) result.add(flat);
            BlockPos up = flat.up();
            if (isSafeStandingPos(world, up) && isPassable(world, pos.up())) result.add(up);
            BlockPos down = flat.down();
            if (isSafeStandingPos(world, down)) result.add(down);
        }
        return result;
    }

    private List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> parent, BlockPos end) {
        LinkedList<BlockPos> path = new LinkedList<>();
        BlockPos cur = end;
        while (parent.containsKey(cur)) {
            path.addFirst(cur);
            cur = parent.get(cur);
        }
        return path;
    }

    // ── Path following ────────────────────────────────────────────────────────

    private void followPath() {
        // Advance pathIndex past already-reached waypoints
        while (pathIndex < currentPath.size()) {
            BlockPos wp = currentPath.get(pathIndex);
            Vec3d pPos  = mc.player.getEntityPos();
            double dx   = (wp.getX() + 0.5) - pPos.x;
            double dz   = (wp.getZ() + 0.5) - pPos.z;
            if (Math.sqrt(dx * dx + dz * dz) > 0.55) break;
            pathIndex++;
        }

        if (pathIndex >= currentPath.size()) {
            // End reached – if no pending path is waiting, clear and let tickSmart replan
            if (pendingPath.isEmpty()) {
                currentPath.clear();
                pathIndex          = 0;
                replanCooldown     = 0;
                sprintJumpCooldown = 0;
            }
            // (if pendingPath exists it will be appended next tick by tickSmart)
            return;
        }

        BlockPos next = currentPath.get(pathIndex);
        Vec3d pPos = mc.player.getEntityPos();
        double dx = (next.getX() + 0.5) - pPos.x;
        double dz = (next.getZ() + 0.5) - pPos.z;

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        mc.player.setYaw(targetYaw);
        mc.options.forwardKey.setPressed(true);

        int dy = next.getY() - mc.player.getBlockPos().getY();

        if (jumpCooldown > 0) jumpCooldown--;
        if (dy >= 1 && mc.player.isOnGround() && jumpCooldown == 0) {
            mc.options.jumpKey.setPressed(true);
            jumpCooldown = 10;
        }

        mc.options.sneakKey.setPressed(dy < -3);

        if (sprintJumpCooldown > 0) sprintJumpCooldown--;
        if (isLongFlatStraight()) {
            mc.options.sprintKey.setPressed(true);
            if (mc.player.isOnGround() && sprintJumpCooldown == 0 && jumpCooldown == 0) {
                mc.options.jumpKey.setPressed(true);
                sprintJumpCooldown = 8;
            }
        } else {
            mc.options.sprintKey.setPressed(false);
        }
    }

    private boolean isLongFlatStraight() {
        int lookahead = 6;
        int end = Math.min(currentPath.size() - 1, pathIndex + lookahead);
        if (end - pathIndex < lookahead) return false;

        int baseY = currentPath.get(pathIndex).getY();
        for (int i = pathIndex; i <= end; i++) {
            if (currentPath.get(i).getY() != baseY) return false;
        }

        for (int i = pathIndex; i < end - 1; i++) {
            BlockPos a = currentPath.get(i);
            BlockPos b = currentPath.get(i + 1);
            BlockPos c = currentPath.get(i + 2);
            double ax = b.getX() - a.getX(), az = b.getZ() - a.getZ();
            double bx = c.getX() - b.getX(), bz = c.getZ() - b.getZ();
            double lenA = Math.sqrt(ax * ax + az * az);
            double lenB = Math.sqrt(bx * bx + bz * bz);
            if (lenA < 1e-4 || lenB < 1e-4) continue;
            double dot = (ax / lenA) * (bx / lenB) + (az / lenA) * (bz / lenB);
            if (dot < 0.7) return false;
        }
        return true;
    }

    // ── Off-route detection ───────────────────────────────────────────────────

    private boolean isOffRoute() {
        Vec3d pos = mc.player.getEntityPos().add(0, 1, 0);
        int lo = Math.max(0, pathIndex - 1);
        int hi = Math.min(currentPath.size() - 1, pathIndex + 4);
        for (int i = lo; i <= hi; i++) {
            BlockPos bp = currentPath.get(i);
            double dist = pos.distanceTo(new Vec3d(bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5));
            if (dist <= OFF_ROUTE_DISTANCE) return false;
        }
        return true;
    }

    // ── Safety helpers ────────────────────────────────────────────────────────

    private boolean isSafeStandingPos(World world, BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        BlockPos floor = pos.down();
        BlockState floorState = world.getBlockState(floor);
        if (floorState.isAir()) return false;
        if (floorState.getCollisionShape(world, floor).isEmpty()) return false;
        if (!isPassable(world, pos))      return false;
        if (!isPassable(world, pos.up())) return false;
        if (isHazard(world, floor))       return false;
        if (isHazard(world, pos))         return false;
        return true;
    }

    private boolean isPassable(World world, BlockPos pos) {
        BlockState bs = world.getBlockState(pos);
        if (bs.isAir()) return true;
        return bs.getCollisionShape(world, pos).isEmpty();
    }

    private boolean isHazard(World world, BlockPos pos) {
        BlockState bs = world.getBlockState(pos);
        return bs.isOf(Blocks.LAVA)
                || bs.isOf(Blocks.WATER)
                || bs.isOf(Blocks.BUBBLE_COLUMN)
                || bs.isOf(Blocks.FIRE)
                || bs.isOf(Blocks.SOUL_FIRE)
                || bs.isOf(Blocks.MAGMA_BLOCK)
                || bs.isOf(Blocks.CACTUS)
                || bs.isOf(Blocks.SWEET_BERRY_BUSH)
                || bs.isOf(Blocks.WITHER_ROSE);
    }

    private List<BlockPos> adjacentBlocks(BlockPos pos) {
        return List.of(pos.north(), pos.south(), pos.east(), pos.west(), pos.down(), pos);
    }

    // ── Keys ──────────────────────────────────────────────────────────────────

    private void releaseAll() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void cycleMode() {
        WalkMode next = (activeMode == WalkMode.FORWARD) ? WalkMode.SMART : WalkMode.FORWARD;
        activeMode = next;
        mode.setValue(next);
        if (next == WalkMode.SMART) initSmartState();
        else currentPath.clear();
    }

    public String getModeName()      { return activeMode.name(); }
    public String getDirectionName() { return chosenDirection < 0 ? "?" : DIR_NAMES[chosenDirection]; }
    public int    getPathProgress()  { return pathIndex; }
    public int    getPathSize()      { return currentPath.size(); }
    public int    getPendingSize()   { return pendingPath.size(); }
    public int    getExploredSize()  { return exploredNodes.size(); }
}