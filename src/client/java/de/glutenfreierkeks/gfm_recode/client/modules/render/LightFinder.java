package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.concurrent.ConcurrentHashMap;



public class LightFinder extends Module {

    public final IntSliderSetting radius  = register(new IntSliderSetting("Radius", "Scan radius around player (XZ)", 24, 5, 128));
    public final IntSliderSetting maxY    = register(new IntSliderSetting("Max Y",  "Highest Y to scan (use 0 for everything below surface)", 0, -64, 0));
    public final BoolSetting      outline = register(new BoolSetting("Outline", "Draw outline around boxes", true));
    public final BoolSetting      filled  = register(new BoolSetting("Fill",    "Draw translucent fill", true));

    private final ConcurrentHashMap<BlockPos, Integer> lightMap = new ConcurrentHashMap<>();

    private volatile boolean scanning   = false;
    private int              tickCounter = 0;

    public LightFinder() {
        super("LightFinder", "Highlights blocks below y=0 by surrounding block-light level", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override protected void onEnable()  { lightMap.clear(); }
    @Override protected void onDisable() { lightMap.clear(); }


    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (tickCounter++ % 30 == 0 && !scanning) {
            Thread t = new Thread(this::updateLightMap, "LightFinder-Scanner");
            t.setDaemon(true);
            t.start();
        }
    }

    private void updateLightMap() {
        scanning = true;
        try {
            if (mc.world == null || mc.player == null) return;

            BlockPos playerPos = mc.player.getBlockPos();
            int rad     = radius.getValue();
            int topY    = maxY.getValue();
            int bottomY = mc.world.getBottomY();

            ConcurrentHashMap<BlockPos, Integer> newMap = new ConcurrentHashMap<>();

            outer:
            for (int x = -rad; x <= rad; x++) {
                for (int z = -rad; z <= rad; z++) {
                    for (int y = topY; y >= bottomY; y--) {
                        if (mc.world == null) return;

                        BlockPos pos = playerPos.add(x, y - playerPos.getY(), z);
                        BlockState state = mc.world.getBlockState(pos);

                        if (state.isAir()) continue;

                        int maxLight = 0;
                        for (Direction dir : Direction.values()) {
                            int l = mc.world.getLightLevel(LightType.BLOCK, pos.offset(dir));
                            if (l > maxLight) maxLight = l;
                            if (maxLight == 15) break; // cant get higher
                        }

                        if (maxLight >= 10) {
                            newMap.put(pos, maxLight);
                        }

                        if (newMap.size() >= 3000) break outer;
                    }
                }
            }

            lightMap.clear();
            lightMap.putAll(newMap);

        } catch (Exception ignored) {
        } finally {
            scanning = false;
        }
    }


    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (lightMap.isEmpty()) return;

        Vec3d   camPos      = camera.getCameraPos();
        boolean drawOutline = outline.getValue();
        boolean drawFill    = filled.getValue();

        for (var entry : lightMap.entrySet()) {
            BlockPos pos   = entry.getKey();
            int      light = entry.getValue();

            double dx = pos.getX() - camPos.x;
            double dy = pos.getY() - camPos.y;
            double dz = pos.getZ() - camPos.z;

            Box box = new Box(dx + 0.02, dy + 0.96, dz + 0.02,
                              dx + 0.98, dy + 1.04, dz + 0.98);

            Color c = lightToColor(light);

            if (drawOutline) RenderUtil.drawBox(posMatrix, box, c, 1.0);
            if (drawFill)    RenderUtil.drawFilledBox(posMatrix, box, new Color(c.getRed(), c.getGreen(), c.getBlue(), 55));
        }
    }

    /**
     * 10 is Yellow
     * 15 is Green 
     */
    private static Color lightToColor(int light) {
        float t = (light - 10) / 5.0f;
        t = Math.max(0f, Math.min(1f, t));

        int r = (int)((1.0f - t) * 255);
        int g = (int)(208     + t * 47);   
        int b = (int)(t * 68);             

        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b)),
            220
        );
    }

    @Override
    public String getDisplayInfo() {
        return lightMap.isEmpty() ? null : lightMap.size() + " blocks";
    }
}
