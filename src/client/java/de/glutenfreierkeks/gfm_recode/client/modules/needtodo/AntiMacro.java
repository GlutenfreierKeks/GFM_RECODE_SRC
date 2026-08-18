package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.RotationUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AntiMacro extends Module {

    private static final String[] SUSPICIOUS_KEYWORDS = new String[]{
            "cheat", "macro", "hack", "bot", "afk", "script",
            "banned", "report", "illegal", "unfair", "automat", "sus", "check", "are you there", "hello", "hi", "wsp", "here"
    };

    // --- SETTINGS ---
    private final BoolSetting chatDetection = register(new BoolSetting("ChatDetection", "Detect suspicious chat messages", true));
    private final BoolSetting dmDetection = register(new BoolSetting("DMDetection", "Detect direct messages", true));
    private final BoolSetting debugMode = register(new BoolSetting("DebugMode", "Show debug messages in chat", true));
    private final BoolSetting proximityDetection = register(new BoolSetting("ProximityDetection", "Detect players staring at you", true));
    private final BoolSetting teleportDetection = register(new BoolSetting("TeleportDetection", "Detect sudden teleports", true));
    private final IntSliderSetting proximityRange = register(new IntSliderSetting("ProximityRange", "Range for player detection", 4, 1, 10));
    private final IntSliderSetting freezeDuration = register(new IntSliderSetting("FreezeDuration", "How long to freeze modules", 3000, 1000, 10000));
    private final IntSliderSetting sneakDuration = register(new IntSliderSetting("SneakDuration", "How long to sneak when detected", 3000, 500, 5000));
    private final IntSliderSetting responseDelay = register(new IntSliderSetting("ResponseDelay", "Delay before AI response", 2500, 500, 8000));
    private final IntSliderSetting lookAroundDuration = register(new IntSliderSetting("LookAroundDuration", "How long to look around after teleport", 2000, 1000, 5000));

    // --- STATE VARIABLES ---
    private boolean frozen = false;
    private long freezeStartTime = 0L;
    private final List<Module> pausedModules = new ArrayList<>();

    private boolean sneaking = false;
    private long sneakStartTime = 0L;
    private PlayerEntity watchingPlayer = null;

    private boolean lookingAround = false;
    private long lookAroundStartTime = 0L;

    private BlockPos lastPlayerPos = null;
    private long lastPosCheckTime = 0L;
    private static final long POS_CHECK_INTERVAL = 1000;

    private final Map<String, List<String>> conversations = new HashMap<>();
    private final Map<String, Long> lastMessageTime = new HashMap<>();
    private static final long CONVERSATION_TIMEOUT = 45000;

    private final Random random = new Random();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String[] CONFUSED_MESSAGES = new String[]{
            "huh?", "wtf was that", "why do i get tpd?", "bruh why am i moving around?", "wait what", "Is that a macro check? if yes please stop"
    };

    public AntiMacro() {
        super("AntiMacro", "AI Anti-Macro with state protection.", Category.MISC);
    }

    @Override
    public void onEnable() {
        resetStates();
        if (mc.player != null) lastPlayerPos = mc.player.getBlockPos();
        lastPosCheckTime = System.currentTimeMillis();
        sendDebug("Active.");
    }

    private void resetStates() {
        frozen = false;
        sneaking = false;
        lookingAround = false;
        watchingPlayer = null;
        pausedModules.clear();
        conversations.clear();
        lastMessageTime.clear();
    }

    // Helper to check if the module is currently "in a reaction"
    private boolean isBusy() {
        return frozen || sneaking || lookingAround;
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        cleanupConversations();

        // 1. Handle Look Around (Teleport Reaction)
        if (lookingAround) {
            handleLookAround();
            return;
        }

        // 2. Handle Staring/Sneaking logic
        if (sneaking) {
            if (System.currentTimeMillis() - sneakStartTime >= sneakDuration.getValue()) {
                stopSneaking();
            } else if (watchingPlayer != null && watchingPlayer.isAlive() && mc.player.distanceTo(watchingPlayer) <= proximityRange.getValue() + 3) {
                float[] rots = RotationUtil.getRotations(watchingPlayer.getEyePos());
                mc.player.setYaw(rots[0]);
                mc.player.setPitch(rots[1]);
            } else {
                stopSneaking();
            }
            return;
        }

        // 3. Logic for detecting new threats (Only if not already busy)
        if (!isBusy()) {
            if (teleportDetection.getValue()) checkTeleport();
            if (proximityDetection.getValue()) checkProximityThreat();
        }
    }

    // ==========================================
    // CHAT MESSAGE HANDLER
    // ==========================================
    public void onChatMessage(Text message) {
        if (mc.player == null) return;

        // IMPORTANT: If we are already reacting, don't trigger AI again to avoid state desync
        if (isBusy()) return;

        String fullMessage = message.getString();
        if (fullMessage.contains("[AM-Debug]") || fullMessage.contains("[AntiMacro]")) return;

        String playerName = mc.player.getName().getString();
        ChatMessageInfo info = parseMessage(fullMessage, playerName);

        if (info == null) return;

        if (info.isDM && dmDetection.getValue()) {
            handleDirectMessage(info);
        } else if (chatDetection.getValue() && (info.mentionsMe || info.isDirectedAtMe)) {
            handlePublicMessage(info);
        }
    }

    private void handleDirectMessage(ChatMessageInfo info) {
        trackConversation(info.sender, info.content);
        triggerAIResponse(info, true);
    }

    private void handlePublicMessage(ChatMessageInfo info) {
        trackConversation(info.sender, info.content);
        triggerAIResponse(info, info.isSuspicious);
    }

    private void trackConversation(String player, String message) {
        conversations.computeIfAbsent(player, k -> new ArrayList<>()).add(message);
        lastMessageTime.put(player, System.currentTimeMillis());
    }

    private void triggerAIResponse(ChatMessageInfo info, boolean isSuspicious) {
        freezeAllModules(); // This sets frozen = true
        sendDebug("Thinking...");

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(responseDelay.getValue() + random.nextInt(1500));
                String response = getAIResponse(info, isSuspicious);

                mc.execute(() -> {
                    if (response != null && !response.isEmpty()) {
                        sendResponse(info, response);
                    } else {
                        unfreezeModules();
                    }
                });
            } catch (Exception e) {
                mc.execute(this::unfreezeModules);
            }
        });
    }

    private void sendResponse(ChatMessageInfo info, String response) {
        if (response.contains("action:jump")) {
            mc.player.jump();
            response = "happy now?";
        } else if (response.contains("action:spin")) {
            performSpin(true);
            response = "weee";
        }

        if (info.isDM && info.sender != null) {
            mc.player.networkHandler.sendChatCommand("msg " + info.sender + " " + response);
        } else {
            mc.player.networkHandler.sendChatMessage(response);
        }

        // Small delay before unfreezing to feel human
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (Exception ignored) {}
            mc.execute(this::unfreezeModules);
        }).start();
    }

    // ==========================================
    // CORE UTILITIES
    // ==========================================

    private void freezeAllModules() {
        if (frozen) return; // Don't freeze twice
        frozen = true;
        freezeStartTime = System.currentTimeMillis();
        pausedModules.clear();
        if (Gfm_recodeClient.modules != null) {
            for (Module module : Gfm_recodeClient.modules.getAll()) {
                if (module != this && module.isEnabled()) {
                    module.disable();
                    pausedModules.add(module);
                }
            }
        }
    }

    private void unfreezeModules() {
        if (!frozen) return;
        for (Module module : pausedModules) {
            module.enable();
        }
        pausedModules.clear();
        frozen = false;
        sendDebug("Resumed.");
    }

    private void stopSneaking() {
        if (!sneaking) return;
        sneaking = false;
        watchingPlayer = null;
        if (mc.player != null) mc.player.setSneaking(false);
        unfreezeModules();
    }

    private void triggerProximityResponse(PlayerEntity player) {
        if (isBusy()) return;
        sendDebug("Staring at: " + player.getName().getString());
        freezeAllModules();
        this.watchingPlayer = player;
        this.sneaking = true;
        this.sneakStartTime = System.currentTimeMillis();
        mc.player.setSneaking(true);
    }

    private void checkTeleport() {
        if (System.currentTimeMillis() - lastPosCheckTime < POS_CHECK_INTERVAL) return;
        BlockPos currentPos = mc.player.getBlockPos();
        if (lastPlayerPos != null && Math.sqrt(currentPos.getSquaredDistance(lastPlayerPos)) > 15) {
            triggerTeleportReaction();
        }
        lastPlayerPos = currentPos;
        lastPosCheckTime = System.currentTimeMillis();
    }

    private void triggerTeleportReaction() {
        freezeAllModules();
        lookingAround = true;
        lookAroundStartTime = System.currentTimeMillis();
        if (random.nextBoolean()) {
            mc.player.networkHandler.sendChatMessage(CONFUSED_MESSAGES[random.nextInt(CONFUSED_MESSAGES.length)]);
        }
    }

    private void handleLookAround() {
        if (System.currentTimeMillis() - lookAroundStartTime >= lookAroundDuration.getValue()) {
            lookingAround = false;
            unfreezeModules();
            return;
        }
        mc.player.setYaw(mc.player.getYaw() + (random.nextFloat() * 10 - 5));
        mc.player.setPitch(mc.player.getPitch() + (random.nextFloat() * 10 - 5));
    }

    private void checkProximityThreat() {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isSpectator()) continue;
            if (mc.player.distanceTo(player) <= proximityRange.getValue() && isPlayerLookingAtMe(player)) {
                triggerProximityResponse(player);
                break;
            }
        }
    }

    private boolean isPlayerLookingAtMe(PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d toMe = mc.player.getEyePos().subtract(player.getEyePos()).normalize();
        return look.dotProduct(toMe) > 0.85;
    }

    private void performSpin(boolean right) {
        new Thread(() -> {
            try {
                float startYaw = mc.player.getYaw();
                for (int i = 0; i < 10; i++) {
                    float newYaw = startYaw + (right ? i * 36 : -i * 36);
                    mc.execute(() -> { if (mc.player != null) mc.player.setYaw(newYaw); });
                    Thread.sleep(50);
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void sendDebug(String message) {
        if (debugMode.getValue() && mc.player != null) {
            mc.player.sendMessage(Text.literal("§7[AM-Debug] " + message), false);
        }
    }

    private void cleanupConversations() {
        long now = System.currentTimeMillis();
        lastMessageTime.entrySet().removeIf(entry -> now - entry.getValue() > CONVERSATION_TIMEOUT);
    }

    private String getAIResponse(ChatMessageInfo info, boolean isSuspicious) {
        // Basic placeholder or implementation using pollinations.ai
        return null;
    }

    private ChatMessageInfo parseMessage(String message, String myName) {
        ChatMessageInfo info = new ChatMessageInfo();
        String clean = message.replaceAll("§[0-9a-fk-or]", "");
        if (clean.contains(":") || clean.contains(">")) {
            String[] split = clean.split("[:>]", 2);
            info.sender = split[0].replaceAll("[<>\\[\\]]", "").trim();
            info.content = split[1].trim();
        } else {
            info.content = clean;
        }
        if (info.sender != null && info.sender.equalsIgnoreCase(myName)) return null;
        info.isDM = clean.toLowerCase().contains("from") || clean.toLowerCase().contains("whisper");
        info.mentionsMe = clean.toLowerCase().contains(myName.toLowerCase());
        for (String k : SUSPICIOUS_KEYWORDS) if (clean.toLowerCase().contains(k)) info.isSuspicious = true;
        info.isDirectedAtMe = info.isDM || info.mentionsMe || lastMessageTime.containsKey(info.sender);
        return info;
    }

    @Override
    public void onDisable() { unfreezeModules(); resetStates(); }

    private static class ChatMessageInfo {
        String sender, content;
        boolean isDM, mentionsMe, isSuspicious, isDirectedAtMe;
    }
}
