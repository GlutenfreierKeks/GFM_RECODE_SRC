package de.glutenfreierkeks.gfm_recode.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import java.util.ArrayList;
import java.util.List;

public class FriendManager {
    public static final List<String> friends = new ArrayList<>();
    public static boolean highlightFriends = true;
    public static String friendColor = "#00FF00"; // Default green color

    public static boolean isFriend(String name) {
        if (name == null) return false;
        for (String friend : friends) {
            if (friend.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static void addFriend(String name) {
        if (name != null && !name.trim().isEmpty() && !isFriend(name)) {
            friends.add(name.trim());
        }
    }

    public static void removeFriend(String name) {
        if (name != null) {
            friends.removeIf(friend -> friend.equalsIgnoreCase(name));
        }
    }

    public static boolean isOnline(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return false;
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile().name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
