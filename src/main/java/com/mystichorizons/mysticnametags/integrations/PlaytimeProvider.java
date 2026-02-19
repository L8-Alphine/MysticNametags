package com.mystichorizons.mysticnametags.integrations;

import javax.annotation.Nullable;
import java.util.UUID;

public interface PlaytimeProvider {
    /** @return total playtime minutes, or null if unavailable */
    @Nullable Integer getPlaytimeMinutes(UUID uuid);
    String getName();
}
