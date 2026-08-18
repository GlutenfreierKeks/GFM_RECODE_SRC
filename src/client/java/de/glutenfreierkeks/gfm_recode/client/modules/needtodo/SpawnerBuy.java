package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;
import net.minecraft.client.render.Camera;

public class SpawnerBuy extends Module {

    public enum ServerMode { SLOWFIZ, EUROPE }

    public enum SpawnerType {
        COW, ALLAY, BLAZE, IRON_GOLEM, CREEPER, WITCH, ENDERMAN, ZOMBIE, SKELETON, ZOMBIE_PIG, GHAST, SLIME
    }

    private final EnumSetting<ServerMode> serverMode = register(new EnumSetting<>("Mode", "Server to buy spawners on", ServerMode.SLOWFIZ));
    private final EnumSetting<SpawnerType> spawnerType = register(new EnumSetting<>("SpawnerType", "Type of spawner to buy", SpawnerType.COW));
    private final BoolSetting autoStock = register(new BoolSetting("AutoStock", "Automatically reopen shop", true));

    private final long SLOWFIZ_GUI_DELAY = 250;
    private final long SLOWFIZ_ACTION_DELAY = 100;
    private final long EUROPE_GUI_DELAY = 300;
    private final long EUROPE_ACTION_DELAY = 100;
    private final long REOPEN_DELAY = 500;

    private long lastClick = 0L;
    private long lastShopOpen = 0L;
    private boolean shopOpened = false;
    private boolean clickedShopSlot = false;
    private boolean clickedSpawner = false;
    private boolean clickedConfirm = false;
    private boolean buying = false;
    private boolean waitingForReopen = false;

    public SpawnerBuy() {
        super("SpawnerBuy", "Ultra fast spawner buying + placing", Category.MISC);
    }

    @Override
    public void onEnable() {
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
        lastClick = System.currentTimeMillis();
        lastShopOpen = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (serverMode.getValue() == ServerMode.SLOWFIZ) {
            runSlowfizLogic();
        } else {
            runEuropeLogic();
        }
    }

    private void runSlowfizLogic() {
        handleAutoStock();
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        var handler = screen.getScreenHandler();
        int syncId = handler.syncId;

        if (shopOpened && !clickedShopSlot && System.currentTimeMillis() - lastClick > SLOWFIZ_GUI_DELAY) {
            click(syncId, 15);
            clickedShopSlot = true;
        }

        if (clickedShopSlot && !clickedSpawner && System.currentTimeMillis() - lastClick > SLOWFIZ_GUI_DELAY) {
            int slot = getSpawnerSlot(spawnerType.getValue(), ServerMode.SLOWFIZ);
            if (slot != -1) {
                click(syncId, slot);
                clickedSpawner = true;
            }
        }

        if (clickedSpawner && !clickedConfirm && System.currentTimeMillis() - lastClick > SLOWFIZ_GUI_DELAY) {
            click(syncId, 17);
            clickedConfirm = true;
            buying = true;
        }

        if (buying && System.currentTimeMillis() - lastClick > SLOWFIZ_ACTION_DELAY) {
            try {
                click(syncId, 23);
                
                // Zuerst Inventar schließen (für den Server legit), dann platzieren
                if (mc.player != null) {
                    mc.player.closeHandledScreen();
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); // PLATZIEREN
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } catch (Exception ignored) {}
            lastClick = System.currentTimeMillis();
        }
        checkShopClosed();
    }

    private void runEuropeLogic() {
        handleAutoStock();
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
        var handler = screen.getScreenHandler();
        int syncId = handler.syncId;

        if (shopOpened && !clickedShopSlot && System.currentTimeMillis() - lastClick > EUROPE_GUI_DELAY) {
            click(syncId, 12);
            clickedShopSlot = true;
        }

        if (clickedShopSlot && !clickedSpawner && System.currentTimeMillis() - lastClick > EUROPE_GUI_DELAY) {
            int slot = getSpawnerSlot(spawnerType.getValue(), ServerMode.EUROPE);
            if (slot != -1) {
                click(syncId, slot);
                clickedSpawner = true;
            }
        }

        if (clickedSpawner && !clickedConfirm && System.currentTimeMillis() - lastClick > EUROPE_GUI_DELAY) {
            click(syncId, 17);
            clickedConfirm = true;
            buying = true;
        }

        if (buying && System.currentTimeMillis() - lastClick > EUROPE_ACTION_DELAY) {
            try {
                click(syncId, 32);
                
                // Zuerst Inventar schließen (für den Server legit), dann platzieren
                if (mc.player != null) {
                    mc.player.closeHandledScreen();
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND); // PLATZIEREN
                    mc.player.swingHand(Hand.MAIN_HAND);
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

    private int getSpawnerSlot(SpawnerType type, ServerMode mode) {
        if (mode == ServerMode.SLOWFIZ) {
            return switch (type) {
                case COW -> 10;
                case CREEPER -> 11;
                case IRON_GOLEM -> 12;
                case BLAZE -> 14;
                case ENDERMAN -> 15;
                case SLIME -> 16;
                default -> -1;
            };
        } else {
            return switch (type) {
                case ZOMBIE -> 9;
                case SKELETON -> 10;
                case BLAZE -> 11;
                case ENDERMAN -> 12;
                case CREEPER -> 13;
                case ZOMBIE_PIG -> 14;
                case IRON_GOLEM -> 15;
                case GHAST -> 16;
                default -> -1;
            };
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
        resetStates();
    }

    private void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    @Override
    public String getDisplayInfo() {
        return serverMode.getValue().name() + (buying ? " | Buying" : "");
    }
}
