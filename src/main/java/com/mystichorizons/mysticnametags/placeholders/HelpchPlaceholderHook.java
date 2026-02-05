package com.mystichorizons.mysticnametags.placeholders;

import at.helpch.placeholderapi.PlaceholderAPI;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import java.util.logging.Level;

public final class HelpchPlaceholderHook {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public void register() {
        // Runtime check so we don't hard-fail if PlaceholderAPI isn't on the classpath
        try {
            Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
        } catch (ClassNotFoundException ex) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] at.helpch PlaceholderAPI not found; skipping helpch expansion.");
            return;
        }

        boolean success = new MysticTagsHelpchExpansion(MysticNameTagsPlugin.getInstance()).register();

        if (success) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Registered at.helpch PlaceholderAPI expansion 'mystictags'. "
                            + "Placeholders: %mystictags_tag%, %mystictags_tag_plain%, "
                            + "%mystictags_full%, %mystictags_full_plain%");
        } else {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Failed to register at.helpch PlaceholderAPI expansion 'mystictags'.");
        }
    }
}
