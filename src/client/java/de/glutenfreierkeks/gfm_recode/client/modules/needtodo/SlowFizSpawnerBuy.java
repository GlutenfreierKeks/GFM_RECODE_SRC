package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.InventorySlotSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;

import java.util.List;

public class SlowFizSpawnerBuy extends Module {

    private static final String TARGET_IP = "slowfiz.net";

    private final InventorySlotSetting shopSlots = register(new InventorySlotSetting("ShopSlots", "Select shop categories/items to buy", InventorySlotSetting.Layout.CHEST_9x3, false));
    private final InventorySlotSetting spawnerSlots = register(new InventorySlotSetting("SpawnerSlots", "Select spawners to buy", InventorySlotSetting.Layout.CHEST_9x3, true));
    private final IntSliderSetting totalAmount = register(new IntSliderSetting("Amount", "Total amount to buy", 1, 1, 64));
    private final BoolSetting autoStock = register(new BoolSetting("AutoStock", "Automatically reopen shop", true));

    private final long GUI_DELAY = 250;
    private final long ACTION_DELAY = 100;
    private final long REOPEN_DELAY = 500;

    private long lastClick = 0L;
    private long lastShopOpen = 0L;
    private boolean shopOpened = false;
    private boolean clickedShopSlot = false;
    private boolean clickedSpawner = false;
    private boolean clickedConfirm = false;
    private boolean buying = false;
    private boolean waitingForReopen = false;

    private int currentShopIndex = 0;
    private int currentSpawnerIndex = 0;
    private int remainingAmount = 0;

    public SlowFizSpawnerBuy() {
        super("SlowFizSpawnerBuy", "Fast spawner buying + placing for slowfiz.net", Category.MISC);

        shopSlots.addSuggestion(15, new ItemStack(Items.SPAWNER), "Spawners");

        spawnerSlots.addSuggestion(10, new ItemStack(Items.CREEPER_SPAWN_EGG), "Creeper Spawner");
        spawnerSlots.addSuggestion(11, new ItemStack(Items.IRON_GOLEM_SPAWN_EGG), "Iron Golem Spawner");
        spawnerSlots.addSuggestion(12, new ItemStack(Items.BLAZE_SPAWN_EGG), "Blaze Spawner");
        spawnerSlots.addSuggestion(14, new ItemStack(Items.ENDERMAN_SPAWN_EGG), "Enderman Spawner");
        spawnerSlots.addSuggestion(15, new ItemStack(Items.SLIME_SPAWN_EGG), "Slime Spawner");
        spawnerSlots.addSuggestion(16, new ItemStack(Items.PIGLIN_SPAWN_EGG), "Piglin Spawner");
    }

    private boolean isCorrectServer() {
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) return false;
        String address = mc.getNetworkHandler().getServerInfo().address;
        return address.toLowerCase().endsWith(TARGET_IP);
    }

    @Override
    public void onEnable() {
        if (!isCorrectServer()) {
            msg("§cError: This module only works on §4" + TARGET_IP + "§c!");
            this.disable();
            return;
        }
        openShop();
    }

    private void openShop() {
        if (mc.player != null) {
            mc.player.networkHandler.sendChatCommand("shop");
            resetStates();
        }
    }

    private void resetStates() {
        shopOpened = true;
        clickedShopSlot = false;
        clickedSpawner = false;
        clickedConfirm = false;
        buying = false;
        waitingForReopen = false;
        currentShopIndex = 0;
        currentSpawnerIndex = 0;
        remainingAmount = totalAmount.getValue();
        lastClick = System.currentTimeMillis();
        lastShopOpen = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || !isCorrectServer()) return;

        handleAutoStock();

        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        var handler = screen.getScreenHandler();
        int syncId = handler.syncId;

        List<Integer> selectedShop = shopSlots.getValue();
        if (selectedShop.isEmpty()) {
            msg("§cError: No shop slots selected!");
            this.disable();
            return;
        }

        if (currentShopIndex >= selectedShop.size()) {
            if (autoStock.getValue()) {
                currentShopIndex = 0;
                openShop();
            } else {
                this.disable();
            }
            return;
        }

        int catSlot = selectedShop.get(currentShopIndex);

        if (shopOpened && !clickedShopSlot && System.currentTimeMillis() - lastClick > GUI_DELAY) {
            click(syncId, catSlot);
            clickedShopSlot = true;
            return;
        }

        if (clickedShopSlot && !clickedSpawner && System.currentTimeMillis() - lastClick > GUI_DELAY) {
            if (catSlot == 15) { // Spawners category
                List<Integer> selectedSpawners = spawnerSlots.getValue();
                if (selectedSpawners.isEmpty()) {
                    msg("§cError: No spawners selected!");
                    currentShopIndex++;
                    clickedShopSlot = false;
                    return;
                }
                if (currentSpawnerIndex < selectedSpawners.size()) {
                    click(syncId, selectedSpawners.get(currentSpawnerIndex));
                    clickedSpawner = true;
                    if (remainingAmount <= 0) remainingAmount = totalAmount.getValue();
                } else {
                    currentShopIndex++;
                    currentSpawnerIndex = 0;
                    clickedShopSlot = false;
                }
            } else {
                click(syncId, 13); // Default item slot
                clickedSpawner = true;
                if (remainingAmount <= 0) remainingAmount = totalAmount.getValue();
            }
            return;
        }

        if (clickedSpawner && !clickedConfirm && System.currentTimeMillis() - lastClick > GUI_DELAY) {
            click(syncId, 17);
            clickedConfirm = true;
            buying = true;
            return;
        }

        if (buying && System.currentTimeMillis() - lastClick > ACTION_DELAY) {
            try {
                click(syncId, 23);
                
                remainingAmount--;

                if (remainingAmount <= 0) {
                    if (mc.player != null) {
                        mc.player.closeHandledScreen();
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }

                    if (catSlot == 15) {
                        currentSpawnerIndex++;
                        List<Integer> selectedSpawners = spawnerSlots.getValue();
                        if (currentSpawnerIndex >= selectedSpawners.size()) {
                            currentShopIndex++;
                            currentSpawnerIndex = 0;
                        }
                    } else {
                        currentShopIndex++;
                    }

                    clickedShopSlot = false;
                    clickedSpawner = false;
                    clickedConfirm = false;
                    buying = false;
                }
            } catch (Exception ignored) {}

            lastClick = System.currentTimeMillis();
        }

        checkShopClosed();
    }

    private void handleAutoStock() {
        if (mc.currentScreen == null && buying && autoStock.getValue()) {
            if (!waitingForReopen) {
                msg("§e[AutoStock] Shop closed, reopening...");
                waitingForReopen = true;
                lastShopOpen = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - lastShopOpen > REOPEN_DELAY) {
                openShop();
            }
        }
    }

    private void checkShopClosed() {
        if (mc.currentScreen == null && (clickedSpawner || buying) && !autoStock.getValue()) {
            msg("§cShop closed. Toggle module to restart.");
            this.disable();
        }
    }

    private void click(int syncId, int slot) {
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
            lastClick = System.currentTimeMillis();
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
        resetStates();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    private void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }

    @Override
    public String getDisplayInfo() {
        return (buying ? "Buying" : "Idle");
    }
}
