package com.mystichorizons.mysticnametags.integrations;

import com.mystichorizons.mysticnametags.playtime.PlaytimeService;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Default PlaytimeProvider using MysticNameTags' own PlaytimeService/stat storage.
 */
public final class InternalPlaytimeProvider implements PlaytimeProvider {

    private final PlaytimeService playtimeService;

    public InternalPlaytimeProvider(@Nonnull PlaytimeService playtimeService) {
        this.playtimeService = playtimeService;
    }

    @Override
    public long getPlaytimeMinutes(@Nonnull UUID uuid) {
        long seconds = playtimeService.getPlaytimeSeconds(uuid);
        if (seconds <= 0L) {
            return 0L;
        }
        return seconds / 60L; // floor
    }
}