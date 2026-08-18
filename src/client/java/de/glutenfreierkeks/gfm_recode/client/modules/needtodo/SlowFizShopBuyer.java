package de.glutenfreierkeks.gfm_recode.client.modules.needtodo;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.BoolSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.IntSliderSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.InventorySlotSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.List;

public class SlowFizShopBuyer extends Module {

    private static final String TARGET_IP = "slowfiz.net";

    public enum BuyAmount {
        TWO(2, 15), ELEVEN(11, 16), SIXTY_FOUR(64, 17);
        private final int value;
        private final int slot;
        BuyAmount(int value, int slot) { this.value = value; this.slot = slot; }
        public int getValue() { return value; }
        public int getSlot() { return slot; }
        @Override public String toString() { return String.valueOf(value); }
    }

    // ═══════════════════════════════════════════════════════════════
    //  SETTINGS
    // ═══════════════════════════════════════════════════════════════
    private final InventorySlotSetting categorySlots = register(new InventorySlotSetting("CategorySlots", "Select shop categories to buy", InventorySlotSetting.Layout.CHEST_9x3, false));
    private final InventorySlotSetting itemSlots = register(new InventorySlotSetting("ItemSlots", "Select items to buy", InventorySlotSetting.Layout.CHEST_9x3, true));

    private final IntSliderSetting totalAmount = register(new IntSliderSetting("Amount", "Total amount to buy", 64, 1, 64));
    private final IntSliderSetting clickCooldown = register(new IntSliderSetting("ClickCooldown", "Delay between clicks in ms", 50, 10, 500));
    private final BoolSetting autoStock = register(new BoolSetting("AutoStock", "Automatically reopen shop", true));

    // ═══════════════════════════════════════════════════════════════
    //  STATE
    // ═══════════════════════════════════════════════════════════════
    private static final long GUI_DELAY = 250;
    private long lastClick = 0L;
    private boolean shopOpened = false;
    private boolean clickedCategory = false;
    private boolean clickedItem = false;
    private boolean clickedAmount = false;
    private boolean buying = false;
    private ItemStack targetItem = null;

    private int currentCategoryIndex = 0;
    private int currentItemIndex = 0;
    private int remainingAmount = 0;
    private List<Integer> lastCategory = null;

    public SlowFizShopBuyer() {
        super("SlowFizShopBuyer", "Automatically buys items from the shop on slowfiz.net", Category.MISC);

        categorySlots.addSuggestion(11, new ItemStack(Items.REDSTONE), "Redstone");
        categorySlots.addSuggestion(12, new ItemStack(Items.DIAMOND_CHESTPLATE), "Armor");
        categorySlots.addSuggestion(13, new ItemStack(Items.NETHERITE_SWORD), "Gear");
        categorySlots.addSuggestion(14, new ItemStack(Items.GRASS_BLOCK), "Blocks");
        categorySlots.addSuggestion(4, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), "Premium");

        // Hints/Placeholders for items (common ones across categories)
        itemSlots.addSuggestion(9, new ItemStack(Items.REDSTONE), "Redstone / Obsidian / Logs");
        itemSlots.addSuggestion(10, new ItemStack(Items.REPEATER), "Repeater / Crystal / Wool");
        itemSlots.addSuggestion(11, new ItemStack(Items.PISTON), "Piston / Anchor / Glass");
        itemSlots.addSuggestion(12, new ItemStack(Items.SLIME_BLOCK), "Slime / Glowstone / Sandstone");
        itemSlots.addSuggestion(13, new ItemStack(Items.TOTEM_OF_UNDYING), "Totem / Dispenser / Chestplate");
        itemSlots.addSuggestion(14, new ItemStack(Items.ENDER_PEARL), "Pearl / Dropper / Helmet");
        itemSlots.addSuggestion(15, new ItemStack(Items.EXPERIENCE_BOTTLE), "XP / Crafter / Shulker");
        itemSlots.addSuggestion(16, new ItemStack(Items.GOLDEN_APPLE), "Gapple / Hopper / Sculk");
        itemSlots.addSuggestion(17, new ItemStack(Items.OBSERVER), "Observer / E-Chest / Purpur");
    }

    private void updateItemSuggestions() {
        List<Integer> selected = categorySlots.getValue();
        if (selected.equals(lastCategory)) return;
        lastCategory = List.copyOf(selected);

        itemSlots.getSuggestions().clear();
        if (selected.isEmpty()) return;

        int cat = selected.get(0);
        switch (cat) {
            case 11 -> { // REDSTONE
                itemSlots.addSuggestion(9, new ItemStack(Items.REDSTONE), "Redstone");
                itemSlots.addSuggestion(10, new ItemStack(Items.REPEATER), "Repeater");
                itemSlots.addSuggestion(11, new ItemStack(Items.PISTON), "Piston");
                itemSlots.addSuggestion(12, new ItemStack(Items.SLIME_BLOCK), "Slime Block");
                itemSlots.addSuggestion(13, new ItemStack(Items.DISPENSER), "Dispenser");
                itemSlots.addSuggestion(14, new ItemStack(Items.DROPPER), "Dropper");
                itemSlots.addSuggestion(15, new ItemStack(Items.CRAFTER), "Crafter");
                itemSlots.addSuggestion(16, new ItemStack(Items.HOPPER), "Hopper");
                itemSlots.addSuggestion(17, new ItemStack(Items.OBSERVER), "Observer");
            }
            case 12 -> { // ARMOR
                itemSlots.addSuggestion(2, new ItemStack(Items.NETHERITE_BOOTS), "Neth Boots");
                itemSlots.addSuggestion(3, new ItemStack(Items.NETHERITE_LEGGINGS), "Neth Leggings");
                itemSlots.addSuggestion(4, new ItemStack(Items.NETHERITE_CHESTPLATE), "Neth Chestplate");
                itemSlots.addSuggestion(5, new ItemStack(Items.NETHERITE_HELMET), "Neth Helmet");
                itemSlots.addSuggestion(6, new ItemStack(Items.SHIELD), "Shield");
                itemSlots.addSuggestion(11, new ItemStack(Items.DIAMOND_BOOTS), "Dia Boots");
                itemSlots.addSuggestion(12, new ItemStack(Items.DIAMOND_LEGGINGS), "Dia Leggings");
                itemSlots.addSuggestion(13, new ItemStack(Items.DIAMOND_CHESTPLATE), "Dia Chestplate");
                itemSlots.addSuggestion(14, new ItemStack(Items.DIAMOND_HELMET), "Dia Helmet");
                itemSlots.addSuggestion(15, new ItemStack(Items.SHULKER_BOX), "Shulker");
            }
            case 13 -> { // GEAR
                itemSlots.addSuggestion(9, new ItemStack(Items.OBSIDIAN), "Obsidian");
                itemSlots.addSuggestion(10, new ItemStack(Items.END_CRYSTAL), "Crystal");
                itemSlots.addSuggestion(11, new ItemStack(Items.RESPAWN_ANCHOR), "Anchor");
                itemSlots.addSuggestion(12, new ItemStack(Items.GLOWSTONE), "Glowstone");
                itemSlots.addSuggestion(13, new ItemStack(Items.TOTEM_OF_UNDYING), "Totem");
                itemSlots.addSuggestion(14, new ItemStack(Items.ENDER_PEARL), "E-Pearl");
                itemSlots.addSuggestion(15, new ItemStack(Items.EXPERIENCE_BOTTLE), "XP Bottle");
                itemSlots.addSuggestion(16, new ItemStack(Items.GOLDEN_APPLE), "Gapple");
                itemSlots.addSuggestion(17, new ItemStack(Items.ENDER_CHEST), "E-Chest");
            }
            case 14 -> { // BLOCKS
                itemSlots.addSuggestion(9, new ItemStack(Items.OAK_LOG), "Oak Logs");
                itemSlots.addSuggestion(10, new ItemStack(Items.WHITE_WOOL), "Wool");
                itemSlots.addSuggestion(11, new ItemStack(Items.GLASS), "Glass");
                itemSlots.addSuggestion(12, new ItemStack(Items.SANDSTONE), "Sandstone");
                itemSlots.addSuggestion(13, new ItemStack(Items.STONE_BRICKS), "Stone Bricks");
                itemSlots.addSuggestion(14, new ItemStack(Items.POLISHED_GRANITE), "Granite");
                itemSlots.addSuggestion(15, new ItemStack(Items.TUFF_BRICKS), "Tuff Bricks");
                itemSlots.addSuggestion(16, new ItemStack(Items.SCULK), "Sculk");
                itemSlots.addSuggestion(17, new ItemStack(Items.PURPUR_BLOCK), "Purpur");
            }
            case 4 -> { // PREMIUM
                itemSlots.addSuggestion(12, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), "E-Gap");
                itemSlots.addSuggestion(13, new ItemStack(Items.ELYTRA), "Elytra");
                itemSlots.addSuggestion(14, new ItemStack(Items.MACE), "Mace");
            }
        }
    }

    private int getAmountSlot(int amount) {
        if (amount >= 64) return 17;
        if (amount >= 11) return 16;
        return 15;
    }

    private int getAmountValue(int slot) {
        return switch (slot) {
            case 17 -> 64;
            case 16 -> 11;
            case 15 -> 2;
            default -> 0;
        };
    }

    private boolean isCorrectServer() {
        if (mc.getNetworkHandler() == null || mc.getNetworkHandler().getServerInfo() == null) return false;
        String address = mc.getNetworkHandler().getServerInfo().address;
        return address.toLowerCase().endsWith(TARGET_IP);
    }

    @Override
    public void onEnable() {
        if (!isCorrectServer()) {
            msg("§cError: This module only works on §4" + TARGET_IP + "§c!");
            this.disable();
            return;
        }
        openShop();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {

    }

    private void openShop() {
        if (mc.player != null) {
            mc.player.networkHandler.sendChatCommand("shop");
            resetStates();
        }
    }

    private void resetStates() {
        shopOpened = true;
        clickedCategory = false;
        clickedItem = false;
        clickedAmount = false;
        buying = false;
        targetItem = null;
        currentCategoryIndex = 0;
        currentItemIndex = 0;
        remainingAmount = totalAmount.getValue();
        lastClick = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || !isCorrectServer()) return;

        updateItemSuggestions();

        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
            if (buying && autoStock.getValue()) {
                openShop();
            } else if (buying) {
                msg("§cShop closed. Toggle module to restart.");
                this.disable();
            }
            return;
        }

        var handler = screen.getScreenHandler();
        int syncId = handler.syncId;

        List<Integer> selectedCategories = categorySlots.getValue();
        if (selectedCategories.isEmpty()) {
            msg("§cError: No categories selected!");
            this.disable();
            return;
        }

        if (currentCategoryIndex >= selectedCategories.size()) {
            if (autoStock.getValue()) {
                currentCategoryIndex = 0;
                openShop();
            } else {
                this.disable();
            }
            return;
        }

        int catSlot = selectedCategories.get(currentCategoryIndex);

        if (shopOpened && !clickedCategory && System.currentTimeMillis() - lastClick > GUI_DELAY) {
            click(syncId, catSlot);
            clickedCategory = true;
            lastClick = System.currentTimeMillis();
            return;
        }

        if (clickedCategory && !clickedItem && System.currentTimeMillis() - lastClick > GUI_DELAY) {
            List<Integer> selectedItems = itemSlots.getValue();
            if (selectedItems.isEmpty()) {
                msg("§cError: No items selected!");
                currentCategoryIndex++;
                clickedCategory = false;
                return;
            }

            if (currentItemIndex < selectedItems.size()) {
                int itemSlot = selectedItems.get(currentItemIndex);
                try {
                    ItemStack stack = handler.slots.get(itemSlot).getStack();
                    if (!stack.isEmpty()) targetItem = stack.copy();
                } catch (Exception ignored) {}

                click(syncId, itemSlot);
                clickedItem = true;
                lastClick = System.currentTimeMillis();
                remainingAmount = totalAmount.getValue();
            } else {
                currentCategoryIndex++;
                currentItemIndex = 0;
                clickedCategory = false;
            }
            return;
        }

        if (clickedItem && !clickedAmount && System.currentTimeMillis() - lastClick > GUI_DELAY) {
            int slot = getAmountSlot(remainingAmount);
            click(syncId, slot);
            clickedAmount = true;
            buying = true;
            lastClick = System.currentTimeMillis();
            return;
        }

        if (buying && System.currentTimeMillis() - lastClick > clickCooldown.getValue()) {
            click(syncId, 23); // Buy button
            lastClick = System.currentTimeMillis();

            int bought = getAmountValue(getAmountSlot(remainingAmount));
            remainingAmount -= bought;

            dropMatchingItems();
            
            if (remainingAmount <= 0) {
                currentItemIndex++;
                clickedItem = false;
                clickedAmount = false;
                buying = false;
            } else {
                clickedAmount = false;
            }
        }
    }

    private void click(int syncId, int slot) {
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private void dropMatchingItems() {
        if (mc.player == null || targetItem == null) return;
        var handler = mc.player.currentScreenHandler;
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getItem() == targetItem.getItem()) {
                mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
            }
        }
    }

    private void msg(String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }
}
