package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * A setting that lets the player choose multiple Minecraft {@link Item}s.
 */
public class ItemSetting extends Setting<List<Item>> {

    private final List<Item> choices;

    public ItemSetting(String name, String description, Item defaultValue, Item... items) {
        super(name, description, new ArrayList<>(Arrays.asList(defaultValue)));
        this.choices = new ArrayList<>();
        this.choices.add(defaultValue);
        this.choices.addAll(Arrays.asList(items));
    }

    public ItemSetting(String name, String description, Item defaultValue) {
        super(name, description, new ArrayList<>(Arrays.asList(defaultValue)));
        this.choices = new ArrayList<>(Registries.ITEM.stream().toList());
    }

    public List<Item> getChoices() {
        return choices;
    }

    public void toggle(Item item) {
        if (getValue().contains(item)) {
            getValue().remove(item);
        } else {
            getValue().add(item);
        }
    }

    public boolean contains(Item item) {
        return getValue().contains(item);
    }

    @Override
    public SettingType getType() {
        return SettingType.ITEM;
    }
}
