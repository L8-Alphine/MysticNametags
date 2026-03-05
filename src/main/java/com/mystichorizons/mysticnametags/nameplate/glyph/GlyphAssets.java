package com.mystichorizons.mysticnametags.nameplate.glyph;

import javax.annotation.Nullable;
import java.awt.Color;

public final class GlyphAssets {

    // Change if your asset namespace differs
    public static final String NAMESPACE = "MysticNameTags";

    private GlyphAssets() {}

    public static String modelId(String safeCharId) {
        // e.g. MysticNameTags:Glyph_lo_a
        return NAMESPACE + ":Glyph_" + safeCharId;
    }

    public static String tintEffectId(int rgbQuantized) {
        // e.g. MysticNameTags:HtTint_FF00AA
        return NAMESPACE + ":HtTint_" + String.format("%06X", (rgbQuantized & 0xFFFFFF));
    }

    public static int rgb(Color c) {
        return ((c.getRed() & 0xFF) << 16) | ((c.getGreen() & 0xFF) << 8) | (c.getBlue() & 0xFF);
    }

    @Nullable
    public static Color tryParseHex6(String hex6) {
        if (hex6 == null || hex6.length() != 6) return null;
        try {
            int rgb = Integer.parseInt(hex6, 16) & 0xFFFFFF;
            return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}