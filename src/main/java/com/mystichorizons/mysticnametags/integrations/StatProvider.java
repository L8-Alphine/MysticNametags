package com.mystichorizons.mysticnametags.integrations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public interface StatProvider {

    @Nullable
    Integer getStatValue(@Nonnull UUID uuid, @Nonnull String key);

    /**
     * Returns the summed value of all stats matching the pattern.
     * Example:
     *   "killed.goblin_*" -> sum of all killed.goblin_xxx stats
     *
     * Default behavior:
     * - If no wildcard is present, falls back to exact lookup.
     * - If wildcard exists but provider doesn't support it, returns null.
     */
    default @Nullable Integer getStatValuePattern(@Nonnull UUID uuid, @Nonnull String keyPattern) {
        if (!keyPattern.contains("*")) {
            return getStatValue(uuid, keyPattern);
        }
        return null;
    }
}