package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import com.mojang.authlib.GameProfile;
import org.joml.Matrix4f;
import net.minecraft.client.render.Camera;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatFilter extends Module {

    private final Map<String, Long> reportedMessages = new ConcurrentHashMap<>();

    private List<String> currentBadWords = Arrays.asList(
            "nigger", "retard", "kys", "neger", "nigga", "niga", "nga"
    );

    private List<Pattern> badWordPatterns = Collections.emptyList();

    public ChatFilter() {
        super("ChatFilter", "Monitors chat for violations and reports them", Category.MISC);
        this.setEnabled(true); // Auto-enable on startup
    }

    @Override
    public void onEnable() {
        try {
            updateBadWordPatterns();
        } catch (RuntimeException e) {
            System.err.println("ChatFilter: Failed to compile bad word patterns. Patterns will be empty. Error: " + e.getMessage());
            badWordPatterns = Collections.emptyList();
        }

    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    private void updateBadWordPatterns() {
        badWordPatterns = currentBadWords.stream()
                .map(word -> Pattern.compile(
                        word.replaceAll("([aeiou])", "[$1\\\\*\\\\_\\\\!]") + "|(" + word + ")",
                        Pattern.CASE_INSENSITIVE
                ))
                .collect(Collectors.toList());
    }

    private String hashMessage(String message, String sender, String server) {
        return String.valueOf((message + sender + server).hashCode());
    }

    private String getServerAddress() {
        if (mc.getNetworkHandler() == null) return "Not Connected";
        ServerInfo info = mc.getNetworkHandler().getServerInfo();
        if (info != null) return info.address;
        if (mc.isIntegratedServerRunning()) return "SinglePlayer";
        return "Unknown Server";
    }

    private String extractPlayerNameFromMessage(Text message) {
        String msg = message.getString().trim();
        if (msg.startsWith("<") && msg.contains(">")) {
            return msg.substring(1, msg.indexOf('>'));
        }
        return "Server/Unknown";
    }

    @Override
    public void onDisable() {
        // Lässt diese Methode leer, um zu verhindern, dass das Modul deaktiviert wird,
        // wenn disable() oder toggle() aufgerufen wird, da es IMMER aktiv sein soll.
    }
}
