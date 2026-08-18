package de.glutenfreierkeks.gfm_recode.client.gui.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.anticheat.AntiCheatProfileManager;
import de.glutenfreierkeks.gfm_recode.client.utils.NowPlayingPoller;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.item.Items;
import net.minecraft.resource.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class WebUiServer {
    private static HttpServer server;
    private static final Gson gson = new Gson();

    private static final List<NotificationMessage> pendingNotifications = new ArrayList<>();

    public static int currentPort = 1337;

    public static void start() {
        for (int port = 1337; port <= 1347; port++) {
            try {
                server = HttpServer.create(new InetSocketAddress(port), 0);
                server.createContext("/", new StaticHandler("/assets/gfm_recode/gui/index.html"));
                server.createContext("/hud", new StaticHandler("/assets/gfm_recode/gui/hud.html"));
                server.createContext("/api/modules", new ModulesHandler());
                server.createContext("/api/toggle", new ToggleHandler());
                server.createContext("/api/set-setting", new SettingUpdateHandler());
                server.createContext("/api/notifications", new NotificationsHandler());
                server.createContext("/api/hud-settings", new HudSettingsHandler());
                server.createContext("/api/item-icon", new ItemIconHandler());
                
                // Centralized Rework Endpoints
                server.createContext("/api/settings", new SettingsHandler());
                server.createContext("/api/settings/update", new SettingsUpdateHandler2());
                server.createContext("/api/sounds", new SoundsListHandler());
                server.createContext("/api/sounds/import", new SoundImportHandler());
                server.createContext("/api/friends", new FriendsHandler());
                server.createContext("/api/friends/add", new FriendsAddHandler());
                server.createContext("/api/friends/remove", new FriendsRemoveHandler());
                server.createContext("/api/configs", new ConfigsListHandler());
                server.createContext("/api/configs/save", new ConfigSaveHandler());
                server.createContext("/api/configs/save-dialog", new ConfigSaveDialogHandler());
                server.createContext("/api/configs/export-content", new ConfigExportContentHandler());
                server.createContext("/api/configs/import-dialog", new ConfigImportDialogHandler());
                server.createContext("/api/configs/import-content", new ConfigImportContentHandler());
                server.createContext("/api/configs/load", new ConfigLoadHandler());
                server.createContext("/api/configs/rename", new ConfigRenameHandler());
                server.createContext("/api/configs/delete", new ConfigDeleteHandler());

                server.createContext("/now-playing-status", new NowPlayingHandler());
                server.createContext("/spotify-status", new NowPlayingHandler());

                server.setExecutor(null);
                server.start();
                currentPort = port;
                Gfm_recodeClient.LOG.info("WebUI Server started on port " + port);
                return;
            } catch (Exception e) {
                if (port == 1347) {
                    Gfm_recodeClient.LOG.error("Failed to start WebUI Server on any port in range 1337-1347", e);
                }
            }
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    static class NowPlayingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = NowPlayingPoller.getCachedJson();
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    public static void sendNotification(String title, String message, String type, int durationMs) {
        synchronized (pendingNotifications) {
            pendingNotifications.add(new NotificationMessage(title, message, type, System.currentTimeMillis(), durationMs));
            // Keep only last 50 notifications
            while (pendingNotifications.size() > 50) {
                pendingNotifications.remove(0);
            }
        }
    }

    public static class NotificationMessage {
        public final String title;
        public final String message;
        public final String type;
        public final long timestamp;
        public final int duration;

        public NotificationMessage(String title, String message, String type, long timestamp, int duration) {
            this.title = title;
            this.message = message;
            this.type = type;
            this.timestamp = timestamp;
            this.duration = duration;
        }
    }

    static class NotificationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Return pending notifications and clear them
            List<NotificationMessage> notifications = new ArrayList<>();
            synchronized (pendingNotifications) {
                notifications.addAll(pendingNotifications);
                pendingNotifications.clear();
            }

            String response = gson.toJson(notifications);
            exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class HudSettingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            if ("POST".equals(method) || "PUT".equals(method)) {
                // Update HUD settings
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    java.util.Map<String, Object> settings = gson.fromJson(body, java.util.Map.class);

                    // Apply settings to HUD module
                    if (Gfm_recodeClient.modules != null) {
                        de.glutenfreierkeks.gfm_recode.client.modules.render.Hud hud =
                            (de.glutenfreierkeks.gfm_recode.client.modules.render.Hud)
                            Gfm_recodeClient.modules.getByName("HUD");
                        if (hud != null) {
                            hud.applyWebSettings(settings);
                        }
                    }

                    String response = "{\"status\":\"ok\"}";
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                } catch (Exception e) {
                    String error = "{\"error\":\"" + e.getMessage() + "\"}";
                    exchange.sendResponseHeaders(500, error.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(error.getBytes());
                    }
                }
            } else {
                // Get current HUD settings
                java.util.Map<String, Object> settings = new java.util.HashMap<>();
                if (Gfm_recodeClient.modules != null) {
                    de.glutenfreierkeks.gfm_recode.client.modules.render.Hud hud =
                        (de.glutenfreierkeks.gfm_recode.client.modules.render.Hud)
                        Gfm_recodeClient.modules.getByName("HUD");
                    if (hud != null) {
                        settings = hud.getWebSettings();
                    }
                }

                String response = gson.toJson(settings);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    static class StaticHandler implements HttpHandler {
        private final String resourcePath;
        StaticHandler(String path) { this.resourcePath = path; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            InputStream is = Gfm_recodeClient.class.getResourceAsStream(resourcePath);
            if (is == null) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }
            byte[] bytes = is.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("Expires", "0");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class ItemIconHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String query = exchange.getRequestURI().getQuery();
            String itemId = "minecraft:barrier";
            if (query != null) {
                for (String part : query.split("&")) {
                    if (part.startsWith("id=")) {
                        itemId = java.net.URLDecoder.decode(part.substring(3), "UTF-8");
                        break;
                    }
                }
            }

            Identifier textureId = resolveTexture(itemId);
            if (textureId == null) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                exchange.sendResponseHeaders(503, 0);
                exchange.close();
                return;
            }

            Optional<Resource> resource = client.getResourceManager().getResource(textureId);
            if (resource.isEmpty()) {
                exchange.sendResponseHeaders(404, 0);
                exchange.close();
                return;
            }

            byte[] bytes;
            try (InputStream in = resource.get().getInputStream()) {
                bytes = in.readAllBytes();
            }

            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private Identifier resolveTexture(String itemId) {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null) {
                return null;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null) {
                return null;
            }

            Identifier[] candidates = new Identifier[] {
                    Identifier.of(id.getNamespace(), "textures/item/" + id.getPath() + ".png"),
                    Identifier.of(id.getNamespace(), "textures/block/" + id.getPath() + ".png")
            };

            for (Identifier candidate : candidates) {
                if (client.getResourceManager().getResource(candidate).isPresent()) {
                    return candidate;
                }
            }

            Identifier fallback = Identifier.of("minecraft", "textures/item/barrier.png");
            return client.getResourceManager().getResource(fallback).isPresent() ? fallback : null;
        }
    }

    static class ModulesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                List<ModuleData> data = new ArrayList<>();
                if (Gfm_recodeClient.modules != null) {
                    for (Module m : Gfm_recodeClient.modules.getAll()) {
                        data.add(new ModuleData(m));
                    }
                }
                String json = gson.toJson(data);
                byte[] response = json.getBytes("UTF-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                Gfm_recodeClient.LOG.error("API Error in ModulesHandler", e);
            }
        }
    }

    static class ToggleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("=")) {
                String name = java.net.URLDecoder.decode(query.split("=")[1], "UTF-8");
                Module m = Gfm_recodeClient.modules.getByName(name);
                if (m != null) m.toggle();
            }
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        }
    }

    static class SettingUpdateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String query = exchange.getRequestURI().getQuery();
                if (query != null) {
                    String[] parts = query.split("&");
                    String modName = java.net.URLDecoder.decode(parts[0].split("=")[1], "UTF-8");
                    String setName = java.net.URLDecoder.decode(parts[1].split("=")[1], "UTF-8");
                    String value = java.net.URLDecoder.decode(parts[2].split("=")[1], "UTF-8");
                    Module m = Gfm_recodeClient.modules.getByName(modName);
                    if (m == null) return;
                    Setting<?> s = null;
                    for (Setting<?> setting : m.getSettings()) {
                        if (setting.name.equals(setName)) {
                            s = setting;
                            break;
                        }
                    }
                    if (s == null) {
                        String error = "{\"error\":\"Setting not found\"}";
                        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(404, error.getBytes().length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(error.getBytes());
                        }
                        return;
                    }
                    // If value is JSON array, parse it
                    if (value.startsWith("[") && value.endsWith("]")) {
                        try {
                            List<String> list = gson.fromJson(value, List.class);
                            updateSetting(s, list);
                        } catch (Exception e) {
                            updateSetting(s, value);
                        }
                    } else {
                        updateSetting(s, value);
                    }

                    // Save config after setting update
                    de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.saveConfig();
                }
                String response = "{\"status\":\"ok\"}";
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } catch (Exception e) {
                Gfm_recodeClient.LOG.error("SettingUpdate Error", e);
                String error = "{\"error\":\"" + e.getMessage() + "\"}";
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(500, error.getBytes().length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(error.getBytes());
                    }
                } catch (IOException ioe) {
                    // Ignore
                }
            } finally {
                exchange.close();
            }
        }

        private void updateSetting(Setting<?> s, Object val) {
            if (s instanceof BoolSetting) {
                ((BoolSetting)s).setValue(Boolean.parseBoolean((String) val));
            } else if (s instanceof IntSliderSetting) {
                ((IntSliderSetting)s).setValue((int) Double.parseDouble((String) val));
            } else if (s instanceof DoubleSliderSetting) {
                ((DoubleSliderSetting)s).setValue(Double.parseDouble((String) val));
            } else if (s instanceof StringSetting) {
                ((StringSetting)s).setValue((String) val);
            } else if (s instanceof ColorSetting cs) {
                String valStr = (String) val;
                if (valStr.startsWith("#")) {
                    int r = Integer.valueOf(valStr.substring(1, 3), 16);
                    int g = Integer.valueOf(valStr.substring(3, 5), 16);
                    int b = Integer.valueOf(valStr.substring(5, 7), 16);
                    int a = valStr.length() > 7 ? Integer.valueOf(valStr.substring(7, 9), 16) : 255;
                    cs.setRGB(r, g, b, a);
                }
            } else if (s instanceof EnumSetting es) {
                String valStr = (String) val;
                for (Object o : es.getValues()) {
                    if (o.toString().trim().equalsIgnoreCase(valStr.trim())) {
                        es.setValue(o);
                        break;
                    }
                }
            } else if (s instanceof KeybindSetting ks) {
                try {
                    ks.setValue(Integer.parseInt((String) val));
                } catch (Exception e) {
                    Gfm_recodeClient.LOG.warn("Failed to set keybind: " + val);
                }
            } else if (s instanceof ItemSetting is) {
                if (val instanceof List<?> list) {
                    List<Item> items = new ArrayList<>();
                    for (Object id : list) {
                        if (id instanceof String) {
                            String idStr = (String) id;
                            Identifier itemId;
                            if (idStr.contains(":")) {
                                String[] parts2 = idStr.split(":", 2);
                                itemId = Identifier.of(parts2[0], parts2[1]);
                            } else {
                                itemId = Identifier.of("minecraft", idStr);
                            }
                            Item item = Registries.ITEM.get(itemId);
                            if (item != Items.AIR) {
                                items.add(item);
                            }
                        }
                    }
                    is.setValue(items);
                }
            }
        }
    }

    static class ModuleData {
        String name;
        String category;
        boolean enabled;
        boolean blocked;
        String blockReason;
        String displayInfo;
        List<SettingData> settings = new ArrayList<>();

        ModuleData(Module m) {
            this.name = m.name;
            this.category = m.category != null ? m.category.name() : "MISC";
            this.enabled = m.isEnabled();
            this.displayInfo = m.getDisplayInfo();
            this.blockReason = AntiCheatProfileManager.getBlockReason(m, AntiCheatProfileManager.getCurrentProfile());
            this.blocked = this.blockReason != null;
            for (Setting<?> s : m.getSettings()) {
                if (s != null) settings.add(new SettingData(s));
            }
        }
    }

    static class SettingData {
        String name;
        String type;
        Object value;
        Object min;
        Object max;
        List<String> options;

        SettingData(Setting<?> s) {
            this.name = s.name;
            Object raw = s.getValue();
            if (raw instanceof Optional<?>) raw = ((Optional<?>) raw).orElse(null);

            if (s instanceof BoolSetting) { type = "bool"; value = raw; }
            else if (s instanceof IntSliderSetting ss) { type = "slider"; value = raw; min = ss.getMin(); max = ss.getMax(); }
            else if (s instanceof DoubleSliderSetting ds) { type = "slider_double"; value = raw; min = ds.getMin(); max = ds.getMax(); }
            else if (s instanceof StringSetting) { type = "string"; value = raw; }
            else if (s instanceof ColorSetting cs) { type = "color"; value = String.format("#%02x%02x%02x", cs.getR(), cs.getG(), cs.getB()); }
            else if (s instanceof EnumSetting es) {
                type = "enum";
                options = new ArrayList<>();
                for (Object o : es.getValues()) options.add(o.toString());
                value = raw != null ? raw.toString() : "";
            } else if (s instanceof KeybindSetting ks) {
                type = "keybind";
                value = ks.getKeyName();
                min = ks.getValue(); // Store raw keycode in 'min' for JS
            } else if (s instanceof ItemSetting is) {
                type = "item_list";
                value = ((List<Item>) raw).stream().map(i -> Registries.ITEM.getId(i).toString()).collect(Collectors.toList());
                options = Registries.ITEM.stream().map(i -> Registries.ITEM.getId(i).toString()).collect(Collectors.toList());
            } else if (s instanceof InventorySlotSetting iss) {
                type = "slot_list";
                value = raw;
                // options could be slot names, but for now, just the layout
            } else {
                type = "unknown";
                value = String.valueOf(raw);
            }
        }
    }

    static class SettingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("playNotificationSounds", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.playNotificationSounds);
            data.put("soundVolume", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.soundVolume);
            data.put("toggleOnSound", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.toggleOnSound);
            data.put("toggleOffSound", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.toggleOffSound);
            data.put("notificationSound", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.notificationSound);
            data.put("theme", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.theme);
            data.put("performanceMode", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.performanceMode);
            data.put("antiCheatProfile", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.antiCheatProfile.name());
            data.put("highlightFriends", de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.highlightFriends);
            data.put("friendColor", de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.friendColor);
            
            String response = gson.toJson(data);
            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes("UTF-8"));
            }
        }
    }

    static class SettingsUpdateHandler2 implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    String[] pair = part.split("=", 2);
                    if (pair.length == 2) {
                        String key = java.net.URLDecoder.decode(pair[0], "UTF-8");
                        String val = java.net.URLDecoder.decode(pair[1], "UTF-8");
                        if ("playNotificationSounds".equals(key)) {
                            de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.playNotificationSounds = Boolean.parseBoolean(val);
                        } else if ("soundVolume".equals(key)) {
                            de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.soundVolume = Double.parseDouble(val);
                        } else if ("toggleOnSound".equals(key)) {
                            de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.toggleOnSound = sanitizeSoundChoice(val);
                        } else if ("toggleOffSound".equals(key)) {
                            de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.toggleOffSound = sanitizeSoundChoice(val);
                        } else if ("notificationSound".equals(key)) {
                            de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.notificationSound = sanitizeSoundChoice(val);
                        } else if ("theme".equals(key)) {
                            de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.theme = val;
                        } else if ("performanceMode".equals(key)) {
                            de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.performanceMode = val;
                        } else if ("highlightFriends".equals(key)) {
                            de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.highlightFriends = Boolean.parseBoolean(val);
                        } else if ("friendColor".equals(key)) {
                            de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.friendColor = val;
                        } else if ("antiCheatProfile".equals(key)) {
                            try {
                                de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.antiCheatProfile = 
                                    de.glutenfreierkeks.gfm_recode.client.anticheat.AntiCheatProfileManager.Profile.valueOf(val.toUpperCase());
                                de.glutenfreierkeks.gfm_recode.client.anticheat.AntiCheatProfileManager.applyProfile(de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.antiCheatProfile);
                            } catch (Exception ignored) {}
                        }
                    }
                }
                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.saveConfig();
            }
            String response = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }

        private String sanitizeSoundChoice(String name) {
            if (name == null || name.isBlank() || "default".equals(name)) return "default";
            String clean = new java.io.File(name).getName();
            String lower = clean.toLowerCase();
            return lower.endsWith(".wav") || lower.endsWith(".mp3") ? clean : "default";
        }
    }

    static class SoundsListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("sounds", de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.listSounds());
            String response = gson.toJson(data);
            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes("UTF-8"));
            }
        }
    }

    static class SoundImportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if (!"POST".equals(exchange.getRequestMethod())) {
                String error = "{\"error\":\"POST required\"}";
                exchange.sendResponseHeaders(405, error.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes());
                }
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String name = "custom.wav";
            if (query != null && query.contains("name=")) {
                name = java.net.URLDecoder.decode(query.split("name=")[1], "UTF-8");
            }
            String lowerName = name.toLowerCase();
            if (!lowerName.endsWith(".wav") && !lowerName.endsWith(".mp3")) {
                String error = "{\"error\":\"Only .wav and .mp3 files are supported\"}";
                exchange.sendResponseHeaders(400, error.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes());
                }
                return;
            }

            java.io.File target = de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getSoundFile(name);
            try {
                java.nio.file.Files.copy(exchange.getRequestBody(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                WebUiServer.sendNotification("Sounds", "Imported " + target.getName(), "success", 2500);
                String response = "{\"status\":\"ok\",\"name\":\"" + target.getName().replace("\"", "\\\"") + "\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes("UTF-8"));
                }
            } catch (Exception e) {
                WebUiServer.sendNotification("Sounds", "Failed to import sound", "alert", 2500);
                String error = "{\"error\":\"Failed to import sound\"}";
                exchange.sendResponseHeaders(500, error.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes());
                }
            }
        }
    }

    static class FriendsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            List<java.util.Map<String, Object>> friendsList = new ArrayList<>();
            for (String friendName : de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.friends) {
                java.util.Map<String, Object> friendMap = new java.util.HashMap<>();
                friendMap.put("name", friendName);
                friendMap.put("online", de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.isOnline(friendName));
                friendsList.add(friendMap);
            }
            String response = gson.toJson(friendsList);
            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes("UTF-8"));
            }
        }
    }

    static class FriendsAddHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("name=")) {
                String name = java.net.URLDecoder.decode(query.split("=")[1], "UTF-8").trim();
                de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.addFriend(name);
                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.saveConfig();
            }
            String response = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class FriendsRemoveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("name=")) {
                String name = java.net.URLDecoder.decode(query.split("=")[1], "UTF-8").trim();
                de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.removeFriend(name);
                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.saveConfig();
            }
            String response = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class ConfigsListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("current", de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getCurrentConfigName());
            data.put("configs", de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.listConfigs());
            String response = gson.toJson(data);
            exchange.sendResponseHeaders(200, response.getBytes("UTF-8").length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes("UTF-8"));
            }
        }
    }

    static class ConfigSaveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("name=")) {
                String name = java.net.URLDecoder.decode(query.split("=")[1], "UTF-8").trim();
                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.saveConfig(name);
                WebUiServer.sendNotification("Config", "Saved config: " + name, "success", 2500);
            } else {
                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.saveConfig();
                WebUiServer.sendNotification("Config", "Saved current config", "success", 2500);
            }
            String response = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class ConfigSaveDialogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                WebUiServer.sendNotification("Config", "Use the Web UI file picker or drag & drop to save configs.", "warning", 3500);
                String response = "{\"status\":\"headless\"}";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                new Thread(() -> {
                    try {
                        java.awt.FileDialog dialog = new java.awt.FileDialog((java.awt.Frame) null, "Save Config As", java.awt.FileDialog.SAVE);
                        dialog.setDirectory(de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getConfigDirectory().getAbsolutePath());
                        dialog.setFile("config.json");
                        dialog.setVisible(true);
                        String file = dialog.getFile();
                        String dir = dialog.getDirectory();
                        dialog.dispose();
                        if (file != null && dir != null) {
                            java.io.File target = new java.io.File(dir, file);
                            de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.saveConfig(target);
                            WebUiServer.sendNotification("Config", "Config saved as " + target.getName(), "success", 3000);
                        }
                    } catch (java.awt.HeadlessException e) {
                        WebUiServer.sendNotification("Config", "File dialogs are unavailable in this environment.", "warning", 3500);
                    }
                }).start();
            });
            String response = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class ConfigExportContentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.saveConfig();
            java.io.File current = de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getConfigFile(
                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getCurrentConfigName()
            );
            if (!current.exists()) {
                String error = "{\"error\":\"Config file not found\"}";
                exchange.sendResponseHeaders(404, error.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(error.getBytes());
                }
                return;
            }
            byte[] response = java.nio.file.Files.readAllBytes(current.toPath());
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    static class ConfigImportDialogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if (java.awt.GraphicsEnvironment.isHeadless()) {
                WebUiServer.sendNotification("Config", "Use the Web UI file picker or drag & drop to import configs.", "warning", 3500);
                String response = "{\"status\":\"headless\"}";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                new Thread(() -> {
                    try {
                        java.awt.FileDialog dialog = new java.awt.FileDialog((java.awt.Frame) null, "Import Config", java.awt.FileDialog.LOAD);
                        dialog.setDirectory(de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getConfigDirectory().getAbsolutePath());
                        dialog.setFile("*.json");
                        dialog.setVisible(true);
                        String file = dialog.getFile();
                        String dir = dialog.getDirectory();
                        dialog.dispose();
                        if (file != null && dir != null) {
                            java.io.File source = new java.io.File(dir, file);
                            java.io.File dest = de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getConfigFile(source.getName());
                            try {
                                java.nio.file.Files.copy(source.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.loadConfig(dest);
                                WebUiServer.sendNotification("Config", "Imported and loaded " + dest.getName(), "success", 3000);
                            } catch (Exception e) {
                                WebUiServer.sendNotification("Config", "Failed to import config", "alert", 3000);
                            }
                        }
                    } catch (java.awt.HeadlessException e) {
                        WebUiServer.sendNotification("Config", "File dialogs are unavailable in this environment.", "warning", 3500);
                    }
                }).start();
            });
            String response = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class ConfigImportContentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if ("POST".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String name = "imported_config.json";
                if (query != null && query.contains("name=")) {
                    name = java.net.URLDecoder.decode(query.split("name=")[1], "UTF-8");
                }
                if (!name.endsWith(".json")) name += ".json";
                String body = new String(exchange.getRequestBody().readAllBytes(), "UTF-8");
                java.io.File target = de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getConfigFile(name);
                try (java.io.FileWriter writer = new java.io.FileWriter(target)) {
                    writer.write(body);
                }
                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.loadConfig(target);
                WebUiServer.sendNotification("Config", "Imported via Drag & Drop: " + target.getName(), "success", 3000);
                
                String response = "{\"status\":\"ok\"}";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }

    static class ConfigLoadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("name=")) {
                String name = java.net.URLDecoder.decode(query.split("=")[1], "UTF-8").trim();
                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.loadConfig(name);
                WebUiServer.sendNotification("Config", "Loaded config: " + name, "success", 2500);
            }
            String response = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class ConfigRenameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                String oldName = null;
                String newName = null;
                for (String part : query.split("&")) {
                    String[] pair = part.split("=", 2);
                    if (pair.length == 2) {
                        String key = java.net.URLDecoder.decode(pair[0], "UTF-8");
                        String val = java.net.URLDecoder.decode(pair[1], "UTF-8").trim();
                        if ("old".equals(key)) oldName = val;
                        else if ("new".equals(key)) newName = val;
                    }
                }
                if (oldName != null && newName != null && !oldName.isEmpty() && !newName.isEmpty()) {
                    java.io.File oldFile = de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getConfigFile(oldName);
                    java.io.File newFile = de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getConfigFile(newName);
                    if (oldFile.exists() && !newFile.exists()) {
                        if (oldFile.renameTo(newFile)) {
                            if (oldName.equals(de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.getCurrentConfigName())) {
                                de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.loadConfig(newName);
                            }
                            WebUiServer.sendNotification("Config", "Renamed config to " + newName, "success", 2500);
                        } else {
                            WebUiServer.sendNotification("Config", "Failed to rename config", "alert", 2500);
                        }
                    }
                }
            }
            String response = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    static class ConfigDeleteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            String query = exchange.getRequestURI().getQuery();
            if (query != null && query.contains("name=")) {
                String name = java.net.URLDecoder.decode(query.split("=")[1], "UTF-8").trim();
                if (de.glutenfreierkeks.gfm_recode.client.config.ConfigManager.deleteConfig(name)) {
                    WebUiServer.sendNotification("Config", "Deleted config: " + name, "warning", 2500);
                } else {
                    WebUiServer.sendNotification("Config", "Cannot delete default/active config", "alert", 2500);
                }
            }
            String response = "{\"status\":\"ok\"}";
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
