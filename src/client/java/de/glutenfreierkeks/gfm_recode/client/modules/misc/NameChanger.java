package de.glutenfreierkeks.gfm_recode.client.modules.misc;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class NameChanger extends Module {

    private final StringSetting replacementName = register(
        new StringSetting("Name", "Name shown locally instead of your real name", "Player")
    );

    public NameChanger() {
        super("Name Changer", "Changes your shown name only on your client", Category.MISC);
    }

    @Override
    protected void onEnable() {
        refreshChat();
    }

    @Override
    protected void onDisable() {
        refreshChat();
    }

    public String getReplacementName() {
        return replacementName.getValue().trim();
    }

    public boolean hasReplacement() {
        return !getReplacementName().isEmpty();
    }

    private void refreshChat() {
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().reset();
        }
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}
