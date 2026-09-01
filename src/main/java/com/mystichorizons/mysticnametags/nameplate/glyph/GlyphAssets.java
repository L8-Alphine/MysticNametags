package com.mystichorizons.mysticnametags.nameplate.glyph;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.Locale;

public final class GlyphAssets {

    public static final String NAMESPACE = "mysticnametags";
    public static final String DEFAULT_FONT = "default";

    /** Where glyph PNGs sit in the mod asset pack, relative to {@code Common/}. */
    public static final String PACK_SUBPATH = "NPC/MysticNameTags/";

    /**
     * Where glyph textures are registered and referenced for the client.
     *
     * <p>Must differ from {@link #PACK_SUBPATH}; see {@link #texturePath(char, String, String)}.</p>
     */
    // ---- TEMPORARY DIAGNOSTIC ----
    // Registration is proven healthy (probe: exists=true, blobBytes=157) yet glyph quads stay
    // blank, while a banner texture renders on the very same model. The only two variables left
    // are this prefix and the image itself. Borrowing the banner prefix - the one prefix known
    // to work - separates them: glyphs appearing means the prefix mattered, glyphs still blank
    // means it is the 16x16 image, not the path.
    public static final String CLIENT_SUBPATH = "NPC/MysticNameTags/banners/";
    // ---- END DIAGNOSTIC (restore: NPC/MysticNameTags/glyphs/) ----
    // Keep in sync with glyphSlotMin/glyphSlotMax/glyphSlotStep in build.gradle, which
    // generates one GlyphSlot_*.blockymodel per step across this range.
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

    /**
     * Where the PNG lives inside the mod jar, under {@code Common/}.
     *
     * <p>Kept separate from {@link #texturePath} because the two must NOT be equal: see the note
     * there.</p>
     */
    public static String resourceTexturePath(char ch, String safeCharId, String font) {
        return PACK_SUBPATH + fileName(ch, safeCharId, font);
    }

    public static String texturePath(char ch, String safeCharId) {
        return texturePath(ch, safeCharId, DEFAULT_FONT);
    }

    /**
     * The texture path sent to the client, which must be the path the glyph is registered under
     * as a common asset.
     *
     * <p>Deliberately under {@code glyphs/} rather than the pack location. The mod ships these
     * same PNGs in its asset pack at {@code NPC/MysticNameTags/...}; registering a common asset
     * on top of a path the pack already claims does not take effect, and the quad renders
     * untextured. Banner art never hit this because it lives under {@code banners/}, a prefix the
     * pack does not contain. Verified on Update 6: pointing a glyph quad at a banner texture
     * rendered immediately, while the identical quad with a pack-colliding glyph path stayed
     * blank.</p>
     */
    public static String texturePath(char ch, String safeCharId, String font) {
        return CLIENT_SUBPATH + fileName(ch, safeCharId, font);
    }

    /** Shared {@code [<font>/]glyph_<id>.png} tail used by both path forms. */
    private static String fileName(char ch, String safeCharId, String font) {
        String normalizedFont = normalizeFont(font);
        String dir = DEFAULT_FONT.equals(normalizedFont) ? "" : normalizedFont + "/";
        String leaf = (ch >= 'A' && ch <= 'Z') ? "glyph_up_" + ch : "glyph_" + safeCharId;
        return dir + leaf + ".png";
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
        int clamped = Math.max(MIN_SLOT_OFFSET, Math.min(MAX_SLOT_OFFSET, offsetPx));
        int quantized = Math.round((float) clamped / SLOT_OFFSET_STEP) * SLOT_OFFSET_STEP;
        String sign = quantized < 0 ? "m" : "p";
        return "NPC/MysticNameTags/GlyphSlot_" + sign + String.format("%03d", Math.abs(quantized)) + ".blockymodel";
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
