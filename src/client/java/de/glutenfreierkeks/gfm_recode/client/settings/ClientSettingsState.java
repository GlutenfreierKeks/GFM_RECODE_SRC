package de.glutenfreierkeks.gfm_recode.client.settings;

import de.glutenfreierkeks.gfm_recode.client.anticheat.AntiCheatProfileManager;

public class ClientSettingsState {
    public static boolean playNotificationSounds = true;
    public static double soundVolume = 0.3;
    public static String toggleOnSound = "default";
    public static String toggleOffSound = "default";
    public static String notificationSound = "default";
    public static String theme = "KRYPTON";
    public static String performanceMode = "NORMAL"; // "LOW", "NORMAL", "ULTRA_FAST"
    public static AntiCheatProfileManager.Profile antiCheatProfile = AntiCheatProfileManager.Profile.VANILLA;
}
