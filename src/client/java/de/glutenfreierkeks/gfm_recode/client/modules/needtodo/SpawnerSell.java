package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.InventorySlotSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

public class SpawnerSell extends Module {

    private static final Random RANDOM = new Random();

    // ── Settings ──────────────────────────────────────────────────────────────
    private final IntSliderSetting range = register(new IntSliderSetting("Range", "Scan range", 5, 1, 10));

    private final InventorySlotSetting chestSlot = register(new InventorySlotSetting("ChestSlot", "Slot to open storage/chest", InventorySlotSetting.Layout.CHEST_9x6, false));
    private final InventorySlotSetting sellSlot  = register(new InventorySlotSetting("SellSlot", "Slot for selling (gold ingot)", InventorySlotSetting.Layout.CHEST_9x6, false));

    private final IntSliderSetting rotSpeed           = register(new IntSliderSetting("RotSpeed", "Rotation speed", 8, 1, 10));
    private final BoolSetting requireLookAngle   = register(new BoolSetting("RequireLookAngle", "Only interact when looking at spawner", true));
    private final DoubleSliderSetting lookAngleTolerance = register(new DoubleSliderSetting("LookTolerance", "Angle tolerance", 30.0, 5.0, 90.0));
    private final IntSliderSetting postRotationDelay  = register(new IntSliderSetting("PostRotDelay", "Delay after rotation", 3, 0, 20));
    private final IntSliderSetting postRotationRandom = register(new IntSliderSetting("PostRotRandom", "Randomize rotation delay", 2, 0, 10));

    private final IntSliderSetting spawnerClickDelay  = register(new IntSliderSetting("SpawnerClickDelay", "Delay before clicking spawner", 20, 1, 200));
    private final IntSliderSetting spawnerClickRandom = register(new IntSliderSetting("SpawnerClickRandom", "Randomize spawner click", 5, 0, 20));
    private final IntSliderSetting guiCheckDelay      = register(new IntSliderSetting("GuiCheckDelay", "Delay before GUI check", 10, 1, 100));
    private final IntSliderSetting chestClickDelay    = register(new IntSliderSetting("ChestClickDelay", "Delay before chest click", 10, 1, 100));
    private final IntSliderSetting chestClickRandom   = register(new IntSliderSetting("ChestClickRandom", "Randomize chest click", 3, 0, 20));
    private final IntSliderSetting goldClickDelay     = register(new IntSliderSetting("GoldClickDelay", "Delay before gold click", 10, 1, 100));
    private final IntSliderSetting goldClickRandom    = register(new IntSliderSetting("GoldClickRandom", "Randomize gold click", 3, 0, 20));

    private final BoolSetting esp  = register(new BoolSetting("ESP", "Show spawner ESP", true));
    private final IntSliderSetting espR = register(new IntSliderSetting("EspRed", "ESP red", 255, 0, 255));
    private final IntSliderSetting espG = register(new IntSliderSetting("EspGreen", "ESP green", 165, 0, 255));
    private final IntSliderSetting espB = register(new IntSliderSetting("EspBlue", "ESP blue", 0, 0, 255));
    private final IntSliderSetting espA = register(new IntSliderSetting("EspAlpha", "ESP alpha", 200, 0, 255));

    // ── State ─────────────────────────────────────────────────────────────────
    private final Queue<BlockPos> spawnerQueue = new LinkedList<>();
    private       List<BlockPos>  allSpawners  = new ArrayList<>();
    private BlockPos current      = null;
    private int      tickCooldown = 0;
    private int      postRotTicks = 0;
    private int      rotatingTicks = 0;
    private int      soldCount    = 0;

    private static final int ROT_SETTLE_TICKS = 3;

    private enum State {
        SEARCH, ROTATING, POST_ROT_WAIT, INTERACT, GUI_CHECK, CLICK_CHEST, CLICK_GOLD
    }
    private State state = State.SEARCH;
    private int scanDelay = 0;

    public SpawnerSell() {
        super("SpawnerSell", "Automatically sells spawners in the area.", Category.FARM);
        chestSlot.addSuggestion(13, new ItemStack(Items.CHEST), "Storage/Chest Slot");
        sellSlot.addSuggestion(49, new ItemStack(Items.GOLD_INGOT), "Sell Button");
    }

    @Override
    public void onEnable() {
        spawnerQueue.clear();
        allSpawners = new ArrayList<>();
        current = null;
        state = State.SEARCH;
        tickCooldown = 0;
        postRotTicks = 0;
        rotatingTicks = 0;
        soldCount = 0;
        scanDelay = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        RotationUtil.onTick();

        if (tickCooldown > 0) {
            tickCooldown--;
            return;
        }

        switch (state) {
            case SEARCH -> {
                if (scanDelay > 0) {
                    scanDelay--;
                } else {
                    findSpawners();
                    scanDelay = 20; // Scan only every 1s (20 ticks) when idle
                }
                nextSpawner();
            }
            case ROTATING -> {
                if (current == null) { state = State.SEARCH; return; }
                Vec3d center = current.toCenterPos();
                float[] tRot = RotationUtil.getRotations(center);
                float factor = Math.min(0.95f, rotSpeed.getValue() * 0.1f);
                mc.player.setYaw(mc.player.getYaw() + wrapDeg(tRot[0] - mc.player.getYaw()) * factor);
                mc.player.setPitch(mc.player.getPitch() + (tRot[1] - mc.player.getPitch()) * factor);

                if (!isLookingAt(current)) {
                    rotatingTicks = 0;
                    return;
                }

                rotatingTicks++;
                if (rotatingTicks < ROT_SETTLE_TICKS) return;
                rotatingTicks = 0;

                postRotTicks = randomDelay(postRotationDelay.getValue(), postRotationRandom.getValue());
                state = State.POST_ROT_WAIT;
            }
            case POST_ROT_WAIT -> {
                if (postRotTicks > 0) { postRotTicks--; return; }
                state = State.INTERACT;
            }
            case INTERACT -> {
                if (current == null) { state = State.SEARCH; return; }
                if (requireLookAngle.getValue() && !isLookingAt(current)) {
                    rotatingTicks = 0;
                    state = State.ROTATING;
                    return;
                }

                Vec3d eyePos = mc.player.getEyePos();
                Vec3d blockCenter = current.toCenterPos();
                Vec3d dir = blockCenter.subtract(eyePos).normalize();
                double hx = MathHelper.clamp(eyePos.x + dir.x * 0.5, current.getX()+0.01, current.getX()+0.99);
                double hy = MathHelper.clamp(eyePos.y + dir.y * 0.5, current.getY()+0.01, current.getY()+0.99);
                double hx_ = hx; // local var for clamp results
                double hz = MathHelper.clamp(eyePos.z + dir.z * 0.5, current.getZ()+0.01, current.getZ()+0.99);

                double dx = eyePos.x - blockCenter.x, dy = eyePos.y - blockCenter.y, dz = eyePos.z - blockCenter.z;
                double adx = Math.abs(dx), ady = Math.abs(dy), adz = Math.abs(dz);
                Direction face;
                if      (ady >= adx && ady >= adz) face = dy > 0 ? Direction.UP    : Direction.DOWN;
                else if (adx >= adz)                face = dx > 0 ? Direction.EAST  : Direction.WEST;
                else                                face = dz > 0 ? Direction.SOUTH : Direction.NORTH;

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                        new BlockHitResult(new Vec3d(hx, hy, hz), face, current, false));
                mc.player.swingHand(Hand.MAIN_HAND);

                state = State.GUI_CHECK;
                tickCooldown = randomDelay(guiCheckDelay.getValue(), 3);
            }
            case GUI_CHECK -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                var handler = screen.getScreenHandler();

                List<Integer> cSlots = chestSlot.getValue();
                if (cSlots.isEmpty()) {
                    boolean found = false;
                    for (int i = 0; i < handler.slots.size(); i++) {
                        if (handler.getSlot(i).getStack().getItem() == Items.CHEST) {
                            mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                            state = State.CLICK_CHEST;
                            tickCooldown = randomDelay(chestClickDelay.getValue(), chestClickRandom.getValue());
                            found = true;
                            break;
                        }
                    }
                    if (!found) finishCurrentSpawner();
                } else {
                    mc.interactionManager.clickSlot(handler.syncId, cSlots.get(0), 0, SlotActionType.PICKUP, mc.player);
                    state = State.CLICK_CHEST;
                    tickCooldown = randomDelay(chestClickDelay.getValue(), chestClickRandom.getValue());
                }
            }
            case CLICK_CHEST -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                var handler = screen.getScreenHandler();
                List<Integer> gSlots = sellSlot.getValue();

                if (gSlots.isEmpty()) {
                    if (handler.slots.size() > 49 && handler.getSlot(49).getStack().getItem() == Items.GOLD_INGOT) {
                        mc.interactionManager.clickSlot(handler.syncId, 49, 0, SlotActionType.PICKUP, mc.player);
                        state = State.CLICK_GOLD;
                        tickCooldown = randomDelay(goldClickDelay.getValue(), goldClickRandom.getValue());
                    } else {
                        finishCurrentSpawner();
                    }
                } else {
                    mc.interactionManager.clickSlot(handler.syncId, gSlots.get(0), 0, SlotActionType.PICKUP, mc.player);
                    state = State.CLICK_GOLD;
                    tickCooldown = randomDelay(goldClickDelay.getValue(), goldClickRandom.getValue());
                }
            }
            case CLICK_GOLD -> finishCurrentSpawner();
        }
    }

    private void nextSpawner() {
        if (!spawnerQueue.isEmpty()) {
            current = spawnerQueue.poll();
            rotatingTicks = 0;
            state = State.ROTATING;
            tickCooldown = randomDelay(spawnerClickDelay.getValue(), spawnerClickRandom.getValue());
        } else {
            current = null;
            state = State.SEARCH;
        }
    }

    private void finishCurrentSpawner() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
        if (current != null) {
            soldCount++;
            msg("§a[SpawnerSell] Sold spawner! §7(" + soldCount + " total)");
        }
        current = null;
        rotatingTicks = 0;
        tickCooldown = randomDelay(5, 2);
        nextSpawner();
    }

    private void findSpawners() {
        spawnerQueue.clear();
        allSpawners = new ArrayList<>();
        if (mc.player == null) return;
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.getValue();

        for (int x = playerPos.getX() - r; x <= playerPos.getX() + r; x++)
            for (int y = playerPos.getY() - 10; y <= playerPos.getY() + 10; y++)
                for (int z = playerPos.getZ() - r; z <= playerPos.getZ() + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > (double) (r * r)) continue;
                    BlockEntity be = mc.world.getBlockEntity(pos);
                    if (be instanceof MobSpawnerBlockEntity) {
                        allSpawners.add(pos);
                        if (!spawnerQueue.contains(pos)) spawnerQueue.add(pos);
                    }
                }
    }

    private boolean isLookingAt(BlockPos pos) {
        if (mc.player == null) return false;
        float[] needed = RotationUtil.getRotations(pos.toCenterPos());
        float dyaw   = Math.abs(wrapDeg(needed[0] - mc.player.getYaw()));
        float dpitch = Math.abs(wrapDeg(needed[1] - mc.player.getPitch()));
        double tol = lookAngleTolerance.getValue();
        return dyaw <= tol && dpitch <= tol;
    }

    private static float wrapDeg(float d) {
        while (d >  180f) d -= 360f;
        while (d < -180f) d += 360f;
        return d;
    }

    private int randomDelay(int base, int jitter) {
        int v = base + (jitter > 0 ? RANDOM.nextInt(jitter * 2 + 1) - jitter : 0);
        return Math.max(0, v);
    }

    private void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    @Override
    public String getDisplayInfo() {
        return state.name().toLowerCase() + (soldCount > 0 ? " | " + soldCount : "");
    }
}
