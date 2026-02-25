package com.mystichorizons.mysticnametags.integrations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Provides arbitrary stat / challenge values for players,
 * e.g. "kills.goblin", "dungeons.completed.goblin_caves", etc.
 */
public interface StatProvider {

    /**
     * @param uuid Player UUID
     * @param key  Stat key, e.g. "kills.goblin"
     * @return current value for the stat, or null if unknown.
     */
    @Nullable
    Integer getStatValue(@Nonnull UUID uuid, @Nonnull String key);
}