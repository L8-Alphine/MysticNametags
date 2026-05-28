package com.mystichorizons.mysticnametags.integrations.rpgleveling;

import com.hypixel.hytale.logger.HytaleLogger;
import org.zuxaw.plugin.api.RPGLevelingAPI;
import org.zuxaw.plugin.components.PlayerLevelData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Adapts RPGLeveling values into MysticNameTags stat requirements.
 *
 * Supported keys:
 *
 *   - "rpgleveling.lvl"                         -> player level
 *   - "rpgleveling.skills.<stat>"               -> allocated stat points
 *   - "rpgleveling.skills.available"            -> unspent stat points
 *   - "rpgleveling.skills.total"                -> total allocated stat points
 *   - "rpgleveling.classes"                     -> 1 when a class is selected
 *   - "rpgleveling.classes.<classId>"           -> 1 when selected class id matches
 *   - "rpgleveling.classes.tier"                -> selected class tier
 *   - "rpgleveling.classes.tier.<classId>"      -> tier for a specific class id
 *   - "rpgleveling.progression"                 -> level XP progress percent
 *   - "rpgleveling.progression.xp"              -> current XP (rounded)
 *   - "rpgleveling.progression.required_xp"     -> XP needed for next level
 *   - "rpgleveling.progression.class_kills"     -> selected class kills in current tier
 *
 * Returns null if RPGLeveling is unavailable or the key is unknown.
 */
public final class RPGLevelingStatBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PREFIX = "rpgleveling.";

    private RPGLevelingStatBridge() {
    }

    @Nullable
    public static Integer getStatValue(@Nonnull UUID uuid, @Nonnull String key) {
        String trimmed = key.trim();
        if (!trimmed.startsWith(PREFIX)) {
            return null;
        }

        String tail = trimmed.substring(PREFIX.length());
        if (tail.isBlank()) {
            return null;
        }

        try {
            RPGLevelingAPI api = RPGLevelingCompat.getApi();
            if (api == null) {
                return null;
            }

            if (tail.equalsIgnoreCase("lvl") || tail.equalsIgnoreCase("level")) {
                int level = RPGLevelingCompat.getPlayerLevel(uuid);
                return level <= 0 ? null : level;
            }

            if (tail.equalsIgnoreCase("xp")) {
                double xp = api.getPlayerXP(uuid);
                if (xp <= 0.0D) {
                    return null;
                }
                if (xp > Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
                return (int) Math.round(xp);
            }

            String lowerTail = tail.toLowerCase(Locale.ROOT);
            if (lowerTail.startsWith("skills.")) {
                String stat = tail.substring("skills.".length());
                return getSkillStat(uuid, stat);
            }

            if (lowerTail.equals("skills")) {
                return getSkillStat(uuid, "total");
            }

            if (lowerTail.equals("classes") || lowerTail.equals("class")) {
                RPGLevelingAPI.PlayerClassInfo info = RPGLevelingCompat.getPlayerClassInfo(uuid);
                return info != null && info.hasClass() ? 1 : 0;
            }

            if (lowerTail.equals("classes.tier") || lowerTail.equals("class_tier")) {
                int tier = RPGLevelingCompat.getSelectedClassTier(uuid);
                return tier < 0 ? null : tier;
            }

            if (lowerTail.startsWith("classes.tier.") || lowerTail.startsWith("class_tier.")) {
                String prefix = lowerTail.startsWith("classes.tier.") ? "classes.tier." : "class_tier.";
                String classId = tail.substring(prefix.length());
                if (classId.isBlank()) {
                    return null;
                }

                int tier = RPGLevelingCompat.getClassTier(uuid, classId);
                return tier < 0 ? 0 : tier;
            }

            if (lowerTail.startsWith("classes.") || lowerTail.startsWith("class.")) {
                String prefix = lowerTail.startsWith("classes.") ? "classes." : "class.";
                String classId = tail.substring(prefix.length());
                if (classId.isBlank()) {
                    return null;
                }

                RPGLevelingAPI.PlayerClassInfo info = RPGLevelingCompat.getPlayerClassInfo(uuid);
                if (info == null || !info.hasClass()) {
                    return 0;
                }

                return RPGLevelingCompat.classIdMatches(info.getClassId(), classId) ? 1 : 0;
            }

            if (lowerTail.equals("progression") || lowerTail.equals("progression.percent")) {
                return getLevelProgressPercent(uuid);
            }

            if (lowerTail.equals("progression.xp")) {
                return roundXp(api.getPlayerXP(uuid));
            }

            if (lowerTail.equals("progression.required_xp") || lowerTail.equals("progression.xp_needed")) {
                RPGLevelingAPI.PlayerLevelInfo info = RPGLevelingCompat.getPlayerLevelInfo(uuid);
                return info == null ? null : roundXp(info.getXpNeededForNext());
            }

            if (lowerTail.equals("progression.class_kills")) {
                RPGLevelingAPI.PlayerClassInfo info = RPGLevelingCompat.getPlayerClassInfo(uuid);
                if (info == null || !info.hasClass()) {
                    return 0;
                }
                return Math.max(0, info.getKillsInCurrentTier());
            }

            return null;
        } catch (Throwable t) {
            LOGGER.at(Level.FINE)
                    .withCause(t)
                    .log("[MysticNameTags] Failed to read RPGLeveling stat '%s' for %s", tail, uuid);
            return null;
        }
    }

    @Nullable
    private static Integer getSkillStat(@Nonnull UUID uuid, @Nonnull String rawStat) {
        String stat = rawStat.trim();
        if (stat.isEmpty()) {
            return null;
        }

        PlayerLevelData data = RPGLevelingCompat.getPlayerData(uuid);
        if (data == null) {
            return null;
        }

        if (stat.equalsIgnoreCase("available") || stat.equalsIgnoreCase("unspent")) {
            return Math.max(0, data.getAvailableStatPoints());
        }

        if (stat.equalsIgnoreCase("total") || stat.equalsIgnoreCase("allocated")) {
            int total = 0;
            for (Integer points : data.getAllocatedStats().values()) {
                if (points != null && points > 0) {
                    total += points;
                }
            }
            return total;
        }

        for (Map.Entry<String, Integer> entry : data.getAllocatedStats().entrySet()) {
            if (entry == null || !entry.getKey().equalsIgnoreCase(stat)) {
                continue;
            }
            Integer points = entry.getValue();
            return points != null ? Math.max(0, points) : 0;
        }

        return Math.max(0, data.getAllocatedPoints(stat));
    }

    @Nullable
    private static Integer getLevelProgressPercent(@Nonnull UUID uuid) {
        RPGLevelingAPI.PlayerLevelInfo info = RPGLevelingCompat.getPlayerLevelInfo(uuid);
        if (info == null) {
            return null;
        }

        if (info.isMaxLevel()) {
            return 100;
        }

        double needed = info.getXpNeededForNext();
        if (needed <= 0.0D) {
            return 0;
        }

        double pct = Math.max(0.0D, Math.min(100.0D, (info.getExperience() / needed) * 100.0D));
        return (int) Math.round(pct);
    }

    @Nullable
    private static Integer roundXp(double xp) {
        if (xp < 0.0D || Double.isNaN(xp) || Double.isInfinite(xp)) {
            return null;
        }
        if (xp > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.round(xp);
    }
}
