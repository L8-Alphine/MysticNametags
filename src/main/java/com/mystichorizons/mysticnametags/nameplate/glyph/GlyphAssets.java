package com.mystichorizons.mysticnametags.nameplate.glyph;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Locale;

public final class GlyphAssets {


    public static final String NAMESPACE = "mysticnametags";
    public static final String DEFAULT_FONT = "default";

    private GlyphAssets() {}

    public static String modelId(String safeCharId) {
        return modelId(DEFAULT_FONT, safeCharId);
    }

    public static String modelId(String font, String safeCharId) {
        // e.g. mysticnametags:Glyph_lo_a
        String normalizedFont = normalizeFont(font);
        if (!DEFAULT_FONT.equals(normalizedFont)) {
            return NAMESPACE + ":Glyph_" + normalizedFont + "_" + safeCharId;
        }

        return NAMESPACE + ":Glyph_" + safeCharId;
    }

    public static String texturePath(char ch, String safeCharId) {
        return texturePath(ch, safeCharId, DEFAULT_FONT);
    }

    public static String texturePath(char ch, String safeCharId, String font) {
        String normalizedFont = normalizeFont(font);
        String prefix = DEFAULT_FONT.equals(normalizedFont)
                ? "NPC/MysticNameTags/"
                : "NPC/MysticNameTags/" + normalizedFont + "/";

        if (ch >= 'A' && ch <= 'Z') {
            return prefix + "glyph_up_" + ch + ".png";
        }

        return prefix + "glyph_" + safeCharId + ".png";
    }

    public static String normalizeFont(@Nullable String font) {
        if (font == null || font.isBlank()) {
            return DEFAULT_FONT;
        }

        String value = font.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "base", "root", "default" -> DEFAULT_FONT;
            case "comic", "cursive", "impact", "mono", "sans", "serif", "thin" -> value;
            default -> DEFAULT_FONT;
        };
    }

    public static String slotModelPath(int offsetPx) {
        int rounded = Math.max(-128, Math.min(128, offsetPx));
        String sign = rounded < 0 ? "m" : "p";
        return "NPC/MysticNameTags/GlyphSlot_" + sign + String.format("%03d", Math.abs(rounded)) + ".blockymodel";
    }

    public static String tintEffectId(int rgbQuantized) {
        // e.g. mysticnametags:HtTint_FF00AA
        return NAMESPACE + ":HtTint_" + String.format("%06X", (rgbQuantized & 0xFFFFFF));
    }

    public static int rgb(Color c) {
        return ((c.getRed() & 0xFF) << 16)
                | ((c.getGreen() & 0xFF) << 8)
                | (c.getBlue() & 0xFF);
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
