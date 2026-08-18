package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.ColorSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderFx;
import de.glutenfreierkeks.gfm_recode.client.utils.RenderUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class ItemESP extends Module {

    private final BoolSetting box = register(new BoolSetting("Box", "Draw a 3D ESP Box", true));
    private final BoolSetting nameTag = register(new BoolSetting("Name Tag", "Show the item's name tag above it", true));
    private final ColorSetting color = register(new ColorSetting("Color", "ESP box and tag color", 255, 255, 255, 255));
    private final BoolSetting webhookEnabled = register(new BoolSetting("Webhook Enabled", "Send notifications to Discord on high tier items", false));
    private final StringSetting webhookUrl = register(new StringSetting("Webhook URL", "Discord webhook URL", ""));

    private final Set<Integer> sentEntities = new HashSet<>();

    public ItemESP() {
        super("ItemESP", "Highlights items on the ground and alerts on valuable loot", Category.RENDER);
        this.macroAllowed = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
        if (mc.world == null || mc.player == null) return;

        Vec3d camPos = camera.getCameraPos();
        Color renderColor = new Color(color.getArgb(), true);
        float r = renderColor.getRed() / 255f;
        float g = renderColor.getGreen() / 255f;
        float b = renderColor.getBlue() / 255f;
        float a = renderColor.getAlpha() / 255f;

        VertexConsumer vc = RenderUtil.beginBatch();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            double x = entity.lastX + (entity.getX() - entity.lastX) * tickDelta;
            double y = entity.lastY + (entity.getY() - entity.lastY) * tickDelta;
            double z = entity.lastZ + (entity.getZ() - entity.lastZ) * tickDelta;

            double rx = x - camPos.x;
            double ry = y - camPos.y;
            double rz = z - camPos.z;

            float width = entity.getWidth();
            float height = entity.getHeight();

            Box itemBox = new Box(
                    rx - width / 2.0, ry, rz - width / 2.0,
                    rx + width / 2.0, ry + height, rz + width / 2.0
            );

            if (box.getValue()) {
                RenderUtil.batchOutlineBox(vc, posMatrix, itemBox, r, g, b, a, 1.0f);
                RenderUtil.batchFilledBox(vc, posMatrix, itemBox, r, g, b, a * 0.15f);
            }

            // Webhook check (Elytra, Diamond items, Netherite items)
            if (webhookEnabled.getValue() && !webhookUrl.getValue().isEmpty()) {
                if (!sentEntities.contains(entity.getId())) {
                    ItemStack stack = itemEntity.getStack();
                    String name = stack.getItem().getName().getString().toLowerCase();
                    if (name.contains("elytra") || name.contains("diamond") || name.contains("netherite")) {
                        sentEntities.add(entity.getId());
                        sendWebhookNotification(stack.getItem().getName().getString(), stack.getCount(), entity.getX(), entity.getY(), entity.getZ());
                    }
                }
            }
        }

        RenderUtil.endBatch();
    }

    @Override
    public void onTick() {
        if (mc.world == null) {
            sentEntities.clear();
        }
    }

    @Override
    public void onDisable() {
        sentEntities.clear();
    }

    private void sendWebhookNotification(String itemName, int count, double x, double y, double z) {
        new Thread(() -> {
            try {
                URL url = new URL(webhookUrl.getValue());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{"
                        + "\"embeds\": [{"
                        + "\"title\": \"🔥 High-Value Item Detected! 🔥\","
                        + "\"color\": 16755200,"
                        + "\"fields\": ["
                        + "{\"name\": \"Item\", \"value\": \"" + itemName + " x" + count + "\", \"inline\": true},"
                        + "{\"name\": \"Location\", \"value\": \"X: " + (int) x + ", Y: " + (int) y + ", Z: " + (int) z + "\", \"inline\": true}"
                        + "]"
                        + "}]"
                        + "}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }
}
