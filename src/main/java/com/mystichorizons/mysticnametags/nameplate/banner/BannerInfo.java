package com.mystichorizons.mysticnametags.nameplate.banner;

import javax.annotation.Nonnull;

/**
 * A banner image that has been registered as a client-downloadable common asset.
 *
 * @param name        normalized lookup key (lowercase, no extension)
 * @param fileName    on-disk file name, e.g. {@code legend.png}
 * @param texturePath asset path the client resolves, e.g. {@code NPC/MysticNameTags/banners/legend.png}
 * @param widthPx     source image width in pixels
 * @param heightPx    source image height in pixels
 */
public record BannerInfo(@Nonnull String name,
                         @Nonnull String fileName,
                         @Nonnull String texturePath,
                         int widthPx,
                         int heightPx) {

    public double aspect() {
        return heightPx <= 0 ? 1.0d : (double) widthPx / (double) heightPx;
    }

    /**
     * Identity for nameplate change detection. Includes the pixel size so that replacing the art
     * with a differently-shaped image and reloading rebuilds the quad instead of reusing the old
     * one at the wrong aspect ratio.
     */
    @Nonnull
    public String renderKey() {
        return texturePath + "@" + widthPx + "x" + heightPx;
    }
}
