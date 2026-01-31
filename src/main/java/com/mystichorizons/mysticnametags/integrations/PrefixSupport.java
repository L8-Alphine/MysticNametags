package com.mystichorizons.mysticnametags.integrations;

import javax.annotation.Nullable;
import java.util.UUID;

public interface PrefixSupport {

    boolean isAvailable();

    /**
     * Returns the raw prefix text (may contain color codes, gradients, etc.),
     * or null if none is set.
     */
    @Nullable
    String getPrefix(UUID uuid);

    String getBackendName();
}
