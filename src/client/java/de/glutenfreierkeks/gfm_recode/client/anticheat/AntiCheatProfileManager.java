package de.glutenfreierkeks.gfm_recode.client.anticheat;

import de.glutenfreierkeks.gfm_recode.client.Gfm_recodeClient;
import de.glutenfreierkeks.gfm_recode.client.gui.web.WebUiServer;
import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.DoubleSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;

import java.util.List;
import java.util.Locale;

public final class AntiCheatProfileManager {

    public enum Profile {
        VANILLA,
        GRIM,
        VULCAN
    }

    private AntiCheatProfileManager() {
    }

    public static Profile getCurrentProfile() {
        return de.glutenfreierkeks.gfm_recode.client.settings.ClientSettingsState.antiCheatProfile;
    }

    public static boolean isModuleAllowed(Module module, Profile profile) {
        return getBlockReason(module, profile) == null;
    }

    public static String getBlockReason(Module module, Profile profile) {
        if (module == null || profile == null || profile == Profile.VANILLA) {
            return null;
        }

        String name = module.name.toLowerCase(Locale.ROOT);
        if (profile == Profile.GRIM) {
            if (matches(name, "spinbot", "clutch", "cactustower")) {
                return "Blocked by Grim profile";
            }
        }

        if (profile == Profile.VULCAN) {
            if (matches(name, "saywallahi")) {
                return "Blocked by Vulcan profile";
            }
        }

        return null;
    }

    public static void applyProfile(Profile profile) {
        if (Gfm_recodeClient.modules == null || profile == null) {
            return;
        }

        for (Module module : Gfm_recodeClient.modules.getAll()) {
            String reason = getBlockReason(module, profile);
            if (reason != null && module.isEnabled()) {
                module.disable();
            }
        }

        tuneSettings(profile);
        WebUiServer.sendNotification("AntiCheat", "Profile set to " + profile.name(), "info", 2500);
    }

    private static void tuneSettings(Profile profile) {
        Module wTap = Gfm_recodeClient.modules.getByName("WTap");
        Module jumpReset = Gfm_recodeClient.modules.getByName("JumpReset");
        Module hitCrystal = Gfm_recodeClient.modules.getByName("HitCrystal");

        switch (profile) {
            case VANILLA -> {
                setDouble(wTap, "Release W ms", 55.0);
                setDouble(wTap, "WTap nach ms", 0.0);
                setDouble(jumpReset, "Chance", 100.0);
                setInt(jumpReset, "Min Delay", 0);
                setInt(jumpReset, "Max Delay", 2);
                setDouble(hitCrystal, "PostPlaceDelay", 35.0);
                setDouble(hitCrystal, "PostObiSnap", 3.4);
            }
            case GRIM -> {
                setDouble(wTap, "Release W ms", 43.28);
                setDouble(wTap, "WTap nach ms", 1.53);
                setDouble(jumpReset, "Chance", 100.0);
                setInt(jumpReset, "Min Delay", 1);
                setInt(jumpReset, "Max Delay", 2);
                setDouble(hitCrystal, "PostPlaceDelay", 35.0);
                setDouble(hitCrystal, "PostObiSnap", 3.4);

                Module killAura = Gfm_recodeClient.modules.getByName("KillAura");
                setDouble(killAura, "Range", 3.0);
                setBool(killAura, "Perfect Cooldown", true);

                Module aimAssist = Gfm_recodeClient.modules.getByName("AimAssist");
                setDouble(aimAssist, "Range", 44.96);
                setDouble(aimAssist, "Smoothing", 0.92);

                Module sprint = Gfm_recodeClient.modules.getByName("Sprint");
                setEnum(sprint, "Mode", "OMNI");
                setBool(sprint, "In Air", true);
            }
            case VULCAN -> {
                setDouble(wTap, "Release W ms", 95.0);
                setDouble(wTap, "WTap nach ms", 0.0);
                setDouble(jumpReset, "Chance", 88.0);
                setInt(jumpReset, "Min Delay", 1);
                setInt(jumpReset, "Max Delay", 3);
                setDouble(hitCrystal, "PostPlaceDelay", 55.0);
                setDouble(hitCrystal, "PostObiSnap", 2.4);
            }
        }
    }

    private static boolean matches(String name, String... values) {
        for (String value : values) {
            if (name.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static void setDouble(Module module, String settingName, double value) {
        Setting<?> setting = findSetting(module, settingName);
        if (setting instanceof DoubleSliderSetting slider) {
            slider.setValue(value);
        }
    }

    private static void setInt(Module module, String settingName, int value) {
        Setting<?> setting = findSetting(module, settingName);
        if (setting instanceof IntSliderSetting slider) {
            slider.setValue(value);
        }
    }

    public static void setBool(Module module, String settingName, boolean value) {
        Setting<?> setting = findSetting(module, settingName);
        if (setting instanceof BoolSetting boolSetting) {
            boolSetting.setValue(value);
        }
    }

    public static void setEnum(Module module, String settingName, String value) {
        Setting<?> setting = findSetting(module, settingName);
        if (setting instanceof EnumSetting<?> enumSetting) {
            Object[] values = enumSetting.getValues();
            for (Object candidate : values) {
                if (candidate.toString().equalsIgnoreCase(value)) {
                    ((EnumSetting) enumSetting).setValue((Enum) candidate);
                    break;
                }
            }
        }
    }

    private static Setting<?> findSetting(Module module, String settingName) {
        if (module == null || settingName == null) {
            return null;
        }
        List<Setting<?>> settings = module.getSettings();
        for (Setting<?> setting : settings) {
            if (setting.name.equalsIgnoreCase(settingName)) {
                return setting;
            }
        }
        return null;
    }
}
