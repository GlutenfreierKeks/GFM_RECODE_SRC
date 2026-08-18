package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.gui.web.BrowserEspOverlayState;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BrowserOverlayESP extends Module {
    public final IntSliderSetting maxDistance = register(new IntSliderSetting("Max Distance", "Entity overlay distance", 96, 8, 256));
    public final IntSliderSetting maxEntities = register(new IntSliderSetting("Max Entities", "Maximum overlay entities", 24, 1, 128));
    public final BoolSetting players = register(new BoolSetting("Players", "Render players", true));
    public final BoolSetting mobs = register(new BoolSetting("Mobs", "Render hostile mobs", true));
    public final BoolSetting animals = register(new BoolSetting("Animals", "Render passive mobs", false));

    public final ColorSetting playerColor = register(new ColorSetting("Player Color", "Player overlay color", 0, 200, 255, 255));
    public final ColorSetting mobColor = register(new ColorSetting("Mob Color", "Mob overlay color", 255, 70, 70, 255));
    public final ColorSetting animalColor = register(new ColorSetting("Animal Color", "Animal overlay color", 255, 190, 70, 255));

    public BrowserOverlayESP() {
        super("BrowserOverlayESP", "Renders ESP into the browser HUD overlay instead of the normal game world.", Category.RENDER);
        this.macroAllowed = true;
    }

    @Override
    protected void onDisable() {
        BrowserEspOverlayState.clear();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.world == null || mc.player == null) {
            BrowserEspOverlayState.clear();
            return;
        }

        double maxDistanceSq = (double) maxDistance.getValue() * maxDistance.getValue();
        Vec3d camPos = camera.getCameraPos();
        Matrix4f combined = new Matrix4f(projMatrix).mul(posMatrix);
        List<Map<String, Object>> overlayEntries = new ArrayList<>();
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            entities.add(entity);
        }
        entities.sort(Comparator.comparingDouble(entity -> mc.player.squaredDistanceTo(entity)));

        int processed = 0;
        for (Entity entity : entities) {
            if (processed >= maxEntities.getValue() * 3) break;
            if (entity == mc.player) continue;
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;

            double distSq = mc.player.squaredDistanceTo(entity);
            if (distSq > maxDistanceSq) continue;

            String type = classify(entity);
            if (type == null) continue;

            Map<String, Object> projected = projectEntity(entity, camPos, combined);
            if (projected == null) continue;

            projected.put("label", entity.getName().getString());
            projected.put("distance", Math.round(Math.sqrt(distSq) * 10.0) / 10.0);
            projected.put("type", type);
            projected.put("color", colorFor(type));
            projected.put("health", Math.round(living.getHealth() * 10.0) / 10.0);
            projected.put("maxHealth", Math.round(living.getMaxHealth() * 10.0) / 10.0);
            overlayEntries.add(projected);
            processed++;
        }

        overlayEntries.sort(Comparator.comparingDouble(entry -> ((Number) entry.getOrDefault("distance", 0.0)).doubleValue()));
        if (overlayEntries.size() > maxEntities.getValue()) {
            overlayEntries = new ArrayList<>(overlayEntries.subList(0, maxEntities.getValue()));
        }
        BrowserEspOverlayState.setEntries(overlayEntries);
    }

    private String classify(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return players.getValue() ? "player" : null;
        }
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }
        if (!living.getType().getSpawnGroup().isPeaceful()) {
            return mobs.getValue() ? "mob" : null;
        }
        return animals.getValue() ? "animal" : null;
    }

    private String colorFor(String type) {
        return switch (type) {
            case "player" -> argbToCss(playerColor.getArgb());
            case "mob" -> argbToCss(mobColor.getArgb());
            default -> argbToCss(animalColor.getArgb());
        };
    }

    private String argbToCss(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return "rgba(" + r + "," + g + "," + b + "," + String.format(java.util.Locale.ROOT, "%.3f", a / 255.0) + ")";
    }

    private Map<String, Object> projectEntity(Entity entity, Vec3d camPos, Matrix4f combined) {
        Box box = entity.getBoundingBox();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean anyVisible = false;

        for (int ix = 0; ix <= 1; ix++) {
            for (int iy = 0; iy <= 1; iy++) {
                for (int iz = 0; iz <= 1; iz++) {
                    double x = (ix == 0 ? box.minX : box.maxX) - camPos.x;
                    double y = (iy == 0 ? box.minY : box.maxY) - camPos.y;
                    double z = (iz == 0 ? box.minZ : box.maxZ) - camPos.z;

                    Vector4f clip = new Vector4f((float) x, (float) y, (float) z, 1.0f);
                    combined.transform(clip);
                    if (clip.w <= 0.01f) continue;

                    float ndcX = clip.x / clip.w;
                    float ndcY = clip.y / clip.w;
                    if (ndcX < -1.4f || ndcX > 1.4f || ndcY < -1.4f || ndcY > 1.4f) continue;

                    anyVisible = true;
                    double screenX = ndcX * 0.5 + 0.5;
                    double screenY = 1.0 - (ndcY * 0.5 + 0.5);
                    minX = Math.min(minX, screenX);
                    minY = Math.min(minY, screenY);
                    maxX = Math.max(maxX, screenX);
                    maxY = Math.max(maxY, screenY);
                }
            }
        }

        if (!anyVisible) return null;

        minX = clamp01(minX);
        minY = clamp01(minY);
        maxX = clamp01(maxX);
        maxY = clamp01(maxY);
        if (maxX - minX < 0.002 || maxY - minY < 0.002) return null;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("left", minX);
        data.put("top", minY);
        data.put("width", maxX - minX);
        data.put("height", maxY - minY);
        return data;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public String getDisplayInfo() {
        return maxDistance.getValue() + "m";
    }
}
