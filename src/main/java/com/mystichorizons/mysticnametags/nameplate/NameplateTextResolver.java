package com.mystichorizons.mysticnametags.nameplate;

import at.helpch.placeholderapi.PlaceholderAPI;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.WiFlowPlaceholderSupport;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

/**
 * Central place for building and resolving nameplate text:
 *  - {rank} {name} {tag} -> Settings.nameplateFormat
 *  - Optional WiFlow / helpch PlaceholderAPI
 *  - Final colorization.
 */
public final class NameplateTextResolver {

    private static final boolean HELPCH_AVAILABLE;

    static {
        boolean helpch;
        try {
            Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
            helpch = true;
        } catch (ClassNotFoundException ex) {
            helpch = false;
        }
        HELPCH_AVAILABLE = helpch;
    }

    private NameplateTextResolver() {
    }

    /**
     * Build the nameplate for the given player using:
     *
     *   Settings.nameplateFormat
     *   -> (optional) WiFlowPlaceholderAPI
     *   -> (optional) at.helpch.placeholderapi
     *   -> ColorFormatter.colorize(...)
     */
    public static String build(PlayerRef playerRef,
                               String rank,
                               String name,
                               String tag) {

        Settings settings = Settings.get();

        // 1) Base format: {rank} {name} {tag}
        String text = settings.formatNameplateRaw(rank, name, tag);

        if (text == null || text.isEmpty()) {
            return text;
        }

        // 2) WiFlow placeholder resolution (if enabled + API present)
        if (settings.isWiFlowPlaceholdersEnabled()) {
            text = WiFlowPlaceholderSupport.apply(playerRef, text);
        }

        // 3) at.helpch PlaceholderAPI resolution (if enabled + present)
        if (settings.isHelpchPlaceholderApiEnabled()
                && HELPCH_AVAILABLE
                && playerRef != null) {

            try {
                text = PlaceholderAPI.setPlaceholders(playerRef, text);
            } catch (Throwable ignored) {
                // Fail-soft: if anything explodes, just keep existing text
            }
        }

        // 4) Final colorization (& + hex)
        return ColorFormatter.colorize(text);
    }
}
