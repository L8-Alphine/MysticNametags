package com.mystichorizons.mysticnametags.nameplate.banner;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.license.MysticNameTagsLicense;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Drop-in banner art for tag nameplates.
 *
 * <p>Server owners put {@code .png} files in {@code <dataDir>/images/}. Each one is registered with
 * {@link CommonAssetModule} so the client downloads it on demand, exactly the way TaleBoard ships
 * its scoreboard icons. Registration also pushes the asset to players who are <em>already</em>
 * connected, so {@code /tags reload} picks up new art without a restart.</p>
 *
 * <p>Assets are named {@code NPC/MysticNameTags/banners/<file>.png} to sit alongside the bundled
 * glyph textures ({@code NPC/MysticNameTags/glyph_lo_a.png}); the prefix is relative to
 * {@code Common/}.</p>
 */
public final class BannerAssetManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Asset pack name assets are registered under. */
    public static final String PACK_NAME = "MysticNameTags";

    /** Client-facing asset prefix, relative to {@code Common/}. */
    public static final String BANNER_SUBPATH = "NPC/MysticNameTags/banners/";

    /** Directory (under the plugin data folder) owners drop art into. */
    public static final String IMAGES_DIR_NAME = "images";

    private static volatile BannerAssetManager instance;

    private final Path imagesDir;
    private final Map<String, BannerInfo> banners = new ConcurrentHashMap<>();

    private BannerAssetManager(@Nonnull Path dataDir) {
        this.imagesDir = dataDir.resolve(IMAGES_DIR_NAME);
    }

    public static void init(@Nonnull Path dataDir) {
        BannerAssetManager manager = new BannerAssetManager(dataDir);
        manager.prepareDirectories();
        instance = manager;
    }

    @Nullable
    public static BannerAssetManager get() {
        return instance;
    }

    @Nonnull
    public Path getImagesDir() {
        return imagesDir;
    }

    private void prepareDirectories() {
        try {
            Files.createDirectories(imagesDir);
            writeReadmeIfAbsent();
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Could not create banner image directories.");
        }
    }

    /**
     * Scans {@code images/} and registers every valid PNG as a common asset.
     *
     * @return number of banners registered
     */
    public synchronized int scanAndRegister() {
        // Licensed feature: don't push banner art to clients that can never display it.
        // reloadAll() re-reads the license before calling this, so adding one and running
        // /tags reload registers the art without a restart.
        if (!MysticNameTagsLicense.bannersLicensed()) {
            banners.clear();
            BannerQuadModels.invalidate();
            return 0;
        }

        CommonAssetModule module;
        try {
            module = CommonAssetModule.get();
        } catch (Throwable t) {
            module = null;
        }

        if (module == null) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] CommonAssetModule unavailable; tag banners not registered.");
            return 0;
        }

        long maxBytes = Settings.get().getBannerMaxFileBytes();
        Map<String, BannerInfo> scanned = new ConcurrentHashMap<>();
        int registered = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(imagesDir, "*.png")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }

                String fileName = file.getFileName().toString();

                try {
                    long size = Files.size(file);
                    if (size > maxBytes) {
                        LOGGER.at(Level.WARNING).log("[MysticNameTags] Banner '" + fileName + "' is "
                                + size + " bytes, over the " + maxBytes
                                + " byte limit (bannerMaxFileBytes). Skipped.");
                        continue;
                    }

                    BufferedImage image = ImageIO.read(file.toFile());
                    if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                        LOGGER.at(Level.WARNING)
                                .log("[MysticNameTags] Banner '" + fileName + "' is not a readable PNG. Skipped.");
                        continue;
                    }

                    byte[] bytes = Files.readAllBytes(file);
                    String texturePath = BANNER_SUBPATH + fileName;

                    module.addCommonAsset(PACK_NAME, new FileCommonAsset(file, texturePath, bytes), false);

                    BannerInfo info = new BannerInfo(
                            normalizeName(fileName),
                            fileName,
                            texturePath,
                            image.getWidth(),
                            image.getHeight()
                    );
                    scanned.put(info.name(), info);
                    registered++;
                } catch (Throwable t) {
                    LOGGER.at(Level.WARNING).withCause(t)
                            .log("[MysticNameTags] Failed to register banner: " + fileName);
                }
            }
        } catch (IOException e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to scan banner directory: " + imagesDir);
        }

        banners.clear();
        banners.putAll(scanned);

        // Quad models are keyed off image dimensions, so stale ones are meaningless after a rescan.
        BannerQuadModels.invalidate();

        LOGGER.at(Level.INFO).log("[MysticNameTags] Registered " + registered + " tag banner(s) from " + imagesDir);
        return registered;
    }

    /**
     * Resolves a {@code banner} config value to a registered banner.
     *
     * <p>Accepts {@code "legend"}, {@code "legend.png"}, {@code "images/legend.png"} and
     * {@code "mods/MysticNameTags/images/legend.png"} — all point at the same file.</p>
     */
    @Nullable
    public BannerInfo find(@Nullable String configValue) {
        String key = normalizeName(configValue);
        return key.isEmpty() ? null : banners.get(key);
    }

    public boolean has(@Nullable String configValue) {
        return find(configValue) != null;
    }

    @Nonnull
    public List<String> listBannerNames() {
        List<String> names = new ArrayList<>(banners.keySet());
        names.sort(String::compareTo);
        return names;
    }

    /**
     * Strips any directory prefix and {@code .png} suffix, then lowercases, so every spelling of a
     * banner reference collapses to one key.
     */
    @Nonnull
    public static String normalizeName(@Nullable String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim().replace('\\', '/');
        if (trimmed.isEmpty()) {
            return "";
        }

        int slash = trimmed.lastIndexOf('/');
        if (slash >= 0) {
            trimmed = trimmed.substring(slash + 1);
        }

        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".png")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }

    private void writeReadmeIfAbsent() throws IOException {
        Path readme = imagesDir.resolve("README.txt");
        if (Files.exists(readme)) {
            return;
        }

        String text = String.join(System.lineSeparator(),
                "MysticNameTags tag banners",
                "==========================",
                "",
                "Drop .png files in this folder to use them as nameplate banners.",
                "",
                "  1. Add legend.png here.",
                "  2. In tags.json, give a tag:  \"banner\": \"legend\"",
                "     (\"legend.png\" and \"images/legend.png\" work too.)",
                "  3. Run /tags reload.",
                "",
                "A tag with a banner renders that image above the player's head instead of its",
                "text display. The tag's \"display\" value is still used in chat, the /tags menu",
                "and placeholders, so keep it set.",
                "",
                "Requirements",
                "  - experimentalGlyphNameplatesEnabled must be true in settings.json. Banners ride",
                "    the glyph nameplate pipeline; with it off, banner tags fall back to text.",
                "  - bannersEnabled must be true in settings.json.",
                "",
                "Sizing",
                "  - 160 pixels = 1 block wide. A 256x64 banner renders 1.6 blocks across.",
                "  - Banners wider than bannerMaxWidthBlocks are scaled down, keeping their aspect.",
                "  - Transparency works; the image renders fullbright and double-sided.",
                "  - Files over bannerMaxFileBytes (default 256 KB) are skipped with a warning.",
                "",
                "Animated GIFs are not supported - the Hytale client decodes PNG and SVG only.",
                "",
                "New or changed PNGs are picked up by /tags reload and pushed to players who are",
                "already connected.",
                ""
        );

        Files.writeString(readme, text, StandardCharsets.UTF_8);
    }
}
