package com.mystichorizons.mysticnametags.tags;

import com.google.gson.Gson;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.UUID;

/**
 * Pluggable storage backend for player tag data.
 */
public interface PlayerTagStore {

    /**
     * Load player tag data; return empty object if none exists.
     */
    @Nonnull
    PlayerTagData load(@Nonnull UUID uuid);

    /**
     * Persist player tag data for this player.
     */
    void save(@Nonnull UUID uuid, @Nonnull PlayerTagData data);

    /**
     * Optional: remove all data for this player (e.g., admin reset).
     */
    default void delete(@Nonnull UUID uuid) {
        // no-op by default
    }

    /**
     * Optional, one-time migration from a folder of *.json files into
     * this backend (used when switching FILE -> SQL).
     *
     * Implementations should be idempotent and best-effort.
     */
    default void migrateFromFolder(@Nonnull File playerDataFolder,
                                   @Nonnull Gson gson) {
        // default: do nothing
    }
}
