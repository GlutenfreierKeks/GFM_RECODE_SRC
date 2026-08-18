package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;

import java.util.List;

public class EuropeMCSpawnerBuy extends Module {
    
    public enum Mode {
        LEGIT, UNLEGIT
    }

    private static final String TARGET_IP = "europemc.eu";
    private static final String MODE = "EUROPE";

    private final EnumSetting<InventorySlotSetting.Layout> layout = register(new EnumSetting<>("Layout", "Inventory layout to use", InventorySlotSetting.Layout.CHEST_9x6));
    private final EnumSetting<Mode> mode = register(new EnumSetting<>("Mode", "Buying mode", Mode.LEGIT));
    private final InventorySlotSetting shopSlots = register(new InventorySlotSetting("ShopSlots", "Select shop categories/items to buy", InventorySlotSetting.Layout.CHEST_9x6, true));
    private final InventorySlotSetting spawnerSlots = register(new InventorySlotSetting("SpawnerSlots", "Select spawners to buy (if spawner category selected)", InventorySlotSetting.Layout.CHEST_9x3, true));

    private final BoolSetting autoStock = register(new BoolSetting("AutoStock", "Automatically reopen shop when finished", true));

    private final long GUI_DELAY = 150;
    private final long GUI_DELAY_LEGIT = 250;
    private final long ACTION_DELAY = 50;
    private final long ACTION_DELAY_LEGIT = 80;
    private final long REOPEN_DELAY = 500;
    private final long PLACE_DELAY = 200;

    private long lastClick = 0L;
    private long lastShopOpen = 0L;
    private long lastCloseTime = 0L;
    private boolean shopOpened = false;
    private boolean clickedShopSlot = false;
    private boolean clickedSpawner = false;
    private boolean clickedConfirm = false;
    private boolean buying = false;
    private boolean waitingForReopen = false;
    private boolean waitingToPlace = false;
    
    private int currentShopIndex = 0;
    private int currentSpawnerIndex = 0;
    private int lastCatSlot = 0;
    private int legitClicks = 0;

    public EuropeMCSpawnerBuy() {
        super("EuropeMCSpawnerBuy", "Ultra fast spawner/shop buying for EuropeMC.", Category.MISC);
        
        shopSlots.addSuggestion(24, new ItemStack(Items.SPAWNER), "Spawners");
        shopSlots.addSuggestion(23, new ItemStack(Items.GRASS_BLOCK), "Dirt/Grass block");
        shopSlots.addSuggestion(21, new ItemStack(Items.TOTEM_OF_UNDYING), "Totem");
        shopSlots.addSuggestion(20, new ItemStack(Items.END_CRYSTAL), "Endcrystal");
        shopSlots.addSuggestion(29, new ItemStack(Items.SUGAR_CANE), "Sugarcane");
        shopSlots.addSuggestion(30, new ItemStack(Items.DIAMOND), "Diamond");
        shopSlots.addSuggestion(32, new ItemStack(Items.SPLASH_POTION), "Splashpotion");
        shopSlots.addSuggestion(33, new ItemStack(Items.ROTTEN_FLESH), "Rotten flesh");

        spawnerSlots.addSuggestion(11, new ItemStack(Items.ZOMBIE_SPAWN_EGG), "Zombie Spawner");
        spawnerSlots.addSuggestion(12, new ItemStack(Items.SKELETON_SPAWN_EGG), "Skeleton Spawner");
        spawnerSlots.addSuggestion(13, new ItemStack(Items.BLAZE_SPAWN_EGG), "Blaze Spawner");
        spawnerSlots.addSuggestion(14, new ItemStack(Items.ENDERMAN_SPAWN_EGG), "Enderman Spawner");
        spawnerSlots.addSuggestion(15, new ItemStack(Items.CREEPER_SPAWN_EGG), "Creeper Spawner");
    }

    private boolean isCorrectServer() {
        if (mc.getCurrentServerEntry() == null) return false;
        String address = mc.getCurrentServerEntry().address;
        return address.toLowerCase().endsWith(TARGET_IP);
    }

    @Override
    public void onEnable() {
        if (!isCorrectServer()) {
            msg("§cFehler: Dieses Modul does not work on §4" + TARGET_IP + "!");
            this.setEnabled(false);
            return;
        }
        currentShopIndex = 0;
        currentSpawnerIndex = 0;
        openShop();
    }

    private void openShop() {
        if (mc.player != null) mc.player.networkHandler.sendChatCommand("shop");
        resetStates();
    }

    private void resetStates() {
        shopOpened = true;
        clickedShopSlot = false;
        clickedSpawner = false;
        clickedConfirm = false;
        buying = false;
        waitingForReopen = false;
        waitingToPlace = false;
        legitClicks = 0;
        lastClick = System.currentTimeMillis();
        lastShopOpen = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || !isCorrectServer()) return;

        handleAutoStock(REOPEN_DELAY);

        if (mode.getValue() == Mode.UNLEGIT && mc.player != null) {
            mc.options.sneakKey.setPressed(true);
        }

        if (buying && waitingToPlace) {
            if (System.currentTimeMillis() - lastCloseTime > PLACE_DELAY) {
                if (mc.player != null && mc.player.isSneaking()) {
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    finishBuy(lastCatSlot);
                }
            }
            return;
        }

        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        var handler = screen.getScreenHandler();
        int syncId = handler.syncId;

        List<Integer> selectedShop = shopSlots.getValue();
        if (selectedShop.isEmpty()) {
            msg("§cFehler: Keine Shop-Slots ausgewählt!");
            this.setEnabled(false);
            return;
        }

        if (currentShopIndex >= selectedShop.size()) {
            if (autoStock.getValue()) {
                currentShopIndex = 0;
                openShop();
            } else {
                this.setEnabled(false);
            }
            return;
        }

        int catSlot = selectedShop.get(currentShopIndex);
        lastCatSlot = catSlot;

        long currentGuiDelay = mode.getValue() == Mode.LEGIT ? GUI_DELAY_LEGIT : GUI_DELAY;

        if (shopOpened && !clickedShopSlot && System.currentTimeMillis() - lastClick > currentGuiDelay) {
            click(syncId, catSlot);
            clickedShopSlot = true;
            return;
        }

        if (clickedShopSlot && !clickedSpawner && System.currentTimeMillis() - lastClick > currentGuiDelay) {
            if (catSlot == 24) {
                List<Integer> selectedSpawners = spawnerSlots.getValue();
                if (selectedSpawners.isEmpty()) {
                    msg("§cFehler: Keine Spawner ausgewählt!");
                    currentShopIndex++;
                    clickedShopSlot = false;
                    return;
                }
                if (currentSpawnerIndex < selectedSpawners.size()) {
                    click(syncId, selectedSpawners.get(currentSpawnerIndex));
                    clickedSpawner = true;
                } else {
                    currentShopIndex++;
                    currentSpawnerIndex = 0;
                    clickedShopSlot = false;
                }
            } else {
                click(syncId, 13);
                clickedSpawner = true;
            }
            return;
        }

        if (clickedSpawner && !clickedConfirm && System.currentTimeMillis() - lastClick > currentGuiDelay) {
            click(syncId, 31);
            clickedConfirm = true;
            buying = true;
            return;
        }

        if (buying) {
            long currentActionDelay = mode.getValue() == Mode.LEGIT ? ACTION_DELAY_LEGIT : ACTION_DELAY;
            if (mode.getValue() == Mode.LEGIT) {
                if (!waitingToPlace && System.currentTimeMillis() - lastClick > currentActionDelay) {
                    try {
                        if (legitClicks < 4) {
                            click(syncId, 8);
                            legitClicks++;
                        } else {
                            if (mc.player != null) {
                                mc.player.closeHandledScreen();
                                waitingToPlace = true;
                                lastCloseTime = System.currentTimeMillis();
                            }
                        }
                    } catch (Exception ignored) {}
                }
            } else {
                if (System.currentTimeMillis() - lastClick > currentActionDelay) {
                    try {
                        click(syncId, 8);
                        if (mc.player != null) {
                            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                            mc.player.swingHand(Hand.MAIN_HAND);
                        }
                        lastClick = System.currentTimeMillis();
                    } catch (Exception ignored) {}
                }
            }
            return;
        }

        checkShopClosed();
    }

    private void finishBuy(int catSlot) {
        if (catSlot == 24) {
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
        waitingToPlace = false;
        legitClicks = 0;
        lastClick = System.currentTimeMillis();
    }

    private void handleAutoStock(long reopenDelay) {
        if (mc.currentScreen == null && !waitingToPlace && autoStock.getValue()) {
            if (!waitingForReopen) {
                if (buying) {
                    msg("§e[AutoStock] Shop closed unexpectedly, reopening...");
                }
                waitingForReopen = true;
                lastShopOpen = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - lastShopOpen > reopenDelay) {
                openShop();
            }
        }
    }

    private void checkShopClosed() {
        if (mc.currentScreen == null && (clickedSpawner || buying) && !autoStock.getValue()) {
            msg("§cShop closed. Toggle module to restart.");
            this.setEnabled(false);
        }
    }

    private void click(int syncId, int slot) {
        mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        lastClick = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            if (mode.getValue() == Mode.UNLEGIT) {
                mc.options.sneakKey.setPressed(false);
            }
            if (mode.getValue() == Mode.LEGIT && mc.currentScreen != null) {
                mc.player.closeHandledScreen();
            }

            if (autoStock.getValue() && buying) {
                msg("§7SpawnerBuy (" + MODE + ") disabled. Session ended.");
            } else {
                msg("§7SpawnerBuy (" + MODE + ") disabled.");
            }
        }
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
        String status = "";
        if (buying) {
            status = " | Buying";
        } else if (waitingForReopen) {
            status = " | Reopen";
        }
        return MODE + status;
    }
}
