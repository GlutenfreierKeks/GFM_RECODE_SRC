package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.InventorySlotSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.Collection;
import java.util.List;

public class AutoTpahere extends Module {

    private final StringSetting playerName = register(new StringSetting("TargetPlayer", "The player to send tpahere to", "PlayerName"));
    private final InventorySlotSetting acceptSlot = register(new InventorySlotSetting("AcceptSlot", "Select the slot to click in the GUI to accept", InventorySlotSetting.Layout.CHEST_9x3, false));

    private long lastAction = 0L;
    private boolean spamMode = false;
    private boolean hasClicked = false; // Verhindert Mehrfachklicks im selben Fenster

    private final long NORMAL_DELAY = 1000; // 1 Sekunde

    public AutoTpahere() {
        super("AutoTpahere", "Automatically sends /tpahere and clicks a slot in GUIs", Category.PLAYER);
        acceptSlot.addSuggestion(16, new ItemStack(Items.LIME_STAINED_GLASS_PANE), "Accept Button");
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        // --- GUI CHECK & CLICK LOGIC ---
        // Prüfen, ob gerade ein Container-GUI (Inventar) offen ist
        if (mc.currentScreen instanceof GenericContainerScreen container) {
            if (!hasClicked) {
                int syncId = container.getScreenHandler().syncId;

                List<Integer> selected = acceptSlot.getValue();
                if (!selected.isEmpty()) {
                    mc.interactionManager.clickSlot(syncId, selected.get(0), 0, SlotActionType.PICKUP, mc.player);
                    hasClicked = true;
                }
            }
        } else {
            // Wenn kein Screen offen ist, Reset für das nächste Mal
            hasClicked = false;
        }

        // --- TPA LOGIC ---
        String target = playerName.getValue();
        boolean targetOnline = isPlayerOnline(target);
        if (!targetOnline) return;

        long now = System.currentTimeMillis();

        // Spam-Phase → 5x sehr schnell senden
        if (spamMode) {
            for (int i = 0; i < 5; i++) {
                mc.getNetworkHandler().sendChatCommand("tpahere " + target);
            }
            spamMode = false;
            lastAction = now;
            return;
        }

        // Normaler 1s-Cooldown
        if (now - lastAction > NORMAL_DELAY) {
            mc.getNetworkHandler().sendChatCommand("tpahere " + target);
            lastAction = now;
        }
    }

    /**
     * Chat-Event – wird aufgerufen, wenn eine Chatnachricht empfangen wird.
     * Stelle sicher, dass diese Methode von deinem Event-System aufgerufen wird!
     */
    public void onChatReceived(Text message) {
        String raw = message.getString().toLowerCase();

        // Wenn im Chat "a tphere request" steht, aktiviere Spammodus
        if (raw.contains("a tphere request")) {
            spamMode = true;
        }
    }

    private boolean isPlayerOnline(String name) {
        Collection<PlayerListEntry> players = mc.getNetworkHandler().getPlayerList();
        for (PlayerListEntry entry : players) {
            if (entry.getProfile().name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDisable() {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§7AutoTpahere disabled."), false);
        spamMode = false;
        hasClicked = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }
}
