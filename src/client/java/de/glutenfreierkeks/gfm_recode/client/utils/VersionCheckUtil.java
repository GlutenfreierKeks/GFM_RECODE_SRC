package de.glutenfreierkeks.gfm_recode.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class VersionCheckUtil {
    private VersionCheckUtil() {
    }

    public static void sendChatMessage(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(message), false);
        }
    }
}
