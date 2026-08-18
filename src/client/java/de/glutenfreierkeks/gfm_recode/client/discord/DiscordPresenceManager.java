package de.glutenfreierkeks.gfm_recode.client.discord;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.modules.misc.DiscordPlaying;
import io.github.kawaxte.presence.DiscordEventHandlers;
import io.github.kawaxte.presence.DiscordRPC;
import io.github.kawaxte.presence.DiscordRichPresence;
import net.minecraft.client.MinecraftClient;

import java.util.Locale;

public final class DiscordPresenceManager {
    private static boolean initialized;
    private static String currentApplicationId = "";
    private static long startTimestamp;
    private static long lastPresenceUpdate;

    private DiscordPresenceManager() {
    }

    public static void tick(DiscordPlaying module) {
        if (module == null || !module.isEnabled()) {
            shutdown();
            return;
        }

        String applicationId = module.applicationId.getValue().trim();
        if (!isValidApplicationId(applicationId)) {
            shutdown();
            return;
        }

        if (!initialized || !applicationId.equals(currentApplicationId)) {
            initialize(applicationId);
        }

        if (!initialized) {
            return;
        }

        DiscordRPC.runCallbacks();

        long now = System.currentTimeMillis();
        if (now - lastPresenceUpdate < 2_000L) {
            return;
        }

        updatePresence(module);
        lastPresenceUpdate = now;
    }

    public static void shutdown() {
        if (!initialized) {
            return;
        }

        try {
            DiscordRPC.clearPresence();
            DiscordRPC.shutdown();
        } catch (Throwable throwable) {
            Gfm_recodeClient.LOG.warn("Failed to shut down Discord Rich Presence cleanly", throwable);
        } finally {
            initialized = false;
            currentApplicationId = "";
            lastPresenceUpdate = 0L;
            startTimestamp = 0L;
        }
    }

    private static void initialize(String applicationId) {
        shutdown();

        try {
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            DiscordRPC.initialise(applicationId, handlers, true, null);
            initialized = true;
            currentApplicationId = applicationId;
            startTimestamp = System.currentTimeMillis() / 1000L;
            lastPresenceUpdate = 0L;
            updatePresence(null);
        } catch (Throwable throwable) {
            initialized = false;
            currentApplicationId = "";
            Gfm_recodeClient.LOG.warn("Failed to initialize Discord Rich Presence", throwable);
        }
    }

    private static void updatePresence(DiscordPlaying module) {
        if (!initialized) {
            return;
        }

        DiscordRichPresence presence = new DiscordRichPresence();
        presence.state = buildState(module);
        presence.details = buildDetails();
        presence.startTimestamp = startTimestamp;

        try {
            DiscordRPC.updatePresence(presence);
        } catch (Throwable throwable) {
            Gfm_recodeClient.LOG.warn("Failed to update Discord Rich Presence", throwable);
        }
    }

    private static String buildDetails() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return "Using GFM Client";
        }

        if (client.player == null) {
            return "Using GFM Client";
        }

        String worldInfo = client.getCurrentServerEntry() != null
                ? client.getCurrentServerEntry().address
                : (client.isInSingleplayer() ? "Singleplayer" : "Main Menu");
        return String.format(Locale.US, "Using %s Client | %s", Gfm_recodeClient.NAME, worldInfo);
    }

    private static String buildState(DiscordPlaying module) {
        if (Gfm_recodeClient.modules == null) {
            return "0 von 0 Modulen aktiv";
        }

        int totalModules = 0;
        int enabledModules = 0;

        for (Module current : Gfm_recodeClient.modules.getAll()) {
            if (current == null || current == module) {
                continue;
            }
            totalModules++;
            if (current.isEnabled()) {
                enabledModules++;
            }
        }

        return enabledModules + " von " + totalModules + " Modulen aktiv";
    }

    private static boolean isValidApplicationId(String applicationId) {
        if (applicationId.isEmpty()) {
            return false;
        }
        for (int i = 0; i < applicationId.length(); i++) {
            if (!Character.isDigit(applicationId.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
