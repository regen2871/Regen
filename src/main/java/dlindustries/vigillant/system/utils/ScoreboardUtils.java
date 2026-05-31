package dlindustries.vigillant.system.utils;

import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dlindustries.vigillant.system.system.mc;

public final class ScoreboardUtils {

	private ScoreboardUtils() {
	}

	public static String getRawScoreboard() {
		try {
			if (mc == null || mc.player == null || mc.world == null || mc.world.getScoreboard() == null) {
				return "";
			}

			Scoreboard scoreboard = mc.world.getScoreboard();
			if (scoreboard == null) return "";

			ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
			if (objective == null) return "";

			StringBuilder result = new StringBuilder();
			Collection<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective);
			if (entries == null) return "";

			for (ScoreboardEntry entry : entries) {
				if (entry == null) continue;

				String name = entry.owner();
				if (name == null) continue;

				try {
					Team team = scoreboard.getScoreHolderTeam(name);
					if (team != null) {
						Text prefix = team.getPrefix();
						Text suffix = team.getSuffix();
						if (prefix != null && suffix != null) {
							result.append(prefix.getString()).append(name).append(suffix.getString()).append("\n");
						} else {
							result.append(name).append("\n");
						}
					} else {
						result.append(name).append("\n");
					}
				} catch (Exception e) {
					result.append(name).append("\n");
				}
			}

			return result.toString();
		} catch (Exception e) {
			return "";
		}
	}

	public static String getPing() {
		String scoreboard = getRawScoreboard();
		Pattern pattern = Pattern.compile("\\((\\d+)ms\\)");
		Matcher matcher = pattern.matcher(scoreboard);

		if (matcher.find()) {
			return matcher.group(1);
		}

		return String.valueOf(PingUtils.getCachedPing());
	}

	public static String getMoney() {
		String scoreboard = getRawScoreboard();
		if (scoreboard.isEmpty()) return "0";

		Pattern pattern = Pattern.compile(
				"(?:\\$|Money|Balance)\\s*:?\\s*\\$?\\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\\.\\d+)?[KMB]?)",
				Pattern.CASE_INSENSITIVE
		);

		Matcher matcher = pattern.matcher(scoreboard);

		if (matcher.find()) {
			return matcher.group(1).replace(",", "");
		}

		return "0";
	}

	public static String getKeyallTimer() {
		String scoreboard = getRawScoreboard();
		Pattern pattern = Pattern.compile("Keyall\\s+([0-9]+[msh]\\s*)+", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(scoreboard);

		if (matcher.find()) {
			String fullMatch = matcher.group(0);
			return fullMatch.replaceFirst("(?i)Keyall\\s+", "").trim();
		}

		return "";
	}

	public static String getRegion(boolean replace) {
		String scoreboard = getRawScoreboard();

		scoreboard = scoreboard.replaceAll("§.", "");

		Pattern pattern = Pattern.compile("([A-Za-z][A-Za-z\\s]+)\\s*\\(");
		Matcher matcher = pattern.matcher(scoreboard);

		if (matcher.find()) {
			String region = matcher.group(1).trim();
			if (!region.isEmpty()) {
				return replace ? "Regen" : region;
			}
		}

		return "Regen";
	}
}
