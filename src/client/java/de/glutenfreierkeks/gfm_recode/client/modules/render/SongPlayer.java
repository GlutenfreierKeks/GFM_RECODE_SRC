package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.utils.NowPlayingPoller;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

public class SongPlayer extends Module {
    public SongPlayer() {
        super("Song Player", "Reads song from Spotify/browser window title + audio visualizer", Category.RENDER);
    }

    @Override
    protected void onEnable() {
        NowPlayingPoller.start();
    }

    @Override
    protected void onDisable() {
        NowPlayingPoller.stop();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }
}