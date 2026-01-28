package com.mystichorizons.mysticnametags.api;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.tags.TagDefinition;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nullable;
import java.util.UUID;

public final class MysticNameTagsAPI {

    private MysticNameTagsAPI() {
    }

    /**
     * Returns the raw tag display for the player (with color codes),
     * or null if they have no tag equipped.
     */
    @Nullable
    public static String getActiveTagDisplay(@Nullable UUID uuid) {
        if (uuid == null) return null;
        TagDefinition def = TagManager.get().getEquipped(uuid);
        return def != null ? def.getDisplay() : null;
    }

    /**
     * Simple placeholder expansion for text containing %mystic_tag%.
     */
    public static String applyTagPlaceholder(String text,
                                             @Nullable PlayerRef playerRef,
                                             @Nullable UUID uuid) {
        if (text == null || !text.contains("%mystic_tag%")) {
            return text;
        }

        String tag = getActiveTagDisplay(uuid);
        if (tag == null) tag = "";

        return text.replace("%mystic_tag%", tag);
    }
}
