package de.glutenfreierkeks.gfm_recode.client.gui.components;

import de.glutenfreierkeks.gfm_recode.client.settings.Setting;
import net.minecraft.client.gui.DrawContext;

public abstract class SettingWidget extends Component {
    protected final Setting<?> setting;

    public SettingWidget(int x, int y, int width, int height, Setting<?> setting) {
        super(x, y, width, height);
        this.setting = setting;
    }

    public Setting<?> getSetting() {
        return setting;
    }
}
