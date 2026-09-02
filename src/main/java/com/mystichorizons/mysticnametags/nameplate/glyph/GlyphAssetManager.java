package com.mystichorizons.mysticnametags.nameplate.glyph;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.universe.Universe;
import com.mystichorizons.mysticnametags.nameplate.banner.BannerAssetManager;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Pushes the configured glyph family's textures to clients.
 *
 * <p>The glyph PNGs are jar resources under {@link GlyphAssets#RESOURCE_ROOT}, not asset-pack
 * files, so the client never sees a family it is not drawing. The active family is extracted
 * to {@code <dataDir>/cache/} and registered through {@link CommonAssetModule}, the same way
 * banner art is. A full set is 96 files per family, so one font costs one block of atlas rows
 * rather than eight.</p>
 *
 * <p>The extraction step is not optional: {@link FileCommonAsset#getBlob0()} serves the blob
 * with {@code Files.readAllBytes(file)} and ignores the {@code byte[]} handed to its
 * constructor. A jar resource registered directly appears to succeed, then fails when a client
 * asks for it.</p>
 */
public final class GlyphAssetManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Same pack as banners, so both arrive under one asset pack. */
    public static final String PACK_NAME = BannerAssetManager.PACK_NAME;

    /** Where jar-bundled glyph PNGs are unpacked so they exist as real files. */
    public static final String CACHE_DIR_NAME = "cache";

    private static volatile String registeredFont = null;

    private GlyphAssetManager() {
    }

    /**
     * Registers every glyph texture for {@code font} (plus the family's fallback) as a common
     * asset.
     *
     * @return number of textures registered
     */
    public static synchronized int registerFont(@Nonnull Path dataDir, @Nonnull String font) {
        String normalized = GlyphAssets.normalizeFont(font);

        CommonAssetModule module;
        try {
            module = CommonAssetModule.get();
        } catch (Throwable t) {
            module = null;
        }

        if (module == null) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] CommonAssetModule unavailable; glyph textures not registered.");
            return 0;
        }

        // resource-in-jar -> path the client is told. Deduplicated because A-Z share the
        // glyph_up_* naming with their safe ids.
        Map<String, String> textures = new LinkedHashMap<>();
        textures.put(GlyphAssets.resourceFallbackPath(normalized), GlyphAssets.fallbackTexturePath(normalized));

        for (char ch : GlyphInfoCompat.supportedChars()) {
            String safeId = GlyphInfoCompat.getSafeIdLower(ch);
            if (safeId == null) {
                continue;
            }
            textures.put(GlyphAssets.resourceTexturePath(ch, safeId, normalized),
                    GlyphAssets.texturePath(ch, safeId, normalized));
        }

        Path cacheRoot = dataDir.resolve(CACHE_DIR_NAME);
        int registered = 0;
        int missing = 0;
        FileCommonAsset probe = null;
        Path probePath = null;
        String probeTexture = null;

        for (Map.Entry<String, String> entry : textures.entrySet()) {
            String resourcePath = entry.getKey();
            String texturePath = entry.getValue();
            byte[] bytes = readResource(resourcePath);
            if (bytes == null) {
                missing++;
                continue;
            }

            try {
                // Mirror the client-facing layout under the cache dir so each asset has a real
                // file. Compare bytes, not sizes: a re-drawn glyph can keep its byte count.
                Path onDisk = cacheRoot.resolve(texturePath);
                Files.createDirectories(onDisk.getParent());
                if (!Files.exists(onDisk) || !Arrays.equals(Files.readAllBytes(onDisk), bytes)) {
                    Files.write(onDisk, bytes);
                }

                FileCommonAsset asset = new FileCommonAsset(onDisk, texturePath, bytes);
                module.addCommonAsset(PACK_NAME, asset, false);
                registered++;

                // Prove the asset can actually serve its bytes. getBlob0() re-reads the Path,
                // so a registration that "succeeds" still fails at delivery if the file is not
                // really there. Probing one asset turns that into a log line instead of a
                // silently blank texture.
                if (probe == null) {
                    probe = asset;
                    probePath = onDisk;
                    probeTexture = texturePath;
                }
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Failed to register glyph texture: " + texturePath);
            }
        }

        String previous = registeredFont;
        registeredFont = normalized;

        if (missing > 0) {
            LOGGER.at(Level.WARNING).log("[MysticNameTags] " + missing
                    + " glyph texture(s) for font '" + normalized + "' were not found in the mod jar.");
        }

        LOGGER.at(Level.INFO).log("[MysticNameTags] Registered " + registered
                + " glyph texture(s) for font '" + normalized + "' under "
                + GlyphAssets.CLIENT_SUBPATH + normalized + "/ (cache: " + cacheRoot + ").");

        if (probe != null) {
            try {
                boolean onDiskExists = Files.exists(probePath);
                long onDiskSize = onDiskExists ? Files.size(probePath) : -1L;
                byte[] blob = probe.getBlob0().get(5, java.util.concurrent.TimeUnit.SECONDS);
                LOGGER.at(Level.INFO).log("[MysticNameTags] Glyph asset probe: texture='"
                        + probeTexture + "' file=" + probePath + " exists=" + onDiskExists
                        + " size=" + onDiskSize + " blobBytes=" + (blob == null ? -1 : blob.length)
                        + " hash=" + probe.getHash());
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Glyph asset probe FAILED for '" + probeTexture
                                + "' (file=" + probePath + "). Textures will render blank.");
            }
        }

        // addCommonAsset streams each file to connected clients but never asks them to rebuild
        // their texture atlases, and an entity texture that is not in the atlas draws as an
        // untextured quad. Players who join later get an atlas built with these textures in
        // it; players already online need one rebuild request, the same packet the server
        // sends for its own asset reloads. Only on a font change: at boot nobody is connected.
        if (registered > 0 && previous != null && !previous.equals(normalized)) {
            try {
                Universe universe = Universe.get();
                if (universe != null && universe.getPlayerCount() > 0) {
                    universe.broadcastPacketNoCache(new RequestCommonAssetsRebuild());
                }
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Could not request a client asset rebuild after switching glyph font; "
                                + "online players see the new font after they reconnect.");
            }
        }

        return registered;
    }

    /** Re-registers only when the configured font actually changed. */
    public static int registerFontIfChanged(@Nonnull Path dataDir, @Nonnull String font) {
        String normalized = GlyphAssets.normalizeFont(font);
        if (normalized.equals(registeredFont)) {
            return 0;
        }
        return registerFont(dataDir, normalized);
    }

    private static byte[] readResource(@Nonnull String resourcePath) {
        try (InputStream in = GlyphAssetManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            return in == null ? null : in.readAllBytes();
        } catch (Throwable t) {
            return null;
        }
    }
}
