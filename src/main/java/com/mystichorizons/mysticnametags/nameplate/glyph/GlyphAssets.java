package com.mystichorizons.mysticnametags.nameplate.glyph;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Locale;

public final class GlyphAssets {

    public static final String NAMESPACE = "mysticnametags";
    public static final String DEFAULT_FONT = "default";

    /**
     * Where the glyph PNGs live inside the mod jar, one directory per family.
     *
     * <p>Deliberately outside the asset pack ({@code Common/}). Every texture under
     * {@code Common/NPC/} is delivered to, and atlased by, every client whether or not the
     * font is ever drawn, and eight families is 768 atlas entries. Only the configured family
     * is pushed, at runtime, by {@link GlyphAssetManager}.</p>
     */
    public static final String RESOURCE_ROOT = "glyphfonts/";

    /** Client-facing prefix the active family's textures are registered under, relative to {@code Common/}. */
    public static final String CLIENT_SUBPATH = "NPC/MysticNameTags/glyphs/";

    /**
     * Glyph texture layout. Every PNG is a {@value #TEXTURE_CANVAS}x{@value #TEXTURE_CANVAS}
     * canvas with the ink cell at ({@value #TEXTURE_GUTTER}, {@value #TEXTURE_GUTTER}); the
     * slot models put their UV box on that cell.
     *
     * <p>Two client rules drive this. The entity texture atlas refuses anything smaller than
     * 32x32 or not a multiple of 32 ({@code Texture width/height must be a multiple of 32 and
     * at least 32x32} in the client log), which is what turned the old 16x16 set into tinted
     * blocks on Update 6. And the quad renderer samples slightly outside the UV box at
     * grazing angles and coarse mips, so ink sitting on the texture edge picks up whatever
     * the atlas packed next to it as a thin flickering line. A transparent gutter of 32px
     * survives every mip level; narrower gutters were tried elsewhere and did not.</p>
     */
    public static final int TEXTURE_CANVAS = 96;
    public static final int TEXTURE_GUTTER = 32;

    /** Ink cell edge, in texels and model units. The default family is a 16px pixel font, the rest are 32px. */
    public static final int DEFAULT_CELL = 16;
    public static final int FONT_CELL = 32;

    // Keep in sync with glyphSlotMin/glyphSlotMax/glyphSlotStep in build.gradle, which
    // generates one GlyphSlot_*.blockymodel (and GlyphSlot32_*) per step across this range.
    public static final int MIN_SLOT_OFFSET = -256;
    public static final int MAX_SLOT_OFFSET = 256;
    public static final int SLOT_OFFSET_STEP = 4;

    /**
     * Longest line the slot grid can place without clamping. A line of n glyphs spans
     * offsets up to {@code 4 * (n - 1)}; past this the outermost glyphs collide on the
     * clamp instead of spreading out.
     */
    public static final int MAX_GLYPHS_PER_LINE = MAX_SLOT_OFFSET / SLOT_OFFSET_STEP + 1;

    private GlyphAssets() {}

    /** Ink cell edge for a family: 16 for the default pixel font, 32 for the others. */
    public static int cellSize(@Nullable String font) {
        return DEFAULT_FONT.equals(normalizeFont(font)) ? DEFAULT_CELL : FONT_CELL;
    }

    /** Where the PNG lives inside the mod jar. */
    public static String resourceTexturePath(char ch, String safeCharId, String font) {
        return RESOURCE_ROOT + normalizeFont(font) + "/" + fileName(ch, safeCharId);
    }

    public static String resourceFallbackPath(String font) {
        return RESOURCE_ROOT + normalizeFont(font) + "/" + fallbackFileName();
    }

    public static String texturePath(char ch, String safeCharId) {
        return texturePath(ch, safeCharId, DEFAULT_FONT);
    }

    /**
     * The texture path sent to the client, which is also the path the glyph is registered
     * under as a common asset.
     */
    public static String texturePath(char ch, String safeCharId, String font) {
        return CLIENT_SUBPATH + normalizeFont(font) + "/" + fileName(ch, safeCharId);
    }

    public static String fallbackTexturePath(String font) {
        return CLIENT_SUBPATH + normalizeFont(font) + "/" + fallbackFileName();
    }

    public static String fallbackFileName() {
        return "glyph_fallback.png";
    }

    /** {@code glyph_<id>.png}, matching the bundled file names. */
    private static String fileName(char ch, String safeCharId) {
        String leaf = (ch >= 'A' && ch <= 'Z') ? "glyph_up_" + ch : "glyph_" + safeCharId;
        return leaf + ".png";
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
        return slotModelPath(offsetPx, DEFAULT_CELL);
    }

    /**
     * The generated slot model for a horizontal offset and ink cell size:
     * {@code GlyphSlot_p012} for the 16px family, {@code GlyphSlot32_p012} for the 32px ones.
     */
    public static String slotModelPath(int offsetPx, int cellSize) {
        int clamped = Math.max(MIN_SLOT_OFFSET, Math.min(MAX_SLOT_OFFSET, offsetPx));
        int quantized = Math.round((float) clamped / SLOT_OFFSET_STEP) * SLOT_OFFSET_STEP;
        String sign = quantized < 0 ? "m" : "p";
        String set = cellSize == DEFAULT_CELL ? "GlyphSlot_" : "GlyphSlot" + cellSize + "_";
        return "NPC/MysticNameTags/" + set + sign + String.format("%03d", Math.abs(quantized)) + ".blockymodel";
    }

    /**
     * Cache-unique id for one glyph entity's model, e.g.
     * {@code mysticnametags:GlyphSlot_p012__glyph_lo_a}.
     *
     * <p>The client keys cached models by {@code assetId}, so two entities sharing an id share
     * a model - including its texture. Every distinct slot/texture pair therefore needs its own
     * id, the same way each banner quad size gets one. The id does not need to resolve to a
     * registered asset; {@code BannerQuad_*} does not either.</p>
     */
    public static String slotAssetId(String slotModelPath, String texturePath) {
        return NAMESPACE + ":" + baseName(slotModelPath) + "__" + baseName(texturePath);
    }

    private static String baseName(@Nullable String path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
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
