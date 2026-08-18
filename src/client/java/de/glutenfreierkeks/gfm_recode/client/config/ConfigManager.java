package de.glutenfreierkeks.gfm_recode.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), Gfm_recodeClient.NAME);
    private static final File SOUND_DIR = new File(CONFIG_DIR, "sounds");
    private static final String DEFAULT_NAME = "config";
    private static final File CONFIG_FILE = new File(CONFIG_DIR, DEFAULT_NAME + ".json");

    private static String currentConfigName = DEFAULT_NAME;

    public static void loadConfig() {
        loadConfig(DEFAULT_NAME);
    }

    public static void loadConfig(String configName) {
        currentConfigName = normalizeName(configName);
        loadConfig(getConfigFile(currentConfigName));
    }

    public static void loadConfig(File file) {
        ensureConfigDir();
        if (!file.exists()) {
            if (file.equals(CONFIG_FILE)) saveConfig(DEFAULT_NAME);
            return;
        }

        currentConfigName = fileNameToConfigName(file.getName());

        try (FileReader reader = new FileReader(file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if (json.has("clientSettings")) {
                JsonObject clientSettingsObj = json.getAsJsonObject("clientSettings");
                if (clientSettingsObj.has("playNotificationSounds")) {
                    de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.playNotificationSounds = clientSettingsObj.get("playNotificationSounds").getAsBoolean();
                }
                if (clientSettingsObj.has("soundVolume")) {
                    de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.soundVolume = clientSettingsObj.get("soundVolume").getAsDouble();
                }
                if (clientSettingsObj.has("toggleOnSound")) {
                    de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.toggleOnSound = clientSettingsObj.get("toggleOnSound").getAsString();
                }
                if (clientSettingsObj.has("toggleOffSound")) {
                    de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.toggleOffSound = clientSettingsObj.get("toggleOffSound").getAsString();
                }
                if (clientSettingsObj.has("notificationSound")) {
                    de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.notificationSound = clientSettingsObj.get("notificationSound").getAsString();
                }
                if (clientSettingsObj.has("theme")) {
                    de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.theme = clientSettingsObj.get("theme").getAsString();
                }
                if (clientSettingsObj.has("performanceMode")) {
                    de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.performanceMode = clientSettingsObj.get("performanceMode").getAsString();
                }
                if (clientSettingsObj.has("antiCheatProfile")) {
                    try {
                        de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.antiCheatProfile = 
                            de.glutenfreierkeks.gfm_recode.client.anticheat.AntiCheatProfileManager.Profile.valueOf(clientSettingsObj.get("antiCheatProfile").getAsString().toUpperCase());
                        de.glutenfreierkeks.gfm_recode.client.anticheat.AntiCheatProfileManager.applyProfile(de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.antiCheatProfile);
                    } catch (Exception ignored) {}
                }
            }

            if (json.has("friends")) {
                JsonObject friendsObj = json.getAsJsonObject("friends");
                if (friendsObj.has("highlightFriends")) {
                    de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.highlightFriends = friendsObj.get("highlightFriends").getAsBoolean();
                }
                if (friendsObj.has("friendColor")) {
                    de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.friendColor = friendsObj.get("friendColor").getAsString();
                }
                if (friendsObj.has("list")) {
                    de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.friends.clear();
                    friendsObj.getAsJsonArray("list").forEach(element -> {
                        de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.addFriend(element.getAsString());
                    });
                }
            }

            if (!json.has("modules")) return;

            JsonObject modulesObj = json.getAsJsonObject("modules");
            for (Module module : Gfm_recodeClient.modules.getAll()) {
                if (!modulesObj.has(module.name)) continue;

                JsonObject moduleObj = modulesObj.getAsJsonObject(module.name);

                if (moduleObj.has("enabled")) {
                    module.setEnabled(moduleObj.get("enabled").getAsBoolean());
                }

                if (module.getKeybindSetting() != null && moduleObj.has("keybind")) {
                    module.getKeybindSetting().setValue(moduleObj.get("keybind").getAsInt());
                }

                if (moduleObj.has("keybindType")) {
                    String type = moduleObj.get("keybindType").getAsString();
                    module.setKeybindType("HOLD".equals(type) ? Module.KeybindType.HOLD : Module.KeybindType.TOGGLE);
                }

                if (!moduleObj.has("settings")) continue;

                JsonObject settingsObj = moduleObj.getAsJsonObject("settings");
                for (Setting<?> setting : module.getSettings()) {
                    if (setting == module.getKeybindSetting() || !settingsObj.has(setting.name)) continue;

                    try {
                        switch (setting.getType()) {
                            case BOOL -> ((Setting<Boolean>) setting).setValue(settingsObj.get(setting.name).getAsBoolean());
                            case SLIDER_INT -> ((Setting<Integer>) setting).setValue(settingsObj.get(setting.name).getAsInt());
                            case SLIDER_DOUBLE -> ((Setting<Double>) setting).setValue(settingsObj.get(setting.name).getAsDouble());
                            case STRING -> ((Setting<String>) setting).setValue(settingsObj.get(setting.name).getAsString());
                            case ENUM -> {
                                Setting<Enum<?>> enumSetting = (Setting<Enum<?>>) setting;
                                String enumName = settingsObj.get(setting.name).getAsString();
                                Enum<?>[] enumConstants = enumSetting.getValue().getDeclaringClass().getEnumConstants();
                                for (Enum<?> e : enumConstants) {
                                    if (e.name().equals(enumName)) {
                                        ((Setting<Enum>) setting).setValue(e);
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Gfm_recodeClient.LOG.error("Failed to load setting " + setting.name + " for module " + module.name, e);
                    }
                }
            }
        } catch (IOException e) {
            Gfm_recodeClient.LOG.error("Failed to load config", e);
        }
    }

    public static void saveConfig() {
        saveConfig(currentConfigName);
    }

    public static void saveConfig(String configName) {
        currentConfigName = normalizeName(configName);
        saveConfig(getConfigFile(currentConfigName));
    }

    public static void saveConfig(File file) {
        ensureConfigDir();

        JsonObject json = new JsonObject();
        JsonObject modulesObj = new JsonObject();

        for (Module module : Gfm_recodeClient.modules.getAll()) {
            JsonObject moduleObj = new JsonObject();
            moduleObj.addProperty("enabled", module.isEnabled());
            if (module.getKeybindSetting() != null) {
                moduleObj.addProperty("keybind", module.getKeybindSetting().getValue());
            }
            moduleObj.addProperty("keybindType", module.getKeybindType().name());

            JsonObject settingsObj = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                if (setting == module.getKeybindSetting()) continue;

                switch (setting.getType()) {
                    case BOOL -> settingsObj.addProperty(setting.name, ((Setting<Boolean>) setting).getValue());
                    case SLIDER_INT -> settingsObj.addProperty(setting.name, ((Setting<Integer>) setting).getValue());
                    case SLIDER_DOUBLE -> settingsObj.addProperty(setting.name, ((Setting<Double>) setting).getValue());
                    case STRING -> settingsObj.addProperty(setting.name, ((Setting<String>) setting).getValue());
                    case ENUM -> settingsObj.addProperty(setting.name, ((Setting<Enum<?>>) setting).getValue().name());
                }
            }
            moduleObj.add("settings", settingsObj);
            modulesObj.add(module.name, moduleObj);
        }

        json.add("modules", modulesObj);

        JsonObject clientSettingsObj = new JsonObject();
        clientSettingsObj.addProperty("playNotificationSounds", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.playNotificationSounds);
        clientSettingsObj.addProperty("soundVolume", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.soundVolume);
        clientSettingsObj.addProperty("toggleOnSound", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.toggleOnSound);
        clientSettingsObj.addProperty("toggleOffSound", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.toggleOffSound);
        clientSettingsObj.addProperty("notificationSound", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.notificationSound);
        clientSettingsObj.addProperty("theme", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.theme);
        clientSettingsObj.addProperty("performanceMode", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.performanceMode);
        clientSettingsObj.addProperty("antiCheatProfile", de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.antiCheatProfile.name());
        json.add("clientSettings", clientSettingsObj);

        JsonObject friendsObj = new JsonObject();
        friendsObj.addProperty("highlightFriends", de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.highlightFriends);
        friendsObj.addProperty("friendColor", de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.friendColor);
        com.google.gson.JsonArray friendsList = new com.google.gson.JsonArray();
        de.glutenfreierkeks.gfm_recode.client.utils.FriendManager.friends.forEach(friendsList::add);
        friendsObj.add("list", friendsList);
        json.add("friends", friendsObj);

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(json, writer);
            currentConfigName = fileNameToConfigName(file.getName());
        } catch (IOException e) {
            Gfm_recodeClient.LOG.error("Failed to save config", e);
        }
    }

    public static List<String> listConfigs() {
        ensureConfigDir();
        File[] files = CONFIG_DIR.listFiles((dir, name) -> name.endsWith(".json"));
        List<String> names = new ArrayList<>();
        if (files == null) return names;

        for (File file : files) {
            names.add(fileNameToConfigName(file.getName()));
        }

        names.sort(Comparator.naturalOrder());
        return names;
    }

    public static boolean deleteConfig(String configName) {
        String normalized = normalizeName(configName);
        File file = getConfigFile(normalized);
        if (!file.exists() || normalized.equals(DEFAULT_NAME)) return false;

        boolean deleted = file.delete();
        if (deleted && normalized.equals(currentConfigName)) {
            currentConfigName = DEFAULT_NAME;
            loadConfig(DEFAULT_NAME);
        }
        return deleted;
    }

    public static String exportConfigWithDialog(String configName) {
        ensureConfigDir();
        File sourceFile = getConfigFile(configName);
        if (!sourceFile.exists()) return null;

        FileDialog dialog = new FileDialog((Frame) null, "Export Config", FileDialog.SAVE);
        dialog.setDirectory(CONFIG_DIR.getAbsolutePath());
        dialog.setFile(sourceFile.getName());
        dialog.setFilenameFilter((dir, name) -> name.endsWith(".json"));
        dialog.setVisible(true);

        String file = dialog.getFile();
        String dir = dialog.getDirectory();
        dialog.dispose();
        if (file == null || dir == null) return null;

        try {
            Files.copy(sourceFile.toPath(), new File(dir, file).toPath(), StandardCopyOption.REPLACE_EXISTING);
            return new File(dir, file).getAbsolutePath();
        } catch (IOException e) {
            Gfm_recodeClient.LOG.error("Failed to export config", e);
            return null;
        }
    }

    public static String importConfigWithDialog() {
        ensureConfigDir();
        FileDialog dialog = new FileDialog((Frame) null, "Import Config", FileDialog.LOAD);
        dialog.setDirectory(CONFIG_DIR.getAbsolutePath());
        dialog.setFile("*.json");
        dialog.setFilenameFilter((dir, name) -> name.endsWith(".json"));
        dialog.setVisible(true);

        String file = dialog.getFile();
        String dir = dialog.getDirectory();
        dialog.dispose();
        if (file == null || dir == null) return null;

        File sourceFile = new File(dir, file);
        String importedName = fileNameToConfigName(sourceFile.getName());
        File targetFile = getConfigFile(importedName);

        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return importedName;
        } catch (IOException e) {
            Gfm_recodeClient.LOG.error("Failed to import config", e);
            return null;
        }
    }

    public static File getConfigFile(String configName) {
        return new File(CONFIG_DIR, normalizeName(configName) + ".json");
    }

    public static String getCurrentConfigName() {
        return currentConfigName;
    }

    public static File getConfigDirectory() {
        ensureConfigDir();
        return CONFIG_DIR;
    }

    public static File getSoundDirectory() {
        ensureConfigDir();
        if (!SOUND_DIR.exists()) SOUND_DIR.mkdirs();
        return SOUND_DIR;
    }

    public static File getSoundFile(String fileName) {
        return new File(getSoundDirectory(), normalizeSoundName(fileName));
    }

    public static List<String> listSounds() {
        File[] files = getSoundDirectory().listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".wav") || lower.endsWith(".mp3");
        });
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) names.add(file.getName());
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private static void ensureConfigDir() {
        if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();
    }

    private static String normalizeSoundName(String fileName) {
        String name = fileName == null ? "" : new File(fileName).getName().trim();
        if (name.isEmpty()) name = "sound.wav";
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        String lower = name.toLowerCase();
        if (!lower.endsWith(".wav") && !lower.endsWith(".mp3")) name += ".wav";
        return name;
    }

    private static String normalizeName(String configName) {
        String trimmed = configName == null ? "" : configName.trim();
        if (trimmed.isEmpty()) return DEFAULT_NAME;
        if (trimmed.endsWith(".json")) trimmed = trimmed.substring(0, trimmed.length() - 5);
        return trimmed.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String fileNameToConfigName(String fileName) {
        return fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }
}
