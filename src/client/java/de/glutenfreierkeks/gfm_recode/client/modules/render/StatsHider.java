package de.glutenfreierkeks.gfm_recode.client.modules.render;

import de.glutenfreierkeks.gfm_recode.client.modules.Module;
import de.glutenfreierkeks.gfm_recode.client.settings.types.EnumSetting;
import de.glutenfreierkeks.gfm_recode.client.settings.types.StringSetting;
import de.glutenfreierkeks.gfm_recode.client.utils.ScoreboardHudUtil;
import net.minecraft.client.render.Camera;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class StatsHider extends Module {

    public enum Mode {
        SCOREBOARD_OFF,
        SCOREBOARD_CHANGE
    }

    private final EnumSetting<Mode> mode = register(
        new EnumSetting<>("Mode", "How the scoreboard should be handled", Mode.SCOREBOARD_OFF)
    );

    private final StringSetting title = register(
        new StringSetting("Title", "Custom sidebar title. Leave empty to keep the original", "")
    );

    private final List<StringSetting> lineSettings = new ArrayList<>();
    private Mode lastMode = mode.getValue();

    public StatsHider() {
        super("StatsHider", "Hides or locally edits the sidebar scoreboard", Category.RENDER);

        for (int i = 0; i < 15; i++) {
            StringSetting setting = register(
                new StringSetting("Line " + (i + 1), "Custom text for scoreboard line " + (i + 1), "")
            );
            lineSettings.add(setting);
        }
    }

    @Override
    protected void onEnable() {
        lastMode = mode.getValue();
        if (mode.getValue() == Mode.SCOREBOARD_CHANGE) {
            captureCurrentScoreboard();
        }
    }

    @Override
    public void onTick() {
        if (mode.getValue() != lastMode) {
            if (mode.getValue() == Mode.SCOREBOARD_CHANGE) {
                captureCurrentScoreboard();
            }
            lastMode = mode.getValue();
        }
    }

    public Mode getMode() {
        return mode.getValue();
    }

    public Text getRenderedTitle(Text original) {
        return title.getValue().isEmpty() ? original : Text.literal(title.getValue()).setStyle(original.getStyle());
    }

    public Text getRenderedLine(int index, Text original) {
        if (index < 0 || index >= lineSettings.size()) return original;
        String custom = lineSettings.get(index).getValue();
        return custom.isEmpty() ? original : Text.literal(custom).setStyle(original.getStyle());
    }

    private void captureCurrentScoreboard() {
        ScoreboardObjective objective = ScoreboardHudUtil.getSidebarObjective(mc);
        if (objective == null) return;

        title.setValue(objective.getDisplayName().getString());

        List<Text> lines = ScoreboardHudUtil.getSidebarLines(objective);
        for (int i = 0; i < lineSettings.size(); i++) {
            lineSettings.get(i).setValue(i < lines.size() ? lines.get(i).getString() : "");
        }
    }

    @Override
    public String getDisplayInfo() {
        return mode.getValue().name();
    }

    @Override
    public void render3D(Matrix4f posMatrix, Matrix4f projMatrix, Camera camera, float tickDelta) {}
}
