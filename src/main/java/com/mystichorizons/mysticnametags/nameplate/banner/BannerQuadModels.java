package com.mystichorizons.mysticnametags.nameplate.banner;

import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphAssets;
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphInfoCompat;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Picks the flat quad a banner is painted onto.
 *
 * <p>The quads are <em>bundled</em> assets, not generated at runtime. That mirrors how the glyph
 * system already works — {@code GlyphQuad.blockymodel} and the {@code GlyphSlot_*} models ship in
 * {@code Common/NPC/MysticNameTags/} and are referenced by path over the wire while the texture is
 * an arbitrary string. Since glyph nameplates render, that whole path is proven; a
 * {@code .blockymodel} registered at runtime through {@code CommonAssetModule} is not, and an
 * unloadable model shows nothing at all.</p>
 *
 * <p>Only the <em>aspect ratio</em> comes from the model. Actual size is applied as an entity
 * scale, so one quad per ratio covers every banner of that shape at any size.</p>
 *
 * <p>Sizing follows the glyph system's scale: a 16-unit quad is {@link GlyphInfoCompat#CHAR_WIDTH}
 * blocks wide, so one block is {@value #UNITS_PER_BLOCK} units.</p>
 */
public final class BannerQuadModels {

    private static final double UNITS_PER_BLOCK = 16.0d / GlyphInfoCompat.CHAR_WIDTH;

    /**
     * Bundled quads, widest aspect first. The recommended 256x64 banner matches
     * {@code BannerQuad_256x64} exactly, so the common case has zero distortion.
     */
    private static final Quad[] LADDER = {
            new Quad(256, 32),   // 8:1
            new Quad(256, 43),   // ~6:1
            new Quad(256, 64),   // 4:1  <- recommended banner shape
            new Quad(256, 85),   // ~3:1
            new Quad(256, 128),  // 2:1
            new Quad(192, 128),  // 3:2
            new Quad(128, 128),  // 1:1
            new Quad(85, 128),   // ~2:3
            new Quad(64, 128)    // 1:2
    };

    private BannerQuadModels() {
    }

    /** Kept for API compatibility with the reload path; the bundled ladder has nothing to cache. */
    public static void invalidate() {
        // no-op
    }

    /**
     * Resolves the quad and entity scale to render a banner at.
     *
     * <p>Both caps preserve aspect ratio and the tighter one wins, so a tall image is limited by
     * its height rather than growing upward without bound.</p>
     *
     * @param banner          registered banner
     * @param maxWidthBlocks  upper bound on rendered width; already clamped by Settings
     * @param maxHeightBlocks upper bound on rendered height; already clamped by Settings
     * @param scale           per-tag multiplier applied before the caps
     */
    @Nullable
    public static Rendered resolve(@Nonnull BannerInfo banner,
                                   double maxWidthBlocks,
                                   double maxHeightBlocks,
                                   double scale) {

        double safeScale = scale <= 0.0d ? 1.0d : scale;
        double width = banner.widthPx() * safeScale;
        double height = banner.heightPx() * safeScale;

        if (width <= 0.0d || height <= 0.0d) {
            return null;
        }

        // Settings guarantees both caps are positive, so honour them exactly - flooring them here
        // would silently ignore any configured value below one block.
        double maxWidthUnits = maxWidthBlocks * UNITS_PER_BLOCK;
        double maxHeightUnits = maxHeightBlocks * UNITS_PER_BLOCK;

        double fit = Math.min(
                width > maxWidthUnits ? maxWidthUnits / width : 1.0d,
                height > maxHeightUnits ? maxHeightUnits / height : 1.0d
        );

        if (fit < 1.0d) {
            width *= fit;
            height *= fit;
        }

        Quad quad = nearestQuad(banner.aspect());

        // Uniform entity scale, so take the tighter axis to stay inside both caps.
        float entityScale = (float) Math.min(width / quad.width, height / quad.height);
        if (entityScale <= 0.0f || !Float.isFinite(entityScale)) {
            return null;
        }

        com.hypixel.hytale.protocol.Model model = new com.hypixel.hytale.protocol.Model();
        model.assetId = GlyphAssets.NAMESPACE + ":" + quad.assetName();
        model.path = quad.path();
        model.texture = banner.texturePath();
        model.scale = 1.0f;

        return new Rendered(model, entityScale);
    }

    /**
     * Nearest ladder entry by ratio rather than by difference, so a 1:2 image is judged as far
     * from 1:1 as a 2:1 image is.
     */
    @Nonnull
    private static Quad nearestQuad(double aspect) {
        double target = Math.log(aspect <= 0.0d ? 1.0d : aspect);

        Quad best = LADDER[0];
        double bestDistance = Double.MAX_VALUE;

        for (Quad quad : LADDER) {
            double distance = Math.abs(Math.log(quad.aspect()) - target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = quad;
            }
        }

        return best;
    }

    /** A bundled quad plus the entity scale that renders it at the requested size. */
    public record Rendered(@Nonnull com.hypixel.hytale.protocol.Model model, float entityScale) {
    }

    private record Quad(int width, int height) {
        double aspect() {
            return (double) width / (double) height;
        }

        String assetName() {
            return "BannerQuad_" + width + "x" + height;
        }

        String path() {
            return "NPC/MysticNameTags/" + assetName() + ".blockymodel";
        }
    }
}
