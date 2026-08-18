package de.glutenfreierkeks.gfm_recode.client.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;

public final class ScoreboardHudUtil {

    private static final Comparator<ScoreboardEntry> ENTRY_COMPARATOR = Comparator.comparing(ScoreboardEntry::value)
        .reversed()
        .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private ScoreboardHudUtil() {}

    public static ScoreboardObjective getSidebarObjective(MinecraftClient mc) {
        if (mc == null || mc.world == null || mc.player == null) return null;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective teamObjective = null;
        Team team = scoreboard.getScoreHolderTeam(mc.player.getNameForScoreboard());
        if (team != null) {
            ScoreboardDisplaySlot teamSlot = ScoreboardDisplaySlot.fromFormatting(team.getColor());
            if (teamSlot != null) {
                teamObjective = scoreboard.getObjectiveForSlot(teamSlot);
            }
        }

        return teamObjective != null ? teamObjective : scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
    }

    public static List<Text> getSidebarLines(ScoreboardObjective objective) {
        if (objective == null) return List.of();

        Scoreboard scoreboard = objective.getScoreboard();
        return scoreboard.getScoreboardEntries(objective)
            .stream()
            .filter(entry -> !entry.hidden())
            .sorted(ENTRY_COMPARATOR)
            .limit(15L)
            .map(entry -> {
                Team team = scoreboard.getScoreHolderTeam(entry.owner());
                return (Text) Team.decorateName(team, entry.name());
            })
            .toList();
    }

    public static NumberFormat getSidebarNumberFormat(ScoreboardObjective objective) {
        return objective.getNumberFormatOr(StyledNumberFormat.RED);
    }
}
