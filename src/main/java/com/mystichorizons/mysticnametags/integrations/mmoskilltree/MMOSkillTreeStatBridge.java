package com.mystichorizons.mysticnametags.integrations.mmoskilltree;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.ziggfreed.mmoskilltree.api.MMOSkillTreeAPI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Adapts MMOSkillTree values into MysticNameTags stat requirements.
 *
 * Supported keys:
 *
 *   - "mmoskilltree.total_level"                  -> total player skill level
 *   - "mmoskilltree.total_xp"                     -> total player skill XP
 *   - "mmoskilltree.level.<skillId>"              -> skill level
 *   - "mmoskilltree.xp.<skillId>"                 -> skill XP
 *   - "mmoskilltree.progress.<skillId>"           -> level progress percent
 *   - "mmoskilltree.achievement.<id>"             -> 1 when unlocked, otherwise 0
 *   - "mmoskilltree.achievement_progress.<id>"    -> achievement progress
 *   - "mmoskilltree.achievement_points"           -> achievement points
 *   - "mmoskilltree.stat.<canonicalKey>"          -> MMOSkillTree statistic total
 *
 * Returns null if MMOSkillTree is unavailable, the player is offline, or the key is unknown.
 */
public final class MMOSkillTreeStatBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PREFIX = "mmoskilltree.";

    private MMOSkillTreeStatBridge() {
    }

    @Nullable
    public static Integer getStatValue(@Nonnull UUID uuid, @Nonnull String key) {
        String trimmed = key.trim();
        if (!trimmed.startsWith(PREFIX)) {
            return null;
        }

        String tail = trimmed.substring(PREFIX.length()).trim();
        if (tail.isBlank()) {
            return null;
        }

        if (!MMOSkillTreeCompat.isAvailable()) {
            return null;
        }

        PlayerContext context = resolvePlayer(uuid);
        if (context == null) {
            return null;
        }

        try {
            if (!MMOSkillTreeAPI.hasSkillData(context.store, context.ref)) {
                return null;
            }

            String lowerTail = tail.toLowerCase(Locale.ROOT);

            if (lowerTail.equals("total_level") || lowerTail.equals("totallevel") || lowerTail.equals("total.level")) {
                return Math.max(0, MMOSkillTreeAPI.getTotalLevel(context.store, context.ref));
            }

            if (lowerTail.equals("total_xp") || lowerTail.equals("totalxp") || lowerTail.equals("total.xp")) {
                return clampLong(MMOSkillTreeAPI.getTotalXp(context.store, context.ref));
            }

            if (lowerTail.equals("achievement_points") || lowerTail.equals("achievementpoints")
                    || lowerTail.equals("achievements.points")) {
                return Math.max(0, MMOSkillTreeAPI.getAchievementPoints(context.store, context.ref));
            }

            if (lowerTail.startsWith("level.")) {
                return getSkillLevel(context, tail.substring("level.".length()));
            }

            if (lowerTail.startsWith("skill.")) {
                return getSkillLevel(context, tail.substring("skill.".length()));
            }

            if (lowerTail.startsWith("xp.")) {
                return getSkillXp(context, tail.substring("xp.".length()));
            }

            if (lowerTail.startsWith("progress.")) {
                return getSkillProgressPercent(context, tail.substring("progress.".length()));
            }

            if (lowerTail.startsWith("level_progress.")) {
                return getSkillProgressPercent(context, tail.substring("level_progress.".length()));
            }

            if (lowerTail.startsWith("achievement_progress.")) {
                return getAchievementProgress(context, tail.substring("achievement_progress.".length()));
            }

            if (lowerTail.startsWith("achievement.progress.")) {
                return getAchievementProgress(context, tail.substring("achievement.progress.".length()));
            }

            if (lowerTail.startsWith("achievement.unlocked.")) {
                return getAchievementUnlocked(context, tail.substring("achievement.unlocked.".length()));
            }

            if (lowerTail.startsWith("achievement.")) {
                return getAchievementUnlocked(context, tail.substring("achievement.".length()));
            }

            if (lowerTail.startsWith("stat.")) {
                return getStatisticTotal(context, tail.substring("stat.".length()));
            }

            if (lowerTail.startsWith("statistics.")) {
                return getStatisticTotal(context, tail.substring("statistics.".length()));
            }

            return null;
        } catch (Throwable t) {
            LOGGER.at(Level.FINE)
                    .withCause(t)
                    .log("[MysticNameTags] Failed to read MMOSkillTree stat '%s' for %s", tail, uuid);
            return null;
        }
    }

    @Nullable
    private static Integer getSkillLevel(@Nonnull PlayerContext context, @Nonnull String skillId) {
        String normalized = skillId.trim();
        if (normalized.isBlank()) {
            return null;
        }

        int level = MMOSkillTreeAPI.getLevel(context.store, context.ref, normalized);
        return level <= 0 ? null : level;
    }

    @Nullable
    private static Integer getSkillXp(@Nonnull PlayerContext context, @Nonnull String skillId) {
        String normalized = skillId.trim();
        if (normalized.isBlank()) {
            return null;
        }

        long xp = MMOSkillTreeAPI.getXp(context.store, context.ref, normalized);
        return xp <= 0L ? null : clampLong(xp);
    }

    @Nullable
    private static Integer getSkillProgressPercent(@Nonnull PlayerContext context, @Nonnull String skillId) {
        String normalized = skillId.trim();
        if (normalized.isBlank()) {
            return null;
        }

        double progress = MMOSkillTreeAPI.getLevelProgress(context.store, context.ref, normalized);
        if (Double.isNaN(progress) || Double.isInfinite(progress)) {
            return null;
        }

        double percent = Math.max(0.0D, Math.min(100.0D, progress * 100.0D));
        return (int) Math.round(percent);
    }

    @Nullable
    private static Integer getAchievementUnlocked(@Nonnull PlayerContext context, @Nonnull String achievementId) {
        String normalized = achievementId.trim();
        if (normalized.isBlank()) {
            return null;
        }

        return MMOSkillTreeAPI.isAchievementUnlocked(context.store, context.ref, normalized) ? 1 : 0;
    }

    @Nullable
    private static Integer getAchievementProgress(@Nonnull PlayerContext context, @Nonnull String achievementId) {
        String normalized = achievementId.trim();
        if (normalized.isBlank()) {
            return null;
        }

        long progress = MMOSkillTreeAPI.getAchievementProgress(context.store, context.ref, normalized);
        return progress <= 0L ? 0 : clampLong(progress);
    }

    @Nullable
    private static Integer getStatisticTotal(@Nonnull PlayerContext context, @Nonnull String canonicalKey) {
        String normalized = canonicalKey.trim();
        if (normalized.isBlank()) {
            return null;
        }

        long total = MMOSkillTreeAPI.getStatTotal(context.store, context.ref, normalized);
        return total <= 0L ? 0 : clampLong(total);
    }

    @Nullable
    private static PlayerContext resolvePlayer(@Nonnull UUID uuid) {
        TagManager manager = TagManager.get();
        if (manager == null) {
            return null;
        }

        PlayerRef playerRef = manager.getOnlinePlayer(uuid);
        if (playerRef == null) {
            return null;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }

        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return null;
        }

        return new PlayerContext(store, ref);
    }

    private static int clampLong(long value) {
        if (value <= 0L) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private static final class PlayerContext {
        private final Store<EntityStore> store;
        private final Ref<EntityStore> ref;

        private PlayerContext(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
            this.store = store;
            this.ref = ref;
        }
    }
}
