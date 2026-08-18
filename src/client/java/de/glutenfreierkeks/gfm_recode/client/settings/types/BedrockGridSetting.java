package de.glutenfreierkeks.gfm_recode.client.settings.types;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import java.util.BitSet;

public class BedrockGridSetting extends Setting<BitSet> {

    public BedrockGridSetting(String name, String description) {
        super(name, description, new BitSet(576));
    }

    public void set(int x, int z, boolean state) {
        if (x < 0 || x >= 24 || z < 0 || z >= 24) return;
        value.set(x * 24 + z, state);
    }

    public boolean get(int x, int z) {
        if (x < 0 || x >= 24 || z < 0 || z >= 24) return false;
        return value.get(x * 24 + z);
    }

    public void toggle(int x, int z) {
        if (x < 0 || x >= 24 || z < 0 || z >= 24) return;
        value.flip(x * 24 + z);
    }

    public void clear() {
        value.clear();
    }

    @Override
    public SettingType getType() {
        return SettingType.BEDROCK_GRID;
    }
}
