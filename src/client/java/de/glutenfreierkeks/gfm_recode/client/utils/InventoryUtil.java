package de.glutenfreierkeks.gfm_recode.client.utils;

import net.minecraft.client.MinecraftClient;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static int getCurrentSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;
        return mc.player.getInventory().getSelectedSlot();
    }

    public static void switchSlot(int slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.player.getInventory().setSelectedSlot(slot);
    }

    public static void restoreSlot(int slot) {
        if (slot < 0) return;
        switchSlot(slot);
    }

    public static int findBlockInHotbar() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            net.minecraft.item.ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.BlockItem) return i;
        }
        return -1;
    }
}
