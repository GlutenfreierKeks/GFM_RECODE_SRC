package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;

public class BoolSetting extends Setting<Boolean> {

    public BoolSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }

    public void toggle() {
        value = !value;
    }

    @Override
    public SettingType getType() { return SettingType.BOOL; }
}
