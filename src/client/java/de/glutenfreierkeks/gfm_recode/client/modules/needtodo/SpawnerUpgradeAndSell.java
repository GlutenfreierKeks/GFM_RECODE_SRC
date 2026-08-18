package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.InventorySlotSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import net.minecraft.client.render.Camera;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class SpawnerUpgradeAndSell extends Module {

    private final IntSliderSetting range = register(new IntSliderSetting("Range", "Scan range", 5, 1, 10));

    private final InventorySlotSetting upgradeSlot = register(new InventorySlotSetting("UpgradeSlot", "Slot for upgrading (where items are placed)", InventorySlotSetting.Layout.CHEST_9x6, false));
    private final InventorySlotSetting chestSlot = register(new InventorySlotSetting("ChestSlot", "Slot to open storage/chest", InventorySlotSetting.Layout.CHEST_9x6, false));
    private final InventorySlotSetting sellSlot = register(new InventorySlotSetting("SellSlot", "Slot for selling (gold ingot)", InventorySlotSetting.Layout.CHEST_9x6, false));

    private final IntSliderSetting spawnerClickDelay = register(new IntSliderSetting("SpawnerClickDelay", "Delay before spawner click", 1000, 50, 10000));
    private final IntSliderSetting guiActionDelay = register(new IntSliderSetting("GuiActionDelay", "Delay between GUI actions", 500, 50, 5000));
    private final IntSliderSetting upgradeDelay = register(new IntSliderSetting("UpgradeDelay", "Delay during upgrade", 300, 50, 3000));

    private final Queue<BlockPos> spawners = new LinkedList<>();
    private BlockPos current = null;
    private long lastActionTime = 0L;

    private enum State {
        SEARCH, INTERACT, GUI_CHECK, CHECK_PAPER, UPGRADE_WITH_PAPER, RETURN_PAPER,
        CHECK_EMERALD, UPGRADE_WITH_EMERALD, RETURN_EMERALD, CLICK_CHEST, CLICK_GOLD
    }

    private State state = State.SEARCH;
    private int scanDelay = 0;
    private int paperSlot = -1;
    private int emeraldSlot = -1;
    private boolean hasPaper = false;
    private boolean hasEmerald = false;

    public SpawnerUpgradeAndSell() {
        super("SpawnerUpgradeAndSell", "Automatically upgrades and sells spawners.", Category.FARM);
        upgradeSlot.addSuggestion(22, new ItemStack(Items.HOPPER), "Upgrade Slot");
        chestSlot.addSuggestion(13, new ItemStack(Items.CHEST), "Storage/Chest Slot");
        sellSlot.addSuggestion(49, new ItemStack(Items.GOLD_INGOT), "Sell Button");
    }

    @Override
    public void onEnable() {
        msg("§aSpawner Upgrade & Sell enabled!");
        spawners.clear();
        current = null;
        state = State.SEARCH;
        lastActionTime = System.currentTimeMillis();
        resetUpgradeState();
        scanDelay = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        switch (state) {
            case SEARCH -> {
                if (scanDelay > 0) {
                    scanDelay--;
                } else {
                    findSpawners();
                    scanDelay = 20; // Scan only every 1s
                }
                nextSpawner();
            }
            case INTERACT -> {
                if (current == null) { state = State.SEARCH; return; }
                RotationUtil.onTick();
                
                // Simplified rotation for this module
                float[] rots = RotationUtil.getRotations(current.toCenterPos());
                mc.player.setYaw(rots[0]);
                mc.player.setPitch(rots[1]);

                if (System.currentTimeMillis() - lastActionTime < spawnerClickDelay.getValue()) return;

                BlockHitResult bhr = new BlockHitResult(current.toCenterPos(), Direction.UP, current, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.swingHand(Hand.MAIN_HAND);

                state = State.GUI_CHECK;
                lastActionTime = System.currentTimeMillis();
            }
            case GUI_CHECK -> {
                if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
                if (System.currentTimeMillis() - lastActionTime < guiActionDelay.getValue()) return;
                state = State.CHECK_PAPER;
                lastActionTime = System.currentTimeMillis();
            }
            case CHECK_PAPER -> {
                if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
                paperSlot = findItemInInventory(Items.PAPER);
                hasPaper = paperSlot != -1;
                if (hasPaper) {
                    msg("§ePaper found! Upgrading...");
                    state = State.UPGRADE_WITH_PAPER;
                } else {
                    state = State.CHECK_EMERALD;
                }
                lastActionTime = System.currentTimeMillis();
            }
            case UPGRADE_WITH_PAPER -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                if (System.currentTimeMillis() - lastActionTime < upgradeDelay.getValue()) return;
                var handler = screen.getScreenHandler();
                List<Integer> slots = upgradeSlot.getValue();
                if (slots.isEmpty()) { finishCurrentSpawner(); return; }
                int upSlot = slots.get(0);

                click(handler.syncId, paperSlot);
                click(handler.syncId, upSlot);
                state = State.RETURN_PAPER;
                lastActionTime = System.currentTimeMillis();
            }
            case RETURN_PAPER -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                if (System.currentTimeMillis() - lastActionTime < upgradeDelay.getValue()) return;
                var handler = screen.getScreenHandler();
                List<Integer> slots = upgradeSlot.getValue();
                if (slots.isEmpty()) { finishCurrentSpawner(); return; }
                int upSlot = slots.get(0);

                click(handler.syncId, upSlot);
                click(handler.syncId, paperSlot);
                state = State.CHECK_EMERALD;
                lastActionTime = System.currentTimeMillis();
            }
            case CHECK_EMERALD -> {
                if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
                emeraldSlot = findItemInInventory(Items.EMERALD);
                hasEmerald = emeraldSlot != -1;
                if (hasEmerald) {
                    msg("§aEmerald found! Upgrading...");
                    state = State.UPGRADE_WITH_EMERALD;
                } else {
                    state = State.CLICK_CHEST;
                }
                lastActionTime = System.currentTimeMillis();
            }
            case UPGRADE_WITH_EMERALD -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                if (System.currentTimeMillis() - lastActionTime < upgradeDelay.getValue()) return;
                var handler = screen.getScreenHandler();
                List<Integer> slots = upgradeSlot.getValue();
                if (slots.isEmpty()) { finishCurrentSpawner(); return; }
                int upSlot = slots.get(0);

                click(handler.syncId, emeraldSlot);
                click(handler.syncId, upSlot);
                state = State.RETURN_EMERALD;
                lastActionTime = System.currentTimeMillis();
            }
            case RETURN_EMERALD -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                if (System.currentTimeMillis() - lastActionTime < upgradeDelay.getValue()) return;
                var handler = screen.getScreenHandler();
                List<Integer> slots = upgradeSlot.getValue();
                if (slots.isEmpty()) { finishCurrentSpawner(); return; }
                int upSlot = slots.get(0);

                click(handler.syncId, upSlot);
                click(handler.syncId, emeraldSlot);
                state = State.CLICK_CHEST;
                lastActionTime = System.currentTimeMillis();
            }
            case CLICK_CHEST -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                if (System.currentTimeMillis() - lastActionTime < guiActionDelay.getValue()) return;
                var handler = screen.getScreenHandler();
                
                List<Integer> slots = chestSlot.getValue();
                if (slots.isEmpty()) {
                    // Fallback to searching for chest if no slot selected
                    boolean foundChest = false;
                    for (int i = 0; i < handler.slots.size(); i++) {
                        if (handler.getSlot(i).getStack().getItem() == Items.CHEST) {
                            click(handler.syncId, i);
                            state = State.CLICK_GOLD;
                            lastActionTime = System.currentTimeMillis();
                            foundChest = true;
                            break;
                        }
                    }
                    if (!foundChest) finishCurrentSpawner();
                } else {
                    click(handler.syncId, slots.get(0));
                    state = State.CLICK_GOLD;
                    lastActionTime = System.currentTimeMillis();
                }
            }
            case CLICK_GOLD -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                if (System.currentTimeMillis() - lastActionTime < guiActionDelay.getValue()) return;
                var handler = screen.getScreenHandler();
                
                List<Integer> slots = sellSlot.getValue();
                if (slots.isEmpty()) {
                    // Fallback to searching for gold if no slot selected
                    if (handler.slots.size() > 49 && handler.getSlot(49).getStack().getItem() == Items.GOLD_INGOT) {
                        click(handler.syncId, 49);
                        lastActionTime = System.currentTimeMillis();
                    }
                } else {
                    click(handler.syncId, slots.get(0));
                    lastActionTime = System.currentTimeMillis();
                }
                finishCurrentSpawner();
            }
        }
    }

    private void click(int syncId, int slot) {
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private int findItemInInventory(net.minecraft.item.Item item) {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return -1;
        var handler = screen.getScreenHandler();
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    private void resetUpgradeState() {
        paperSlot = -1;
        emeraldSlot = -1;
        hasPaper = false;
        hasEmerald = false;
    }

    private void nextSpawner() {
        if (!spawners.isEmpty()) {
            current = spawners.poll();
            state = State.INTERACT;
            lastActionTime = System.currentTimeMillis();
            resetUpgradeState();
        } else {
            current = null;
            state = State.SEARCH;
        }
    }

    private void finishCurrentSpawner() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
        if (current != null) msg("§aSpawner upgraded and sold!");
        current = null;
        state = State.SEARCH;
        resetUpgradeState();
        nextSpawner();
    }

    private void findSpawners() {
        spawners.clear();
        if (mc.player == null) return;
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.getValue();
        for (int x = playerPos.getX() - r; x <= playerPos.getX() + r; x++)
            for (int y = playerPos.getY() - 10; y <= playerPos.getY() + 10; y++)
                for (int z = playerPos.getZ() - r; z <= playerPos.getZ() + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > r * r) continue;
                    BlockEntity be = mc.world.getBlockEntity(pos);
                    if (be instanceof MobSpawnerBlockEntity && !spawners.contains(pos)) {
                        spawners.add(pos);
                    }
                }
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
        spawners.clear();
        current = null;
        resetUpgradeState();
    }

    private void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    @Override
    public String getDisplayInfo() {
        return current != null ? "Upgrading" : "Idle";
    }
}
