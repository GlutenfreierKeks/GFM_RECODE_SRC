package de.glutenfreierkeks.gfm_recode.client.gui.web;

import com.google.gson.Gson;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.anticheat.AntiCheatProfileManager;
import de.glutenfreierkeks.gfm_recode.client.config.ConfigManager;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.modules.render.HudModule;
import de.glutenfreierkeks.gfm_recode.client.modules.render.SongPlayer;
import de.glutenfreierkeks.gfm_recode.client.modules.render.StatsHider;
import de.glutenfreierkeks.gfm_recode.client.utils.NameProtectUtil;
import de.glutenfreierkeks.gfm_recode.client.utils.NowPlayingPoller;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HudRenderer {
    private static final ResolutionManager RESOLUTION_MANAGER = ResolutionManager.getInstance();
    private static final Gson GSON = new Gson();
    private static final Comparator<ScoreboardEntry> SCOREBOARD_ENTRY_COMPARATOR = Comparator.comparing(ScoreboardEntry::value)
            .reversed()
            .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static BrowserRenderer browser;
    private static long lastStatPush;
    private static long lastSongPush;
    private static Map<String, Object> lastSongPayload = Map.of("enabled", false);

    private HudRenderer() {
    }

    private static Map<String, Object> buildSongPlayerData() {
        SongPlayer songPlayer = Gfm_recodeClient.modules == null ? null : Gfm_recodeClient.modules.getModuleByClass(SongPlayer.class);
        Map<String, Object> data = new LinkedHashMap<>();
        boolean enabled = songPlayer != null && songPlayer.isEnabled();
        data.put("enabled", enabled);
        if (enabled) {
            data.putAll(NowPlayingPoller.getCurrent());
        }
        return data;
    }


    public static void init() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> render(drawContext));
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden || client.currentScreen instanceof GuiScreen) {
            return;
        }

        Module hudModule = getHudModule();
        if (hudModule == null || !hudModule.isEnabled()) {
            return;
        }

        if (browser == null) {
            browser = new BrowserRenderer("hud", "http://localhost:" + WebUiServer.currentPort + "/hud?v=", RESOLUTION_MANAGER, true);
        }

        if (browser.isReady()) {
            browser.renderFullscreen(context);
            pushHudState(client);
        } else {
            renderFallback(context, hudModule);
        }
    }

    private static HudModule getHudModule() {
        if (Gfm_recodeClient.modules == null) {
            return null;
        }
        Module module = Gfm_recodeClient.modules.getByName("HUD");
        return module instanceof HudModule hudModule ? hudModule : null;
    }

    private static void pushHudState(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastStatPush < 125L) {
            return;
        }

        double speed = Math.sqrt(client.player.getVelocity().x * client.player.getVelocity().x
                + client.player.getVelocity().z * client.player.getVelocity().z) * 20.0;
        int ping = 0;
        try {
            if (client.getNetworkHandler() != null && client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()) != null) {
                ping = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()).getLatency();
            }
        } catch (Exception ignored) {
        }

        String server = client.getCurrentServerEntry() != null ? client.getCurrentServerEntry().address : "Singleplayer";
        String biome = "unknown";
        try {
            biome = client.world.getBiome(client.player.getBlockPos()).getKey().map(key -> key.getValue().getPath()).orElse("unknown");
        } catch (Exception ignored) {
        }

        String direction = client.player.getHorizontalFacing().asString();
        long time = client.world.getTimeOfDay() % 24000L;
        int hours = (int) (time / 1000L) + 6;
        if (hours >= 24) {
            hours -= 24;
        }
        int minutes = (int) ((time % 1000L) * 60L / 1000L);
        String timeString = String.format("%02d:%02d", hours, minutes);
        String durability = client.player.getMainHandStack().isEmpty()
                ? "N/A"
                : String.valueOf(client.player.getMainHandStack().getMaxDamage() - client.player.getMainHandStack().getDamage());
        long maxMemory = Runtime.getRuntime().maxMemory() / 1024L / 1024L;
        long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024L / 1024L;
        int air = client.player.getAir();
        int maxAir = client.player.getMaxAir();

        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("fps", client.getCurrentFps());
        stats.put("pos", String.format(Locale.US, "%.1f / %.1f / %.1f", client.player.getX(), client.player.getY(), client.player.getZ()));
        stats.put("speed", String.format(Locale.US, "%.2f", speed));
        stats.put("ping", ping);
        stats.put("server", server);
        stats.put("biome", biome);
        stats.put("direction", direction);
        stats.put("time", timeString);
        stats.put("tps", "20.0");
        stats.put("durability", durability);
        stats.put("memory", usedMemory + "/" + maxMemory + " MB");
        payload.put("stats", stats);

        Map<String, Object> vitals = new LinkedHashMap<>();
        vitals.put("health", round1(client.player.getHealth()));
        vitals.put("maxHealth", round1(client.player.getMaxHealth()));
        vitals.put("absorption", round1(client.player.getAbsorptionAmount()));
        vitals.put("armor", client.player.getArmor());
        vitals.put("food", client.player.getHungerManager().getFoodLevel());
        vitals.put("air", air);
        vitals.put("maxAir", maxAir);
        vitals.put("xpProgress", client.player.experienceProgress);
        vitals.put("xpLevel", client.player.experienceLevel);
        payload.put("vitals", vitals);

        Map<String, Object> world = new LinkedHashMap<>();
        world.put("weather", describeWeather(client));
        world.put("day", (client.world.getTimeOfDay() / 24000L) + 1L);
        world.put("dimension", client.world.getRegistryKey().getValue().getPath());
        payload.put("world", world);

        payload.put("hotbar", buildHotbarData(client));
        payload.put("scoreboard", buildScoreboardData(client));
        payload.put("effects", buildEffectsData(client));
        payload.put("radar", buildRadarData(client));
        payload.put("combat", buildCombatData(client));
        payload.put("inventory", buildInventoryData(client));
        payload.put("session", buildSessionData(client, world));
        SongPlayer songModule = Gfm_recodeClient.modules == null ? null : Gfm_recodeClient.modules.getModuleByClass(SongPlayer.class);
        boolean songEnabled = songModule != null && songModule.isEnabled();
        if (now - lastSongPush >= 1000L || songEnabled != Boolean.TRUE.equals(lastSongPayload.get("enabled"))) {
            lastSongPayload = buildSongPlayerData();
            lastSongPush = now;
        } else if (!songEnabled) {
            lastSongPayload = Map.of("enabled", false);
        }
        payload.put("songPlayer", lastSongPayload);
        payload.put("speedometer", Map.of(
                "value", round2(speed),
                "max", 12.0,
                "attackCooldown", round2(client.player.getAttackCooldownProgress(0.0F))
        ));
        payload.put("browserEsp", BrowserEspOverlayState.getEntries());

        String script = "if (window.updateHudState) { window.updateHudState(" + GSON.toJson(payload) + "); }";
        browser.executeJavaScript(script);
        lastStatPush = now;
    }

    private static Map<String, Object> buildHotbarData(MinecraftClient client) {
        Map<String, Object> hotbar = new LinkedHashMap<>();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            slots.add(serializeStack(client.player.getInventory().getStack(i), i == client.player.getInventory().getSelectedSlot(), i));
        }
        hotbar.put("selectedSlot", client.player.getInventory().getSelectedSlot());
        hotbar.put("slots", slots);
        hotbar.put("offhand", serializeStack(client.player.getOffHandStack(), false, -1));
        return hotbar;
    }

    private static Map<String, Object> buildCombatData(MinecraftClient client) {
        int nearbyPlayers = 0;
        double closestPlayer = Double.MAX_VALUE;

        for (Entity entity : client.world.getOtherEntities(client.player, client.player.getBoundingBox().expand(6.0), entity -> entity instanceof net.minecraft.entity.player.PlayerEntity && !entity.isSpectator())) {
            nearbyPlayers++;
            closestPlayer = Math.min(closestPlayer, Math.sqrt(client.player.squaredDistanceTo(entity)));
        }

        return Map.of(
                "attackCooldown", round2(client.player.getAttackCooldownProgress(0.0F)),
                "hurtTime", client.player.hurtTime,
                "nearbyPlayers", nearbyPlayers,
                "closestPlayer", closestPlayer == Double.MAX_VALUE ? "-" : String.format(Locale.US, "%.1fm", closestPlayer),
                "antiCheat", AntiCheatProfileManager.getCurrentProfile().name()
        );
    }

    private static Map<String, Object> buildInventoryData(MinecraftClient client) {
        return Map.of(
                "totems", countItem(client, Items.TOTEM_OF_UNDYING),
                "crystals", countItem(client, Items.END_CRYSTAL),
                "gapples", countItem(client, Items.ENCHANTED_GOLDEN_APPLE) + countItem(client, Items.GOLDEN_APPLE),
                "pearls", countItem(client, Items.ENDER_PEARL),
                "xpBottles", countItem(client, Items.EXPERIENCE_BOTTLE)
        );
    }

    private static Map<String, Object> buildSessionData(MinecraftClient client, Map<String, Object> world) {
        String mode = de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.antiCheatProfile.name();

        return Map.of(
                "config", ConfigManager.getCurrentConfigName(),
                "mode", mode,
                "weather", String.valueOf(world.get("weather")),
                "dimension", String.valueOf(world.get("dimension")),
                "day", String.valueOf(world.get("day"))
        );
    }


    private static Map<String, Object> serializeStack(ItemStack stack, boolean selected, int slot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("slot", slot);
        data.put("selected", selected);
        data.put("empty", stack.isEmpty());
        if (!stack.isEmpty()) {
            data.put("name", stack.getName().getString());
            data.put("itemId", Registries.ITEM.getId(stack.getItem()).toString());
            data.put("count", stack.getCount());
            data.put("maxCount", stack.getMaxCount());
            data.put("damage", stack.getDamage());
            data.put("maxDamage", stack.getMaxDamage());
            data.put("damageable", stack.isDamageable());
            data.put("barWidth", stack.getItemBarStep());
        }
        return data;
    }

    private static Map<String, Object> buildScoreboardData(MinecraftClient client) {
        Map<String, Object> data = new LinkedHashMap<>();
        ScoreboardObjective objective = getSidebarObjective(client);
        if (objective == null) {
            data.put("title", "");
            data.put("entries", List.of());
            return data;
        }

        StatsHider statsHider = Gfm_recodeClient.modules == null ? null : Gfm_recodeClient.modules.getModuleByClass(StatsHider.class);
        boolean statsEnabled = statsHider != null && statsHider.isEnabled();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);
        Scoreboard scoreboard = objective.getScoreboard();
        List<Map<String, String>> entries = new ArrayList<>();

        List<ScoreboardEntry> scoreboardEntries = scoreboard.getScoreboardEntries(objective)
                .stream()
                .filter(entry -> !entry.hidden())
                .sorted(SCOREBOARD_ENTRY_COMPARATOR)
                .limit(15L)
                .toList();

        for (int i = 0; i < scoreboardEntries.size(); i++) {
            ScoreboardEntry entry = scoreboardEntries.get(i);
            Team team = scoreboard.getScoreHolderTeam(entry.owner());
            Text name = Team.decorateName(team, entry.name());
            if (statsEnabled) {
                name = statsHider.getRenderedLine(i, name);
            }
            name = NameProtectUtil.replaceOwnName(name);

            Map<String, String> line = new LinkedHashMap<>();
            line.put("label", name.getString());
            line.put("score", entry.formatted(numberFormat).getString());
            entries.add(line);
        }

        Text title = statsEnabled ? statsHider.getRenderedTitle(objective.getDisplayName()) : objective.getDisplayName();
        title = NameProtectUtil.replaceOwnName(title);
        data.put("title", title.getString());
        data.put("entries", entries);
        return data;
    }

    private static ScoreboardObjective getSidebarObjective(MinecraftClient client) {
        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective teamObjective = null;
        Team team = scoreboard.getScoreHolderTeam(client.player.getNameForScoreboard());
        if (team != null) {
            ScoreboardDisplaySlot teamSlot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (teamSlot != null) {
                teamObjective = scoreboard.getObjectiveForSlot(teamSlot);
            }
        }
        return teamObjective != null ? teamObjective : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
    }

    private static List<Map<String, Object>> buildEffectsData(MinecraftClient client) {
        List<Map<String, Object>> effects = new ArrayList<>();
        for (StatusEffectInstance effect : client.player.getStatusEffects()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", effect.getEffectType().value().getName().getString());
            data.put("amplifier", effect.getAmplifier() + 1);
            data.put("duration", formatDuration(effect.getDuration()));
            data.put("ambient", effect.isAmbient());
            effects.add(data);
        }
        return effects;
    }

    private static Map<String, Object> buildRadarData(MinecraftClient client) {
        List<Map<String, Object>> points = new ArrayList<>();
        Box area = Box.of(new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ()), 96.0, 48.0, 96.0);
        List<Entity> nearby = client.world.getOtherEntities(client.player, area, entity ->
                entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator());

        nearby.stream()
                .sorted(Comparator.comparingDouble(client.player::squaredDistanceTo))
                .limit(20)
                .forEach(entity -> {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("x", round2(entity.getX() - client.player.getX()));
                    point.put("z", round2(entity.getZ() - client.player.getZ()));
                    point.put("distance", round2(Math.sqrt(client.player.squaredDistanceTo(entity))));
                    point.put("label", entity.getName().getString());
                    point.put("type", entity instanceof net.minecraft.entity.player.PlayerEntity ? "player" : "mob");
                    points.add(point);
                });

        return Map.of(
                "yaw", round2(client.player.getYaw()),
                "entries", points
        );
    }

    private static String formatDuration(int ticks) {
        if (ticks < 0) {
            return "Infinite";
        }
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private static String describeWeather(MinecraftClient client) {
        if (client.world.isThundering()) {
            return "Thunder";
        }
        if (client.world.isRaining()) {
            return "Rain";
        }
        return "Clear";
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static int countItem(MinecraftClient client, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.getItem() == item) {
                count += stack.getCount();
            }
        }
        if (client.player.getOffHandStack().getItem() == item) {
            count += client.player.getOffHandStack().getCount();
        }
        return count;
    }

    private static void renderFallback(DrawContext context, Module hudModule) {
    }

    public static void forwardMouseMove(double mouseX, double mouseY) {
        if (browser == null || !browser.isReady()) {
            return;
        }
        ResolutionManager.ResolutionSnapshot snapshot = RESOLUTION_MANAGER.getCurrent();
        browser.sendMouseMove(snapshot.toFramebufferX(mouseX), snapshot.toFramebufferY(mouseY));
    }

    public static void forwardMousePress(Click click) {
        if (browser == null || !browser.isReady()) {
            return;
        }
        ResolutionManager.ResolutionSnapshot snapshot = RESOLUTION_MANAGER.getCurrent();
        browser.sendMousePress(snapshot.toFramebufferX(click.x()), snapshot.toFramebufferY(click.y()), click.button());
    }

    public static void forwardMouseRelease(Click click) {
        if (browser == null || !browser.isReady()) {
            return;
        }
        ResolutionManager.ResolutionSnapshot snapshot = RESOLUTION_MANAGER.getCurrent();
        browser.sendMouseRelease(snapshot.toFramebufferX(click.x()), snapshot.toFramebufferY(click.y()), click.button());
    }

    public static void forwardMouseScroll(double mouseX, double mouseY, double verticalAmount, double horizontalAmount) {
        if (browser == null || !browser.isReady()) {
            return;
        }
        ResolutionManager.ResolutionSnapshot snapshot = RESOLUTION_MANAGER.getCurrent();
        browser.sendMouseWheel(
                snapshot.toFramebufferX(mouseX),
                snapshot.toFramebufferY(mouseY),
                verticalAmount,
                horizontalAmount
        );
    }

    public static void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }
}
