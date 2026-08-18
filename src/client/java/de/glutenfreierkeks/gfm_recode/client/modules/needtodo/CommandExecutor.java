package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

public class CommandExecutor extends Module {

    private final StringSetting command = register(new StringSetting("Command", "Command to execute", "/home"));
    private final DoubleSliderSetting cooldown = register(new DoubleSliderSetting("CooldownSeconds", "Seconds between executions", 1.0, 0.1, 10.0));

    private long lastExecution = 0L;

    public CommandExecutor() {
        super("CommandExecutor", "Executes a custom command repeatedly", Category.MISC);
    }

    @Override
    public void onEnable() {
        lastExecution = 0L;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        long now = System.currentTimeMillis();
        long delay = (long) (cooldown.getValue() * 1000L);

        if (now - lastExecution >= delay) {
            String cmd = command.getValue();

            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1); // "/" entfernen, da sendCommand kein Slash braucht
            }

            mc.player.networkHandler.sendChatCommand(cmd);
            lastExecution = now;
        }
    }

    @Override
    public void onDisable() {
        if (mc.player != null) mc.player.sendMessage(Text.literal("§7CommandExecutor disabled."), false);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }
}
