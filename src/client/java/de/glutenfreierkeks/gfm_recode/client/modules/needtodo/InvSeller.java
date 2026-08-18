package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public class InvSeller extends Module {

    private final IntSliderSetting cooldown = register(
            new IntSliderSetting("Cooldown", "Delay between sell cycles in ms", 2000, 0, 10000)
    );

    private long lastAction = 0L;
    private boolean selling = false;
    private boolean guiOpened = false;

    public InvSeller() {
        super("InvSeller", "Sells all inventory items automatically via /sell", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) mc.player.networkHandler.sendChatCommand("sell");

        guiOpened = true;
        selling = false;
        lastAction = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();

        // 1️⃣ - Wiederholungsintervall
        if (now - lastAction < cooldown.getValue()) return;

        // 2️⃣ - Öffnet das Shop GUI falls noch nicht offen
        if (!guiOpened) {
            mc.player.networkHandler.sendChatCommand("sell");
            guiOpened = true;
            selling = false;
            lastAction = now;
            return;
        }

        // 3️⃣ - Wenn GUI offen, beginne mit dem Verkaufen
        if (mc.currentScreen instanceof HandledScreen<?> screen) {
            var handler = screen.getScreenHandler();

            // Durch alle Slots des Spielerinventars
            int playerInvStart = handler.slots.size() - 36; // meist die letzten 36 Slots sind das Player Inv
            for (int i = playerInvStart; i < handler.slots.size(); i++) {
                try {
                    if (!handler.getSlot(i).getStack().isEmpty()) {
                        mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    }
                } catch (Exception ignored) {}
            }

            // GUI schließen
            mc.player.closeHandledScreen();
            guiOpened = false;
            selling = true;
            lastAction = now;
            mc.player.sendMessage(Text.literal("§aSold all inventory items!"), false);
        } else {
            // Kein GUI offen, erneut verkaufen
            mc.player.networkHandler.sendChatCommand("sell");
            guiOpened = true;
            selling = false;
            lastAction = now;
        }
    }

    @Override
    public void onDisable() {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        if (mc.player != null) mc.player.sendMessage(Text.literal("§7InvSeller disabled."), false);
        selling = false;
        guiOpened = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }
}
