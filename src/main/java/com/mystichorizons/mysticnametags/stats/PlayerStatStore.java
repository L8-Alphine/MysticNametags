package com.mystichorizons.mysticnametags.stats;

import com.google.gson.Gson;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.UUID;

/**
 * Storage backend for player stats.
 *
 * Implementations:
 *  - FilePlayerStatStore (JSON per player)
 *  - SqlPlayerStatStore  (single table with JSON blob)
 */
public interface PlayerStatStore {

    @Nonnull
    PlayerStatsData load(@Nonnull UUID uuid);

    void save(@Nonnull UUID uuid, @Nonnull PlayerStatsData data);

    /**
     * Optional hard delete (e.g. for admin full reset).
     */
    default void delete(@Nonnull UUID uuid) {
        // no-op by default
    }

    /**
     * Optional migration from legacy folder-based JSON stats, if you ever
     * used that. For now you can leave it unused.
     */
    default void migrateFromFolder(@Nonnull File folder, @Nonnull Gson gson) {
        // no-op by default
    }
}