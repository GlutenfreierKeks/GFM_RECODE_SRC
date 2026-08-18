package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventorySlotSetting extends Setting<List<Integer>> {

    public static class Suggestion {
        public final ItemStack stack;
        public final String name;

        public Suggestion(ItemStack stack, String name) {
            this.stack = stack;
            this.name = name;
        }
    }

    public enum Layout {
        CHEST_9x1(9, 1),
        CHEST_9x2(9, 2),
        CHEST_9x3(9, 3),
        CHEST_9x4(9, 4),
        CHEST_9x5(9, 5),
        CHEST_9x6(9, 6),
        PLAYER_INVENTORY(9, 4);

        public final int columns;
        public final int rows;

        Layout(int columns, int rows) {
            this.columns = columns;
            this.rows = rows;
        }

        public int getTotalSlots() {
            return columns * rows;
        }
    }

    private Layout layout;
    private final boolean multiSelect;
    private final Map<Integer, Suggestion> suggestions = new HashMap<>();

    public InventorySlotSetting(String name, String description, Layout layout, boolean multiSelect) {
        super(name, description, new ArrayList<>());
        this.layout = layout;
        this.multiSelect = multiSelect;
    }

    public void addSuggestion(int slot, ItemStack stack, String name) {
        suggestions.put(slot, new Suggestion(stack, name));
    }

    public Map<Integer, Suggestion> getSuggestions() {
        return suggestions;
    }

    public Layout getLayout() {
        return layout;
    }

    public void setLayout(Layout layout) {
        this.layout = layout;
        value.removeIf(slot -> slot >= layout.getTotalSlots());
    }

    public void cycleLayout() {
        Layout[] layouts = Layout.values();
        int next = (layout.ordinal() + 1) % layouts.length;
        setLayout(layouts[next]);
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public void click(int slot) {
        if (slot < 0 || slot >= layout.getTotalSlots()) return;

        if (multiSelect) {
            if (value.contains(slot)) {
                value.remove(Integer.valueOf(slot));
            } else {
                value.add(slot);
            }
        } else {
            value.clear();
            value.add(slot);
        }
    }

    public boolean isSelected(int slot) {
        return value.contains(slot);
    }

    @Override
    public SettingType getType() {
        return SettingType.INVENTORY;
    }
}
