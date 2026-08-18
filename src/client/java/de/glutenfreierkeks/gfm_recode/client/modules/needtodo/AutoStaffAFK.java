package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.*;

/**
 * AutoStaffAFK
 *
 * Sendet automatisch alle 20 Minuten zufällig eine Staff-Nachricht in den Chat,
 * um Aktivität zu simulieren (z. B. für AFK-Moderatoren).
 * Nachrichten behandeln Cheater-, Bug- und Regelmeldungen.
 */
public class AutoStaffAFK extends Module {

    private final long MESSAGE_INTERVAL = 20L * 60L * 1000L; // 20 Minuten in Millisekunden
    private long lastMessageTime = 0L;

    private final List<String> messages = new ArrayList<>(Arrays.asList(
            "Join the /discord to report cheaters!",
            "Found a cheater? Msg a staff to report them!",
            "Found a bug? Report it to staff or make a ticket!",
            "Report rule breakers via /msg staff or /discord.",
            "Found a bug or exploit? Please report it!"
    ));

    private final List<String> unusedMessages = new ArrayList<>(messages);
    private final Random random = new Random();

    public AutoStaffAFK() {
        super("AutoStaffAFK", "Sends periodic staff messages to simulate activity.", Category.MISC);
    }

    @Override
    public void onEnable() {
        lastMessageTime = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastMessageTime >= MESSAGE_INTERVAL) {
            if (unusedMessages.isEmpty()) {
                unusedMessages.addAll(messages); // Wenn alle benutzt, dann resetten
            }

            // Zufällige Nachricht auswählen
            String message = unusedMessages.remove(random.nextInt(unusedMessages.size()));

            // Nachricht in den Chat senden
            mc.player.networkHandler.sendChatMessage(message);

            lastMessageTime = currentTime;
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§cAutoStaffAFK disabled."), false);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }
}
