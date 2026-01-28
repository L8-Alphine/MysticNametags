package com.mystichorizons.mysticnametags.placeholders;

import com.hypixel.hytale.logger.HytaleLogger;
import com.wiflow.placeholderapi.WiFlowPlaceholderAPI;

import java.util.logging.Level;

public final class WiFlowPlaceholderHook {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public void register() {
        // Check if WiFlowPlaceholderAPI is loaded and initialized
        if (!WiFlowPlaceholderAPI.isInitialized()) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] WiFlowPlaceholderAPI not initialized; skipping WiFlow expansion.");
            return;
        }

        boolean success = WiFlowPlaceholderAPI.registerExpansion(new MysticTagsWiFlowExpansion());
        if (success) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Registered WiFlowPlaceholderAPI expansion 'mystictags'. "
                            + "Placeholders: {mystictags_tag}, {mystictags_tag_plain}, {mystictags_full}, {mystictags_full_plain}");
        } else {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Failed to register WiFlowPlaceholderAPI expansion 'mystictags'.");
        }
    }
}
