package de.glutenfreierkeks.gfm_recode.client.modules.misc;

import de.glutenfreierkeks.gfm_recode.client.discord.DiscordPresenceManager;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class DiscordPlaying extends Module {
    public final StringSetting applicationId = register(new StringSetting(
            "Application ID",
            "Discord application ID used for Rich Presence",
            "1436763416436932721"
    ));

    public DiscordPlaying() {
        super("Discord Playing", "Shows GFM usage and active module count on Discord", Category.MISC);
    }

    @Override
    protected void onDisable() {
        DiscordPresenceManager.shutdown();
    }

    @Override
    public void onTick() {
        DiscordPresenceManager.tick(this);
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }
}
