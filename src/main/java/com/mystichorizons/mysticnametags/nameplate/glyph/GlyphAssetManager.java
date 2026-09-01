package com.mystichorizons.mysticnametags.nameplate.glyph;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.mystichorizons.mysticnametags.nameplate.banner.BannerAssetManager;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * Pushes the bundled glyph textures to clients.
 *
 * <p>Shipping a PNG inside the mod's asset pack makes it available to the <em>server</em>, but a
 * texture named by a packet-sent {@code Model} is only drawn once the client actually holds the
 * file. Banner art has always gone through {@link CommonAssetModule} and renders; the bundled
 * glyph textures did not, and glyph quads drew blank - correctly positioned, correctly tinted,
 * and untextured. Registering them the same way closes that gap.</p>
 *
 * <p>Only the font in use is registered. A full set is 96 files per family and there are eight
 * families, so pushing all of them would mean 768 needless downloads per client.</p>
 *
 * <p>The PNGs are extracted from the mod jar to {@code <dataDir>/cache/glyphs/} first, because
 * {@link FileCommonAsset#getBlob0()} serves the blob with {@code Files.readAllBytes(file)} and
 * ignores the {@code byte[]} handed to its constructor. A jar resource therefore cannot be
 * registered directly: the asset appears to register, then fails when a client asks for it.</p>
 */
public final class GlyphAssetManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Same pack as banners, so both arrive under one asset pack. */
    public static final String PACK_NAME = BannerAssetManager.PACK_NAME;

    private static final String FALLBACK_FILE = "glyph_fallback.png";

    /** Where jar-bundled glyph PNGs are unpacked so they exist as real files. */
    public static final String CACHE_DIR_NAME = "cache";

    private static volatile String registeredFont = null;

    private GlyphAssetManager() {
    }

    /**
     * Registers every glyph texture for {@code font} (plus the shared fallback) as a common asset.
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

        // resource-in-jar -> path the client is told. These must differ: a common asset
        // registered on a path the mod asset pack already claims does not take effect.
        // Deduplicated because A-Z share the glyph_up_* naming with their safe ids.
        java.util.Map<String, String> textures = new java.util.LinkedHashMap<>();
        textures.put(GlyphAssets.PACK_SUBPATH + FALLBACK_FILE,
                GlyphAssets.CLIENT_SUBPATH + FALLBACK_FILE);

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

        for (java.util.Map.Entry<String, String> entry : textures.entrySet()) {
            String resourcePath = entry.getKey();
            String texturePath = entry.getValue();
            byte[] bytes = readResource("Common/" + resourcePath);
            if (bytes == null) {
                missing++;
                continue;
            }

            try {
                // Mirror the pack layout under the cache dir so each asset has a real file.
                Path onDisk = cacheRoot.resolve(texturePath);
                Files.createDirectories(onDisk.getParent());
                if (!Files.exists(onDisk) || Files.size(onDisk) != bytes.length) {
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

        registeredFont = normalized;

        if (missing > 0) {
            LOGGER.at(Level.WARNING).log("[MysticNameTags] " + missing
                    + " glyph texture(s) for font '" + normalized + "' were not found in the mod jar.");
        }

        LOGGER.at(Level.INFO).log("[MysticNameTags] Registered " + registered
                + " glyph texture(s) for font '" + normalized + "' under "
                + GlyphAssets.CLIENT_SUBPATH + " (cache: " + cacheRoot + ").");

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
