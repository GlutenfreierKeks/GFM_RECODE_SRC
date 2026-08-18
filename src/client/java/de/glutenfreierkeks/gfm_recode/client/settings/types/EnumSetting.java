package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {

    private final E[] values;

    @SuppressWarnings("unchecked")
    public EnumSetting(String name, String description, E defaultValue) {
        super(name, description, defaultValue);
        this.values = (E[]) defaultValue.getDeclaringClass().getEnumConstants();
    }

    /** Cycles to the next enum value. */
    public void cycle() {
        int nextIndex = (value.ordinal() + 1) % values.length;
        value = values[nextIndex];
    }

    public E[] getValues() { return values; }

    @Override
    public SettingType getType() { return SettingType.ENUM; }
}
