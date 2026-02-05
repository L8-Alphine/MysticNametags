package com.mystichorizons.mysticnametags.placeholders;

import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.tags.TagManager;

import java.util.Locale;
import java.util.UUID;

/**
 * at.helpch.placeholderapi expansion for MysticNameTags.
 *
 * Usage:
 *   %mystictags_tag%
 *   %mystictags_tag_plain%
 *   %mystictags_full%
 *   %mystictags_full_plain%
 */
public final class MysticTagsHelpchExpansion extends PlaceholderExpansion {

    private final MysticNameTagsPlugin plugin;

    public MysticTagsHelpchExpansion(MysticNameTagsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        // %mystictags_...%
        return "mystictags";
    }

    @Override
    public String getAuthor() {
        return "Alphine";
    }

    @Override
    public String getVersion() {
        // If you prefer a fixed string, just return "1.0.0" here.
        return plugin.getResolvedVersion();
    }

    /**
     * Keep this expansion registered across reloads.
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * Allow PlaceholderAPI to register this expansion.
     */
    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(PlayerRef player, String params) {
        if (player == null) {
            return "";
        }

        TagManager manager = TagManager.get();
        if (manager == null) {
            return "";
        }

        UUID uuid = player.getUuid();
        if (uuid == null) {
            return "";
        }

        String playerName = player.getUsername();
        if (playerName == null || playerName.isEmpty()) {
            playerName = "Unknown";
        }

        String key = (params == null) ? "" : params.toLowerCase(Locale.ROOT).trim();

        try {
            switch (key) {
                case "tag":
                    // Colored tag only (e.g. "&#8A2BE2&l[Mystic]")
                    return manager.getColoredActiveTag(uuid);

                case "tag_plain":
                    // Tag without any color codes
                    return manager.getPlainActiveTag(uuid);

                case "full":
                    // [Rank] Name [Tag] with colors (for chat / UI)
                    return manager.getColoredFullNameplate(uuid, playerName);

                case "full_plain":
                    // [Rank] Name [Tag] without any formatting
                    return manager.getPlainFullNameplate(uuid, playerName);

                default:
                    // Unknown parameter -> let PlaceholderAPI keep original text
                    return null;
            }
        } catch (Exception e) {
            // Silent failure so we don't spam logs
            return null;
        }
    }
}
