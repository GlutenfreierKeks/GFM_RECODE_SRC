package de.glutenfreierkeks.gfm_recode.client.modules.combat;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.slot.Slot;
import org.joml.Matrix4f;

import java.util.concurrent.ThreadLocalRandom;

public class AutoTotem extends Module {

    public enum Mode {
        Legit,
        Blatant
    }

    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", "Legit opens inventory, Blatant is silent", Mode.Legit);
    private final DoubleSliderSetting delay = new DoubleSliderSetting("Delay", "Delay between open and equip (seconds)", 0.08, 0.0, 1.0);
    private final BoolSetting mainHand = new BoolSetting("Main Hand", "Hold totem in main hand during refill", false);

    private int delayTicks = 0;
    private int closeDelayTicks = 0;
    private int reactionJitter = 0;
    private boolean waitingForInventory = false;
    private boolean userClosedInventory = false;
    private long lastCloseTime = 0;
    private boolean wasInventoryOpen = false;
    private boolean suppressCloseDetection = false;
    private boolean autoOpenedInventory = false;
    private int originalSelectedSlot = -1;
    private int tempMainHandTotemSlot = -1;

    public AutoTotem() {
        super("AutoTotem", "Automatically equips a totem when offhand is empty", Category.PLAYER);
        register(mode);
        register(delay);
        register(mainHand);
        this.macroAllowed = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        boolean isInventoryOpen = mc.currentScreen instanceof InventoryScreen;

        if (wasInventoryOpen && !isInventoryOpen) {
            if (suppressCloseDetection) {
                suppressCloseDetection = false;
            } else {
                if (autoOpenedInventory) {
                    userClosedInventory = true;
                    lastCloseTime = System.currentTimeMillis();
                    restoreOriginalSlot();
                }
                autoOpenedInventory = false;
            }
        }

        wasInventoryOpen = isInventoryOpen;

        if (userClosedInventory && System.currentTimeMillis() - lastCloseTime > 2000) {
            userClosedInventory = false;
        }

        handleTemporaryMainHandSwitch();

        if (isSatisfied()) {
            if (isInventoryOpen && autoOpenedInventory) {
                resetState(true);
            } else {
                resetState(false);
            }
            return;
        }

        if (mode.getValue() == Mode.Blatant) {
            equipSilent();
            return;
        }

        equipLegit();
    }

    private void equipLegit() {
        if (mc.player == null || mc.interactionManager == null) {
            return;
        }

        boolean isInventoryOpen = mc.currentScreen instanceof InventoryScreen;
        if (userClosedInventory && !isInventoryOpen) {
            return;
        }

        if (hasTotemInOffhand()) {
            return;
        }

        if (!isInventoryOpen) {
            // Totem in Hotbar? → Slot wechseln bevor Inventar geöffnet wird
            int hotbarSlot = findTotemHotbarIndex();
            if (hotbarSlot != -1) {
                if (originalSelectedSlot == -1) {
                    originalSelectedSlot = mc.player.getInventory().getSelectedSlot();
                }
                mc.player.getInventory().setSelectedSlot(hotbarSlot);
                tempMainHandTotemSlot = hotbarSlot;
            }

            if (findTotemSlotIdForOffhand() == -1) {
                resetState(false);
                return;
            }

            prepareMainHandForInventory();
            openInventory();
            return;
        }

        if (waitingForInventory) {
            waitingForInventory = false;
            return;
        }

        // Reaction jitter: kurzes Zögern nach Inventar-Öffnen
        if (reactionJitter > 0) {
            reactionJitter--;
            return;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        // SWAP Packet (F-Taste) — ein einziges Packet, kein Cursor-Item
        int slot = findTotemHandledSlotIdForOffhand();
        if (slot == -1) {
            slot = findTotemSlotIdForOffhand();
        }
        if (slot != -1) {
            moveTotemToOffhand(slot);
        }

        if (mainHand.getValue() && mc.player.getMainHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
            int inventoryTotem = findTotemInMainInventorySlotId();
            if (inventoryTotem != -1) {
                int targetHotbarSlot = originalSelectedSlot != -1 ? originalSelectedSlot : mc.player.getInventory().getSelectedSlot();
                moveInventoryTotemToHotbar(inventoryTotem, targetHotbarSlot);
                if (tempMainHandTotemSlot == -1) {
                    tempMainHandTotemSlot = targetHotbarSlot;
                }
            }
        }
    }

    private void equipSilent() {
        if (mainHand.getValue()) {
            int hotbarSlot = findTotemHotbarIndex();
            if (hotbarSlot != -1) {
                mc.player.getInventory().setSelectedSlot(hotbarSlot);
            }
        }

        int slotId = findTotemSlotIdForOffhand();
        if (slotId == -1) {
            return;
        }
        // Blatant: auch SWAP Packet, direkt ohne Inventar
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotId, 40, SlotActionType.SWAP, mc.player
        );
    }

    // SWAP Packet: Button 40 = F-Taste → tauscht Slot mit Offhand, kein Cursor-Item nötig
    private boolean moveTotemToOffhand(int slotId) {
        if (mc.player == null || mc.interactionManager == null || mc.player.currentScreenHandler == null) {
            return false;
        }
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotId, 40, SlotActionType.SWAP, mc.player
        );
        return hasTotemInOffhand();
    }

    private void moveInventoryTotemToHotbar(int slotId, int hotbarIndex) {
        if (mc.player == null || mc.interactionManager == null || mc.player.currentScreenHandler == null) {
            return;
        }
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                slotId, hotbarIndex, SlotActionType.SWAP, mc.player
        );
    }

    private int findTotemSlotIdForOffhand() {
        if (mc.player == null) {
            return -1;
        }

        int selectedHotbarSlotId = 36 + mc.player.getInventory().getSelectedSlot();
        PlayerInventory inv = mc.player.getInventory();

        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            int slotId = 36 + i;
            if (mainHand.getValue() && slotId == selectedHotbarSlotId && countTotems() > 1) {
                continue;
            }
            if (inv.getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return slotId;
            }
        }

        return -1;
    }

    private int findTotemHandledSlotIdForOffhand() {
        if (mc.player == null || mc.player.currentScreenHandler == null) {
            return -1;
        }

        int selectedHotbarSlotId = 36 + mc.player.getInventory().getSelectedSlot();
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (slot == null || !slot.hasStack() || slot.id == 45) {
                continue;
            }
            if (slot.getStack().getItem() != Items.TOTEM_OF_UNDYING) {
                continue;
            }
            if (mainHand.getValue() && slot.id == selectedHotbarSlotId && countTotems() > 1) {
                continue;
            }
            return slot.id;
        }
        return -1;
    }

    private int findTotemHotbarIndex() {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    private int findTotemInMainInventorySlotId() {
        if (mc.player == null) {
            return -1;
        }
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                return i;
            }
        }
        return -1;
    }

    private int countTotems() {
        if (mc.player == null) {
            return 0;
        }

        int count = 0;
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                count += inv.getStack(i).getCount();
            }
        }

        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
            count += mc.player.getOffHandStack().getCount();
        }
        return count;
    }

    private boolean isSatisfied() {
        boolean hasOffhand = hasTotemInOffhand();
        if (!mainHand.getValue()) {
            return hasOffhand;
        }
        boolean hasMainHand = mc.player != null && mc.player.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING;
        return hasOffhand && (hasMainHand || countTotems() <= 1);
    }

    private void openInventory() {
        if (mc.player == null) {
            return;
        }
        waitingForInventory = true;

        // Bell-curve Delay: zwei Würfe gemittelt → natürlichere Verteilung
        int baseDelay = Math.max(0, (int) Math.round(delay.getValue() * 20.0));
        int r1 = ThreadLocalRandom.current().nextInt(0, 5);
        int r2 = ThreadLocalRandom.current().nextInt(0, 5);
        delayTicks = baseDelay + (r1 + r2) / 2;

        // Reaction jitter: 0–2 extra Ticks nach Inventar-Öffnen
        reactionJitter = ThreadLocalRandom.current().nextInt(0, 3);

        autoOpenedInventory = true;
        mc.setScreen(new InventoryScreen(mc.player));
    }

    private boolean hasTotemInOffhand() {
        return mc.player != null && mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING;
    }

    private void resetState(boolean closeInventory) {
        delayTicks = 0;
        waitingForInventory = false;

        if (closeInventory && mode.getValue() == Mode.Legit && mc.currentScreen instanceof InventoryScreen) {
            if (closeDelayTicks <= 0) {
                // Bell-curve Close Delay: 2–12 Ticks (~0.1–0.6s)
                int r1 = ThreadLocalRandom.current().nextInt(1, 3);
                int r2 = ThreadLocalRandom.current().nextInt(1, 2);
                closeDelayTicks = r1 + r2;
                return;
            }
            closeDelayTicks--;
            if (closeDelayTicks > 0) {
                return;
            }
            suppressCloseDetection = true;
            mc.setScreen(null);
            userClosedInventory = false;
        }

        restoreOriginalSlot();
        tempMainHandTotemSlot = -1;

        if (!closeInventory || !(mc.currentScreen instanceof InventoryScreen)) {
            autoOpenedInventory = false;
        }
    }

    private void prepareMainHandForInventory() {
        if (!mainHand.getValue() || mc.player == null) {
            return;
        }

        if (originalSelectedSlot == -1) {
            originalSelectedSlot = mc.player.getInventory().getSelectedSlot();
        }

        int hotbarSlot = findTotemHotbarIndex();
        if (hotbarSlot != -1) {
            mc.player.getInventory().setSelectedSlot(hotbarSlot);
            tempMainHandTotemSlot = hotbarSlot;
        }
    }

    private void restoreOriginalSlot() {
        if (mc.player != null && originalSelectedSlot != -1) {
            if (tempMainHandTotemSlot == -1 || mc.player.getInventory().getSelectedSlot() == tempMainHandTotemSlot) {
                mc.player.getInventory().setSelectedSlot(originalSelectedSlot);
            }
        }
        originalSelectedSlot = -1;
    }

    private void handleTemporaryMainHandSwitch() {
        if (!mainHand.getValue() || mc.player == null) {
            return;
        }

        if (!hasTotemInOffhand()) {
            if (tempMainHandTotemSlot == -1) {
                int hotbarSlot = findTotemHotbarIndex();
                if (hotbarSlot != -1) {
                    if (originalSelectedSlot == -1) {
                        originalSelectedSlot = mc.player.getInventory().getSelectedSlot();
                    }
                    mc.player.getInventory().setSelectedSlot(hotbarSlot);
                    tempMainHandTotemSlot = hotbarSlot;
                }
            }
            return;
        }

        if (originalSelectedSlot != -1) {
            restoreOriginalSlot();
        }
        tempMainHandTotemSlot = -1;
    }

    @Override
    public void onEnable() {
        closeDelayTicks = 0;
        reactionJitter = 0;
        resetState(false);
    }

    @Override
    public void onDisable() {
        closeDelayTicks = 0;
        reactionJitter = 0;
        resetState(true);
    }
}