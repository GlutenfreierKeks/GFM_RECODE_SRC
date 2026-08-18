package de.glutenfreierkeks.gfm_recode.client.modules.world;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class StashFinder extends Module {

    private final BoolSetting chestEnabled = register(new BoolSetting("Chest Alert", "Alert when many chests are nearby", true));
    private final IntSliderSetting chestThreshold = register(new IntSliderSetting("Chest Threshold", "Min chests to trigger alert", 20, 3, 500));
    private final BoolSetting pearlEnabled = register(new BoolSetting("Pearl Alert", "Alert when ender pearls are on the ground", true));
    private final BoolSetting playerEnabled = register(new BoolSetting("Player Alert", "Alert when players are nearby", true));
    private final IntSliderSetting playerRange = register(new IntSliderSetting("Player Range", "Range to detect players", 50, 10, 200));
    private final StringSetting webhookUrl = register(new StringSetting("Webhook URL", "Discord webhook URL", ""));

    private final Set<Integer> sentPearlIds = new HashSet<>();
    private final Set<String> sentPlayerNames = new HashSet<>();
    private boolean chestAlerted = false;

    public StashFinder() {
        super("StashFinder", "Scans for stashes, pearls and players", Category.WORLD);
        this.macroAllowed = false;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (webhookUrl.getValue().isEmpty()) return;

        if (chestEnabled.getValue() && mc.player.age % 20 == 0) {
            scanChests();
        }

        if (pearlEnabled.getValue() && mc.player.age % 5 == 0) {
            scanPearls();
        }

        if (playerEnabled.getValue() && mc.player.age % 10 == 0) {
            scanPlayers();
        }
    }

    private void scanChests() {
        int count = 0;
        int renderDistance = mc.options.getClampedViewDistance();
        int pCx = mc.player.getBlockPos().getX() >> 4;
        int pCz = mc.player.getBlockPos().getZ() >> 4;

        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(pCx + x, pCz + z);
                if (chunk != null) {
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (isStorage(be)) count++;
                    }
                }
            }
        }

        if (count >= chestThreshold.getValue() && !chestAlerted) {
            chestAlerted = true;
            sendWebhook("Stash detected!",
                    count + " chests/containers nearby at "
                    + (int) mc.player.getX() + " " + (int) mc.player.getY() + " " + (int) mc.player.getZ());
        }
    }

    private void scanPearls() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ItemEntity item && item.getStack().getItem() == Items.ENDER_PEARL) {
                if (!sentPearlIds.contains(entity.getId())) {
                    sentPearlIds.add(entity.getId());
                    sendWebhook("Ender Pearl found!",
                            "At X: " + (int) entity.getX() + " Y: " + (int) entity.getY() + " Z: " + (int) entity.getZ()
                            + " | Owner: unknown");
                }
            }
            if (entity instanceof EnderPearlEntity pearl) {
                if (!sentPearlIds.contains(entity.getId())) {
                    sentPearlIds.add(entity.getId());
                    String owner = "unknown";
                    Entity ownerEntity = pearl.getOwner();
                    if (ownerEntity instanceof PlayerEntity p) {
                        owner = p.getName().getString();
                    }
                    sendWebhook("Ender Pearl thrown!",
                            "At X: " + (int) entity.getX() + " Y: " + (int) entity.getY() + " Z: " + (int) entity.getZ()
                            + " | Thrown by: " + owner);
                }
            }
        }
    }

    private void scanPlayers() {
        int range = playerRange.getValue();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player && entity != mc.player) {
                double dist = mc.player.distanceTo(player);
                if (dist <= range && !sentPlayerNames.contains(player.getName().getString())) {
                    sentPlayerNames.add(player.getName().getString());
                    sendWebhook("Player spotted!",
                            player.getName().getString() + " at X: " + (int) player.getX()
                            + " Y: " + (int) player.getY() + " Z: " + (int) player.getZ()
                            + " | Distance: " + (int) dist + "m");
                }
            }
        }
    }

    private boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof TrappedChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof ShulkerBoxBlockEntity
                || be instanceof HopperBlockEntity
                || be instanceof DropperBlockEntity
                || be instanceof DispenserBlockEntity;
    }

    @Override
    public void onDisable() {
        sentPearlIds.clear();
        sentPlayerNames.clear();
        chestAlerted = false;
    }

    private void sendWebhook(String title, String description) {
        String urlStr = webhookUrl.getValue();
        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String json = "{"
                        + "\"embeds\": [{"
                        + "\"title\": \"🔍 " + title + "\","
                        + "\"color\": 16755200,"
                        + "\"fields\": ["
                        + "{\"name\": \"Details\", \"value\": \"" + description + "\", \"inline\": false}"
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
