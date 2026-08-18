package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Formatting;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public class AutoOrder extends Module {

    private final BoolSetting orderPerson = register(new BoolSetting("OrderPerson", "Only order if it belongs to a person", true));
    private final StringSetting orderPlayerName = register(new StringSetting("OrderPlayerName", "Name of the player to order from", ""));
    private final BoolSetting orderBest = register(new BoolSetting("OrderBest", "Order the best quality", false));
    private final BoolSetting autoDrop = register(new BoolSetting("AutoDrop", "Automatically drop other items", false));
    private final IntSliderSetting guiActionDelay = register(new IntSliderSetting("Delay", "Delay between GUI actions", 500, 50, 2000));
    private final IntSliderSetting waitDelay = register(new IntSliderSetting("WaitDelay", "Delay between orders", 1000, 100, 5000));

    private Item targetItem = null;
    private String targetPlayer = null;
    private long lastActionTime = 0L;
    private int foundSlot = -1;
    private int bestClickCount = 0;

    private enum State {
        SEND_ORDER,
        WAIT_FOR_GUI,
        SEARCH_ITEM,
        CLICK_ITEM,
        FILL_GUI,
        PROCESS_SLOT_16,
        CLEANUP_DROPS,
        COOLDOWN
    }

    private State state = State.SEND_ORDER;

    public AutoOrder() {
        super("AutoOrder", "Automatisches Bestellen (Full Page Support).", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        ItemStack heldItem = mc.player.getMainHandStack();
        if (heldItem.isEmpty()) {
            msg("§cFehler: Bitte Item halten!");
            setEnabled(false);
            return;
        }
        targetItem = heldItem.getItem();
        targetPlayer = orderPlayerName.getValue().isEmpty() ? mc.player.getName().getString() : orderPlayerName.getValue();
        state = State.SEND_ORDER;
        bestClickCount = 0;
        msg("§aAutoOrder aktiv! Suche: " + targetPlayer);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    private void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }

    @Override
    public void onTick() {
        if (mc.player == null || targetItem == null) return;
        if (System.currentTimeMillis() - lastActionTime < guiActionDelay.getValue()) return;

        switch (state) {
            case SEND_ORDER -> {
                mc.player.networkHandler.sendChatCommand("order");
                state = State.WAIT_FOR_GUI;
                lastActionTime = System.currentTimeMillis();
            }

            case WAIT_FOR_GUI -> {
                if (mc.currentScreen instanceof HandledScreen<?>) {
                    state = State.SEARCH_ITEM;
                }
            }

            case SEARCH_ITEM -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                var handler = screen.getScreenHandler();

                // OrderBest: Erst 3x Slot 47 klicken (nur am Anfang der Suche)
                if (orderBest.getValue() && bestClickCount < 3) {
                    mc.interactionManager.clickSlot(handler.syncId, 47, 0, SlotActionType.PICKUP, mc.player);
                    bestClickCount++;
                    lastActionTime = System.currentTimeMillis();
                    return;
                }

                foundSlot = -1;
                String matchSuffix = targetPlayer + "'s Order";

                // Scan das aktuelle GUI
                for (int i = 0; i < 54; i++) {
                    if (i >= handler.slots.size()) break;
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (stack.isEmpty()) continue;

                    String displayName = Formatting.strip(stack.getName().getString());

                    if (orderPerson.getValue()) {
                        // Check Item + Name (z.B. "Name's Order")
                        if (stack.getItem() == targetItem && displayName != null && displayName.contains(matchSuffix)) {
                            foundSlot = i;
                            break;
                        }
                    } else if (orderBest.getValue()) {
                        // Check nur Item
                        if (stack.getItem() == targetItem) {
                            foundSlot = i;
                            break;
                        }
                    }
                }

                if (foundSlot != -1) {
                    state = State.CLICK_ITEM;
                } else {
                    // NICHT GEFUNDEN -> Klicke auf Slot 53 für die nächste Seite
                    // Das passiert jetzt sowohl bei orderPerson als auch bei orderBest
                    if (handler.slots.size() > 53) {
                        mc.interactionManager.clickSlot(handler.syncId, 53, 0, SlotActionType.PICKUP, mc.player);
                        msg("§7Item nicht gefunden, wechsle Seite...");
                        lastActionTime = System.currentTimeMillis();
                    }
                }
            }

            case CLICK_ITEM -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, foundSlot, 0, SlotActionType.PICKUP, mc.player);
                state = State.FILL_GUI;
                lastActionTime = System.currentTimeMillis();
            }

            case FILL_GUI -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                var handler = screen.getScreenHandler();
                for (int i = 27; i < handler.slots.size(); i++) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (stack.isEmpty()) continue;

                    if (stack.getItem() == targetItem) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    } else if (autoDrop.getValue()) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                    }
                }
                mc.player.closeHandledScreen();
                lastActionTime = System.currentTimeMillis() + waitDelay.getValue();
                state = State.PROCESS_SLOT_16;
            }

            case PROCESS_SLOT_16 -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
                    mc.player.networkHandler.sendChatCommand("order");
                    lastActionTime = System.currentTimeMillis();
                    return;
                }
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 16, 0, SlotActionType.PICKUP, mc.player);
                state = State.CLEANUP_DROPS;
                lastActionTime = System.currentTimeMillis();
            }

            case CLEANUP_DROPS -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;
                var handler = screen.getScreenHandler();
                for (int i = 0; i < 27; i++) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (!stack.isEmpty() && stack.getItem() == targetItem) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                    }
                }
                mc.player.closeHandledScreen();
                state = State.COOLDOWN;
                lastActionTime = System.currentTimeMillis();
            }

            case COOLDOWN -> {
                bestClickCount = 0;
                state = State.SEND_ORDER;
                lastActionTime = System.currentTimeMillis();
            }
        }
    }
}
