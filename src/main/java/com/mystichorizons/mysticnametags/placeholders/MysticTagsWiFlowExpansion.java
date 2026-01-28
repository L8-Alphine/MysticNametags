package com.mystichorizons.mysticnametags.placeholders;

import com.mystichorizons.mysticnametags.tags.TagManager;
import com.wiflow.placeholderapi.context.PlaceholderContext;
import com.wiflow.placeholderapi.expansion.PlaceholderExpansion;

import java.util.Locale;
import java.util.UUID;

/**
 * WiFlowPlaceholderAPI expansion for MysticNameTags.
 *
 * Provides placeholders like:
 *   {mystictags_tag}
 *   {mystictags_tag_plain}
 *   {mystictags_full}
 *   {mystictags_full_plain}
 */
public class MysticTagsWiFlowExpansion extends PlaceholderExpansion {

    @Override
    public String getIdentifier() {
        // {mystictags_*}
        return "mystictags";
    }

    @Override
    public String getAuthor() {
        return "Alphine";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getName() {
        return "MysticNameTags Expansion";
    }

    @Override
    public String getDescription() {
        return "Provides MysticNameTags placeholders: tag, tag_plain, full, full_plain.";
    }

    /**
     * Optional – if you want WiFlow to only register this when MysticNameTags is present.
     * Because this class lives inside MysticNameTags itself, you can safely return null.
     *
     * If you ever move this to its own expansion JAR, you could return the API or plugin class name:
     *   return "com.mystichorizons.mysticnametags.MysticNameTagsPlugin";
     */
    @Override
    public String getRequiredPlugin() {
        return null;
    }

    @Override
    public String onPlaceholderRequest(PlaceholderContext context, String params) {
        // Defensive checks – parser will keep original text when we return null
        if (context == null) {
            return null;
        }

        UUID uuid = context.getPlayerUuid();
        if (uuid == null) {
            return null;
        }

        TagManager manager = TagManager.get();
        if (manager == null) {
            return null;
        }

        // For full nameplate we need the player's name – WiFlow gives us this directly
        String playerName = context.getPlayerName();
        if (playerName == null || playerName.isEmpty()) {
            playerName = "Unknown";
        }

        String key = (params == null) ? "" : params.toLowerCase(Locale.ROOT).trim();

        try {
            switch (key) {
                case "tag":
                    // Colored tag only (e.g. "&#8A2BE2&l[Mystic]" formatted)
                    return manager.getColoredActiveTag(uuid);

                case "tag_plain":
                    // Tag without any color codes
                    return manager.getPlainActiveTag(uuid);

                case "full":
                    // [Rank] Name [Tag] with colors, for chat / UI, NOT for Nameplate
                    return manager.getColoredFullNameplate(uuid, playerName);

                case "full_plain":
                    // [Rank] Name [Tag] with all formatting stripped
                    return manager.getPlainFullNameplate(uuid, playerName);

                default:
                    // Unknown placeholder for this identifier – let parser keep original
                    return null;
            }
        } catch (Exception e) {
            // Fail quietly – WiFlow recommends not spamming logs from expansions
            return null;
        }
    }
}
