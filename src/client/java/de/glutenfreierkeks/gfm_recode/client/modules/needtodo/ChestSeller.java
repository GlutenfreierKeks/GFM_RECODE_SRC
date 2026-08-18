package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.LinkedList;
import java.util.Queue;

/**
 * ChestSeller – findet automatisch Chests im Umkreis, öffnet sie,
 * nimmt Items raus, öffnet /sell-GUI, legt Items hinein und verkauft sie.
 *
 * Entwickelt für Minecraft 1.21.5 (Yarn mappings)
 */
public class ChestSeller extends Module {

    private final IntSliderSetting range = register(new IntSliderSetting("Range", "Scan range for chests", 5, 1, 10));

    private enum State { SEARCH, OPEN_CHEST, WAIT_CHEST, TAKE_ITEMS, OPEN_SELL, PUT_ITEMS, CLOSE_SELL }
    private State state = State.SEARCH;

    private final Queue<BlockPos> chestQueue = new LinkedList<>();
    private BlockPos currentChest = null;
    private long lastAction = 0L;
    private final long DELAY = 400L;

    private ItemStack[] itemsToSell = new ItemStack[0];

    public ChestSeller() {
        super("ChestSeller", "Automatically sells items from nearby chests via /sell GUI", Category.MISC);
    }

    @Override
    public void onEnable() {
        chestQueue.clear();
        currentChest = null;
        state = State.SEARCH;
        lastAction = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        switch (state) {

            // 🔍 Truhen in Reichweite suchen
            case SEARCH -> {
                findChests();
                nextChest();
            }

            // 🗝️ Truhe öffnen
            case OPEN_CHEST -> {
                if (currentChest == null) {
                    state = State.SEARCH;
                    return;
                }

                if (System.currentTimeMillis() - lastAction < DELAY) return;

                RotationUtil.rotateToBlock(BlockPos.ofFloored(currentChest.toCenterPos()));

                BlockHitResult bhr = new BlockHitResult(
                        currentChest.toCenterPos(),
                        Direction.UP,
                        currentChest,
                        false
                );

                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                lastAction = System.currentTimeMillis();
                state = State.WAIT_CHEST;
            }

            // ⏳ Warten bis Truhen-GUI offen ist
            case WAIT_CHEST -> {
                if (mc.currentScreen instanceof HandledScreen<?> screen &&
                        screen.getTitle().getString().toLowerCase().contains("chest")) {
                    state = State.TAKE_ITEMS;
                    lastAction = System.currentTimeMillis();
                }
            }

            // 📦 Items aus der Truhe nehmen
            case TAKE_ITEMS -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                if (System.currentTimeMillis() - lastAction < DELAY) return;

                var handler = screen.getScreenHandler();

                // Items speichern
                itemsToSell = handler.slots.stream()
                        .filter(s -> s.inventory != mc.player.getInventory())
                        .map(s -> s.getStack().copy())
                        .filter(s -> !s.isEmpty())
                        .toArray(ItemStack[]::new);

                // Alle Items rausnehmen
                for (int i = 0; i < handler.slots.size(); i++) {
                    var slot = handler.getSlot(i);
                    if (slot.inventory != mc.player.getInventory() && slot.hasStack()) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    }
                }

                mc.player.closeHandledScreen();
                lastAction = System.currentTimeMillis();
                state = State.OPEN_SELL;
            }

            // 💰 /sell ausführen
            case OPEN_SELL -> {
                if (System.currentTimeMillis() - lastAction < DELAY * 2) return;
                mc.player.networkHandler.sendChatCommand("sell");
                lastAction = System.currentTimeMillis();
                state = State.PUT_ITEMS;
            }

            // 🛒 Items ins /sell-GUI legen
            case PUT_ITEMS -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                if (System.currentTimeMillis() - lastAction < DELAY) return;

                var handler = screen.getScreenHandler();

                // Richtige Slots erkennen (Player Inventory im GUI)
                for (ItemStack stack : itemsToSell) {
                    if (stack == null || stack.isEmpty()) continue;

                    for (int i = 0; i < handler.slots.size(); i++) {
                        var slot = handler.getSlot(i);
                        if (slot.inventory == mc.player.getInventory()) {
                            ItemStack invStack = slot.getStack();
                            if (invStack.isEmpty()) continue;

                            if (invStack.isOf(stack.getItem())) {
                                mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                                break;
                            }
                        }
                    }
                }

                lastAction = System.currentTimeMillis();
                state = State.CLOSE_SELL;
            }

            // 🚪 GUI schließen, nächste Truhe
            case CLOSE_SELL -> {
                if (System.currentTimeMillis() - lastAction < DELAY * 2) return;

                mc.player.closeHandledScreen();
                mc.player.sendMessage(Text.literal("§aSold all items from a chest!"), false);

                currentChest = null;
                itemsToSell = new ItemStack[0];
                state = State.SEARCH;
                nextChest();
            }
        }
    }

    // 🔁 Nächste Truhe abarbeiten
    private void nextChest() {
        if (!chestQueue.isEmpty()) {
            currentChest = chestQueue.poll();
            state = State.OPEN_CHEST;
            lastAction = System.currentTimeMillis();
        } else {
            currentChest = null;
            state = State.SEARCH;
        }
    }

    // 📡 Truhen im Umkreis finden
    private void findChests() {
        chestQueue.clear();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.getValue();

        for (int x = playerPos.getX() - r; x <= playerPos.getX() + r; x++) {
            for (int y = playerPos.getY() - 2; y <= playerPos.getY() + 2; y++) {
                for (int z = playerPos.getZ() - r; z <= playerPos.getZ() + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity be = mc.world.getBlockEntity(pos);
                    if (be instanceof ChestBlockEntity && !chestQueue.contains(pos)) {
                        chestQueue.add(pos);
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        chestQueue.clear();
        currentChest = null;
        itemsToSell = new ItemStack[0];
        state = State.SEARCH;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }
}
