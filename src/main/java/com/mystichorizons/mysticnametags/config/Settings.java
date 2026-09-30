package com.mystichorizons.mysticnametags.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphAssets;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class Settings {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static Settings INSTANCE;

    // Core Settings
    private String nameplateFormat = "{rank} {name} {tag}";
    private String nameplatePreset = "CUSTOM";
    private boolean stripExtraSpaces = true;
    private String language = "en_US";

    /**
     * Delay in seconds before a player can EQUIP a *different* tag again.
     * 0 = no cooldown.
     */
    private int tagDelaysecs = 20;

    // ---------------------------------------------------------------------
    // Storage backend (FILE / SQLITE / MYSQL / MARIADB / REDIS)
    // ---------------------------------------------------------------------

    private String storageBackend = "FILE"; // FILE, SQLITE, MYSQL, MARIADB, REDIS

    // SQLite options (relative to plugin data folder)
    private String sqliteFile = "playerdata.db";

    // MySQL / MariaDB options (both backends share these)
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "mysticnametags";
    private String mysqlUser = "root";
    private String mysqlPassword = "password";

    // Redis options (used by storageBackend=REDIS and by redisSyncEnabled)
    private String redisHost = "localhost";
    private int redisPort = 6379;
    /** Redis 6+ ACL username; blank uses the server's default user. */
    @SerializedName(value = "redisUser", alternate = {"redisUsername"})
    private String redisUser = "";
    private String redisPassword = "";
    private int redisDatabase = 0;
    private boolean redisSsl = false;
    private String redisKeyPrefix = "mysticnametags:";
    private int redisTimeoutMs = 2000;
    private int redisPoolSize = 8;

    /**
     * Cross-server tag sync over Redis pub/sub. Independent of the storage
     * backend: pair it with MYSQL to keep durability in SQL while still
     * pushing tag changes to the rest of the network instantly.
     */
    private boolean redisSyncEnabled = false;

    /**
     * Identifies this server in sync messages so it ignores its own
     * broadcasts. Blank = derive one automatically at startup.
     */
    private String networkServerId = "";

    // Playtime Setup
    private String playtimeProvider = "AUTO"; // AUTO, INTERNAL, ZIB_PLAYTIME, NONE

    // --- Nameplate toggles ------------------------------------------------------

    /** Master toggle for MysticNameTags nameplates (default ON). */
    private boolean nameplatesEnabled = true;

    /** If true, use a default tag when player has no equipped tag. */
    private boolean defaultTagEnabled = false;

    /** Tag id from tags.json to use as default (e.g. "mystic"). */
    private String defaultTagId = "mystic";

    /** EndlessLeveling integration (default off). */
    private boolean endlessLevelingNameplatesEnabled = false;

    /** EndlessLeveling Integration for RACE DISPLAY */
    private boolean endlessRaceDisplay = false;

    /** EndlessLeveling prestige display toggle. */
    private boolean endlessPrestigeDisplay = false;

    /** EndlessLeveling primary class display toggle. */
    private boolean endlessPrimaryClassDisplay = false;

    /** EndlessLeveling secondary class display toggle. */
    private boolean endlessSecondaryClassDisplay = false;

    /** Prefix used before prestige number in EL nameplates. Example: "P" -> "P3". */
    private String endlessPrestigePrefix = "P";

    // --- Placeholder toggles -------------------------------------------------

    /**
     * If true, MysticNameTags will run WiFlowPlaceholderAPI on the built nameplate text.
     * If auto-detect is enabled, this value will be overwritten by detection.
     */
    private boolean wiFlowPlaceholdersEnabled = false;

    /**
     * If true, MysticNameTags will run at.helpch.placeholderapi on the built nameplate text.
     * If auto-detect is enabled, this value will be overwritten by detection.
     */
    private boolean helpchPlaceholderApiEnabled = false;

    /**
     * NEW: If true, plugin will auto-detect WiFlowPlaceholderAPI presence and toggle wiFlowPlaceholdersEnabled.
     * If false, wiFlowPlaceholdersEnabled is treated as an explicit admin override.
     */
    private boolean wiFlowPlaceholdersAutoDetect = true;

    /**
     * NEW: If true, plugin will auto-detect at.helpch PlaceholderAPI presence and toggle helpchPlaceholderApiEnabled.
     * If false, helpchPlaceholderApiEnabled is treated as an explicit admin override.
     */
    private boolean helpchPlaceholderApiAutoDetect = true;

    // --- Economy / permission / RPG flags -----------------------------------

    private boolean economySystemEnabled = true;
    private boolean useCoinSystem = false;
    private boolean usePhysicalCoinEconomy = false;
    private boolean fullPermissionGate = false;
    private boolean permissionGate = false;

    /**
     * If true, non-purchasable tags that declare a permission node are
     * automatically treated as unlocked while the player has that permission
     * (no UNLOCK click needed). Losing the permission revokes access again.
     */
    private boolean autoUnlockPermissionTags = false;

    private boolean rpgLevelingNameplatesEnabled = false;
    private int rpgLevelingRefreshSeconds = 30;

    // --- Commands / features -------------------------------------------------

    /**
     * If true, enables the "owned tags" command/UI (e.g. /tags owned).
     * Nullable for backward compat.
     */
    private Boolean ownedTagsCommandEnabled = Boolean.TRUE;

    // --- Experimental glyph / hologram nameplates ----------------------------

    private boolean experimentalGlyphNameplatesEnabled = false;
    private String experimentalGlyphFont = GlyphAssets.DEFAULT_FONT;
    private int experimentalGlyphMaxChars = 32;
    private int experimentalGlyphUpdateTicks = 1; // 1 tick -> smooth per-viewer billboarding
    private int experimentalGlyphMaxEntitiesPerPlayer = 40;

    // Glyph billboard optimization / tuning
    private double experimentalGlyphViewerActivationDistance = 12.0d;
    private double experimentalGlyphViewerDropDistance = 14.0d;
    private int experimentalGlyphViewerRefreshActiveMs = 25;
    private int experimentalGlyphViewerRefreshIdleMs = 500;
    private int experimentalGlyphIdleFollowIntervalMs = 500;
    private int experimentalGlyphRotationSyncIntervalMs = 25;
    private int experimentalGlyphMaxLines = 2;
    private int experimentalGlyphMaxCharsPerLine = 32;
    private double experimentalGlyphLineSpacing = 0.30d;
    private double experimentalGlyphTintStrength = 0.65d;

    // --- Tag banners ---------------------------------------------------------

    private boolean bannersEnabled = true;
    private double bannerMaxWidthBlocks = 1.6d;
    private double bannerMaxHeightBlocks = 0.8d;
    private long bannerMaxFileBytes = 262_144L;
    private boolean bannerKeepNameLine = false;

    // ---------------------------------------------------------------------

    // Not serialized
    private transient boolean dirty = false;

    public static void init() {
        INSTANCE = new Settings();
        INSTANCE.load();
    }

    public static Settings get() {
        return INSTANCE;
    }

    private File getFile() {
        File data = MysticNameTagsPlugin.getInstance().getDataDirectory().toFile();
        return new File(data, "settings.json");
    }

    private void load() {
        File file = getFile();

        if (!file.exists()) {
            applyAutoPlaceholderDetection(true);
            dirty = true;
            saveIfDirty();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            if (loaded != null) {
                // Core
                this.nameplateFormat = nonBlankOr(loaded.nameplateFormat, this.nameplateFormat);
                this.nameplatePreset = nonBlankOr(loaded.nameplatePreset, this.nameplatePreset);
                this.stripExtraSpaces = loaded.stripExtraSpaces;
                this.language = nonBlankOr(loaded.language, this.language);
                this.tagDelaysecs = Math.max(0, loaded.tagDelaysecs);

                // Storage
                this.storageBackend = nonBlankOr(loaded.storageBackend, this.storageBackend);
                this.sqliteFile = nonBlankOr(loaded.sqliteFile, this.sqliteFile);

                this.mysqlHost = nonBlankOr(loaded.mysqlHost, this.mysqlHost);
                this.mysqlPort = (loaded.mysqlPort <= 0 ? this.mysqlPort : loaded.mysqlPort);
                this.mysqlDatabase = nonBlankOr(loaded.mysqlDatabase, this.mysqlDatabase);
                this.mysqlUser = nonBlankOr(loaded.mysqlUser, this.mysqlUser);
                this.mysqlPassword = (loaded.mysqlPassword == null ? this.mysqlPassword : loaded.mysqlPassword);

                this.redisHost = nonBlankOr(loaded.redisHost, this.redisHost);
                this.redisPort = (loaded.redisPort <= 0 ? this.redisPort : loaded.redisPort);
                this.redisUser = (loaded.redisUser == null ? this.redisUser : loaded.redisUser);
                this.redisPassword = (loaded.redisPassword == null ? this.redisPassword : loaded.redisPassword);
                this.redisDatabase = loaded.redisDatabase;
                this.redisSsl = loaded.redisSsl;
                this.redisKeyPrefix = nonBlankOr(loaded.redisKeyPrefix, this.redisKeyPrefix);
                this.redisTimeoutMs = (loaded.redisTimeoutMs <= 0 ? this.redisTimeoutMs : loaded.redisTimeoutMs);
                this.redisPoolSize = (loaded.redisPoolSize <= 0 ? this.redisPoolSize : loaded.redisPoolSize);
                this.redisSyncEnabled = loaded.redisSyncEnabled;
                this.networkServerId = (loaded.networkServerId == null ? this.networkServerId : loaded.networkServerId);

                // Playtime
                this.playtimeProvider = nonBlankOr(loaded.playtimeProvider, this.playtimeProvider);

                // Nameplates
                this.nameplatesEnabled = loaded.nameplatesEnabled;
                this.defaultTagEnabled = loaded.defaultTagEnabled;
                this.defaultTagId = nonBlankOr(loaded.defaultTagId, this.defaultTagId);

                this.endlessLevelingNameplatesEnabled = loaded.endlessLevelingNameplatesEnabled;
                this.endlessRaceDisplay = loaded.endlessRaceDisplay;
                this.endlessPrestigeDisplay = loaded.endlessPrestigeDisplay;
                this.endlessPrimaryClassDisplay = loaded.endlessPrimaryClassDisplay;
                this.endlessSecondaryClassDisplay = loaded.endlessSecondaryClassDisplay;
                this.endlessPrestigePrefix = nonBlankOr(loaded.endlessPrestigePrefix, this.endlessPrestigePrefix);

                // Placeholder toggles + auto flags
                this.wiFlowPlaceholdersEnabled = loaded.wiFlowPlaceholdersEnabled;
                this.helpchPlaceholderApiEnabled = loaded.helpchPlaceholderApiEnabled;

                this.wiFlowPlaceholdersAutoDetect = loaded.wiFlowPlaceholdersAutoDetect;
                this.helpchPlaceholderApiAutoDetect = loaded.helpchPlaceholderApiAutoDetect;

                if (!hasKeyInJson(file, "wiFlowPlaceholdersAutoDetect")) {
                    this.wiFlowPlaceholdersAutoDetect = true;
                    dirty = true;
                }
                if (!hasKeyInJson(file, "helpchPlaceholderApiAutoDetect")) {
                    this.helpchPlaceholderApiAutoDetect = true;
                    dirty = true;
                }

                // Economy & permissions
                this.economySystemEnabled = loaded.economySystemEnabled;
                this.useCoinSystem = loaded.useCoinSystem;
                this.usePhysicalCoinEconomy = loaded.usePhysicalCoinEconomy;
                this.fullPermissionGate = loaded.fullPermissionGate;
                this.permissionGate = loaded.permissionGate;
                this.autoUnlockPermissionTags = loaded.autoUnlockPermissionTags;

                // RPG
                this.rpgLevelingNameplatesEnabled = loaded.rpgLevelingNameplatesEnabled;
                this.rpgLevelingRefreshSeconds = loaded.rpgLevelingRefreshSeconds;

                // Commands
                this.ownedTagsCommandEnabled = loaded.ownedTagsCommandEnabled;

                // Glyph
                this.experimentalGlyphNameplatesEnabled = loaded.experimentalGlyphNameplatesEnabled;
                this.experimentalGlyphFont = nonBlankOr(loaded.experimentalGlyphFont, this.experimentalGlyphFont);
                this.experimentalGlyphMaxChars = loaded.experimentalGlyphMaxChars;
                this.experimentalGlyphUpdateTicks = loaded.experimentalGlyphUpdateTicks;
                this.experimentalGlyphMaxEntitiesPerPlayer = loaded.experimentalGlyphMaxEntitiesPerPlayer;
                this.experimentalGlyphViewerActivationDistance = loaded.experimentalGlyphViewerActivationDistance;
                this.experimentalGlyphViewerDropDistance = loaded.experimentalGlyphViewerDropDistance;
                this.experimentalGlyphViewerRefreshActiveMs = loaded.experimentalGlyphViewerRefreshActiveMs;
                this.experimentalGlyphViewerRefreshIdleMs = loaded.experimentalGlyphViewerRefreshIdleMs;
                this.experimentalGlyphIdleFollowIntervalMs = loaded.experimentalGlyphIdleFollowIntervalMs;
                this.experimentalGlyphRotationSyncIntervalMs = loaded.experimentalGlyphRotationSyncIntervalMs;
                this.experimentalGlyphMaxLines = loaded.experimentalGlyphMaxLines;
                this.experimentalGlyphMaxCharsPerLine = loaded.experimentalGlyphMaxCharsPerLine;
                this.experimentalGlyphLineSpacing = loaded.experimentalGlyphLineSpacing;
                this.experimentalGlyphTintStrength = loaded.experimentalGlyphTintStrength;

                // Banners
                this.bannersEnabled = loaded.bannersEnabled;
                this.bannerMaxWidthBlocks = loaded.bannerMaxWidthBlocks;
                this.bannerMaxHeightBlocks = loaded.bannerMaxHeightBlocks;
                this.bannerMaxFileBytes = loaded.bannerMaxFileBytes;
                this.bannerKeepNameLine = loaded.bannerKeepNameLine;
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load settings.json, using defaults.");
            dirty = true;
        }

        applyAutoPlaceholderDetection(false);
        normalizeAndClamp();

        saveToDisk();
        dirty = false;
    }

    private void normalizeAndClamp() {
        String before;

        before = this.storageBackend;
        this.storageBackend = getStorageBackendRaw();
        if (!safeEquals(before, this.storageBackend)) dirty = true;

        before = this.sqliteFile;
        this.sqliteFile = getSqliteFile();
        if (!safeEquals(before, this.sqliteFile)) dirty = true;

        before = this.mysqlHost;
        this.mysqlHost = getMysqlHost();
        if (!safeEquals(before, this.mysqlHost)) dirty = true;

        int oldPort = this.mysqlPort;
        this.mysqlPort = getMysqlPort();
        if (oldPort != this.mysqlPort) dirty = true;

        before = this.mysqlDatabase;
        this.mysqlDatabase = getMysqlDatabase();
        if (!safeEquals(before, this.mysqlDatabase)) dirty = true;

        before = this.redisHost;
        this.redisHost = getRedisHost();
        if (!safeEquals(before, this.redisHost)) dirty = true;

        int oldRedisPort = this.redisPort;
        this.redisPort = getRedisPort();
        if (oldRedisPort != this.redisPort) dirty = true;

        int oldRedisDb = this.redisDatabase;
        this.redisDatabase = getRedisDatabase();
        if (oldRedisDb != this.redisDatabase) dirty = true;

        before = this.redisKeyPrefix;
        this.redisKeyPrefix = getRedisKeyPrefix();
        if (!safeEquals(before, this.redisKeyPrefix)) dirty = true;

        int oldRedisTimeout = this.redisTimeoutMs;
        this.redisTimeoutMs = getRedisTimeoutMs();
        if (oldRedisTimeout != this.redisTimeoutMs) dirty = true;

        int oldRedisPool = this.redisPoolSize;
        this.redisPoolSize = getRedisPoolSize();
        if (oldRedisPool != this.redisPoolSize) dirty = true;

        before = this.networkServerId;
        this.networkServerId = getNetworkServerId();
        if (!safeEquals(before, this.networkServerId)) dirty = true;

        before = this.defaultTagId;
        this.defaultTagId = (this.defaultTagId == null ? "mystic" : this.defaultTagId.trim());
        if (!safeEquals(before, this.defaultTagId)) dirty = true;

        before = this.endlessPrestigePrefix;
        this.endlessPrestigePrefix = nonBlankOr(this.endlessPrestigePrefix, "P");
        if (!safeEquals(before, this.endlessPrestigePrefix)) dirty = true;

        int oldDelay = this.tagDelaysecs;
        this.tagDelaysecs = Math.max(0, this.tagDelaysecs);
        if (oldDelay != this.tagDelaysecs) dirty = true;

        int oldRpg = this.rpgLevelingRefreshSeconds;
        this.rpgLevelingRefreshSeconds = Math.max(5, this.rpgLevelingRefreshSeconds);
        if (oldRpg != this.rpgLevelingRefreshSeconds) dirty = true;

        int oldGlyphChars = this.experimentalGlyphMaxChars;
        this.experimentalGlyphMaxChars = Math.max(8, this.experimentalGlyphMaxChars);
        if (oldGlyphChars != this.experimentalGlyphMaxChars) dirty = true;

        before = this.nameplatePreset;
        this.nameplatePreset = getNameplatePreset();
        if (!safeEquals(before, this.nameplatePreset)) dirty = true;

        before = this.experimentalGlyphFont;
        this.experimentalGlyphFont = GlyphAssets.normalizeFont(this.experimentalGlyphFont);
        if (!safeEquals(before, this.experimentalGlyphFont)) dirty = true;

        int oldGlyphTicks = this.experimentalGlyphUpdateTicks;
        this.experimentalGlyphUpdateTicks = Math.max(1, this.experimentalGlyphUpdateTicks);
        if (oldGlyphTicks != this.experimentalGlyphUpdateTicks) dirty = true;

        int oldMax = this.experimentalGlyphMaxEntitiesPerPlayer;
        this.experimentalGlyphMaxEntitiesPerPlayer = Math.max(8, this.experimentalGlyphMaxEntitiesPerPlayer);
        if (oldMax != this.experimentalGlyphMaxEntitiesPerPlayer) dirty = true;

        double oldViewerActivationDistance = this.experimentalGlyphViewerActivationDistance;
        this.experimentalGlyphViewerActivationDistance = Math.max(2.0d, this.experimentalGlyphViewerActivationDistance);
        if (Double.compare(oldViewerActivationDistance, this.experimentalGlyphViewerActivationDistance) != 0) dirty = true;

        double oldViewerDropDistance = this.experimentalGlyphViewerDropDistance;
        this.experimentalGlyphViewerDropDistance = Math.max(
                this.experimentalGlyphViewerActivationDistance,
                this.experimentalGlyphViewerDropDistance
        );
        if (Double.compare(oldViewerDropDistance, this.experimentalGlyphViewerDropDistance) != 0) dirty = true;

        int oldViewerRefreshActiveMs = this.experimentalGlyphViewerRefreshActiveMs;
        this.experimentalGlyphViewerRefreshActiveMs = Math.max(1, this.experimentalGlyphViewerRefreshActiveMs);
        if (oldViewerRefreshActiveMs != this.experimentalGlyphViewerRefreshActiveMs) dirty = true;

        int oldViewerRefreshIdleMs = this.experimentalGlyphViewerRefreshIdleMs;
        this.experimentalGlyphViewerRefreshIdleMs = Math.max(50, this.experimentalGlyphViewerRefreshIdleMs);
        if (oldViewerRefreshIdleMs != this.experimentalGlyphViewerRefreshIdleMs) dirty = true;

        int oldIdleFollowMs = this.experimentalGlyphIdleFollowIntervalMs;
        this.experimentalGlyphIdleFollowIntervalMs = Math.max(50, this.experimentalGlyphIdleFollowIntervalMs);
        if (oldIdleFollowMs != this.experimentalGlyphIdleFollowIntervalMs) dirty = true;

        int oldRotationSyncMs = this.experimentalGlyphRotationSyncIntervalMs;
        this.experimentalGlyphRotationSyncIntervalMs = Math.max(1, this.experimentalGlyphRotationSyncIntervalMs);
        if (oldRotationSyncMs != this.experimentalGlyphRotationSyncIntervalMs) dirty = true;

        int oldGlyphMaxLines = this.experimentalGlyphMaxLines;
        this.experimentalGlyphMaxLines = Math.max(1, this.experimentalGlyphMaxLines);
        if (oldGlyphMaxLines != this.experimentalGlyphMaxLines) dirty = true;

        int oldGlyphCharsPerLine = this.experimentalGlyphMaxCharsPerLine;
        this.experimentalGlyphMaxCharsPerLine = Math.max(1, this.experimentalGlyphMaxCharsPerLine);
        if (oldGlyphCharsPerLine != this.experimentalGlyphMaxCharsPerLine) dirty = true;

        double oldGlyphLineSpacing = this.experimentalGlyphLineSpacing;
        this.experimentalGlyphLineSpacing = Math.max(0.05d, this.experimentalGlyphLineSpacing);
        if (Double.compare(oldGlyphLineSpacing, this.experimentalGlyphLineSpacing) != 0) dirty = true;

        double oldGlyphTintStrength = this.experimentalGlyphTintStrength;
        this.experimentalGlyphTintStrength = Math.max(0.0d, Math.min(1.0d, this.experimentalGlyphTintStrength));
        if (Double.compare(oldGlyphTintStrength, this.experimentalGlyphTintStrength) != 0) dirty = true;

        double oldBannerWidth = this.bannerMaxWidthBlocks;
        this.bannerMaxWidthBlocks = Math.max(0.25d, Math.min(8.0d, this.bannerMaxWidthBlocks));
        if (Double.compare(oldBannerWidth, this.bannerMaxWidthBlocks) != 0) dirty = true;

        double oldBannerHeight = this.bannerMaxHeightBlocks;
        this.bannerMaxHeightBlocks = Math.max(0.25d, Math.min(8.0d, this.bannerMaxHeightBlocks));
        if (Double.compare(oldBannerHeight, this.bannerMaxHeightBlocks) != 0) dirty = true;

        long oldBannerBytes = this.bannerMaxFileBytes;
        this.bannerMaxFileBytes = Math.max(1024L, this.bannerMaxFileBytes);
        if (oldBannerBytes != this.bannerMaxFileBytes) dirty = true;
    }

    private void saveIfDirty() {
        if (!dirty) return;
        saveToDisk();
        dirty = false;
    }

    private static void addInfoBlock(@Nonnull JsonObject out,
                                     @Nonnull String key,
                                     @Nonnull String... lines) {
        JsonArray arr = new JsonArray();
        for (String line : lines) {
            arr.add(line);
        }
        out.add(key, arr);
    }

    /**
     * Writes the current in-memory settings to settings.json with ordered sections and inline doc fields.
     */
    private void saveToDisk() {
        File file = getFile();
        File parent = file.getParentFile();

        try {
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw new IllegalStateException("Failed to create config directory: " + parent.getAbsolutePath());
            }

            try (FileWriter writer = new FileWriter(file)) {
                JsonObject root = GSON.toJsonTree(this).getAsJsonObject();
                JsonObject out = new JsonObject();

                Set<String> copied = new HashSet<>();

                Consumer<String> copy = (key) -> {
                    if (root.has(key)) {
                        out.add(key, root.get(key));
                        copied.add(key);
                    }
                };

                out.addProperty("_", "MysticNameTags settings.json – edit & reload/restart to apply changes.");

                addInfoBlock(out, "__core",
                        "Core nameplate settings.",
                        "nameplatePreset = CUSTOM / COMPACT / TAG_ONLY / TWO_LINE / RPG / ENDLESS",
                        "When nameplatePreset is CUSTOM, nameplateFormat is used directly.",
                        "nameplateFormat = tokens: {rank}, {name}, {tag}, {endless_level}, {endless_prestige}, {endless_race}, {endless_primary_class}, {endless_secondary_class}, {rpg_level}, {ecoquests_rank}",
                        "nameplateFormat supports /n, \\n, {nl}, {newline}, and <br> for a new line.",
                        "Native Hytale nameplates flatten configured lines into one line; glyph nameplates preserve multiline layout.",
                        "stripExtraSpaces = condense multiple spaces",
                        "language = locale bundle (e.g. en_US)",
                        "tagDelaysecs = cooldown (seconds) before equipping a DIFFERENT tag again (0 = off)"
                );
                copy.accept("nameplatePreset");
                copy.accept("nameplateFormat");
                copy.accept("stripExtraSpaces");
                copy.accept("language");
                copy.accept("tagDelaysecs");

                addInfoBlock(out, "__storage",
                        "Storage backend for tag ownership data.",
                        "storageBackend = FILE / SQLITE / MYSQL / MARIADB / REDIS",
                        "FILE and SQLITE are single-server only.",
                        "MYSQL, MARIADB and REDIS are shared: every server reads the same tag",
                        "data, so a tag equipped on one server is already equipped on the next",
                        "server the player joins.",
                        "MYSQL and MARIADB use the same mysql* settings below and the same",
                        "tables; they differ only in the JDBC driver. Pick MARIADB when the",
                        "server is MariaDB - MySQL Connector/J and MariaDB do not always",
                        "agree on the handshake, and the MariaDB driver is the supported one.",
                        "REDIS needs Redis persistence (RDB/AOF) turned on, or tag ownership",
                        "is lost when the Redis instance restarts."
                );
                copy.accept("storageBackend");
                copy.accept("sqliteFile");
                copy.accept("mysqlHost");
                copy.accept("mysqlPort");
                copy.accept("mysqlDatabase");
                copy.accept("mysqlUser");
                copy.accept("mysqlPassword");

                addInfoBlock(out, "__network",
                        "Redis connection + cross-server sync (multi-server networks).",
                        "Used when storageBackend = REDIS, and whenever redisSyncEnabled is true.",
                        "redisSyncEnabled = broadcast tag changes to the other servers over",
                        "Redis pub/sub so they update live instead of only on next join.",
                        "Pair redisSyncEnabled with storageBackend = MYSQL or MARIADB to keep",
                        "durability in SQL and use Redis purely as the message bus.",
                        "redisUser = Redis 6+ ACL username. Leave blank for a server that only",
                        "has a requirepass password; set it when the ACL expects",
                        "AUTH <username> <password>. redisUsername is accepted as an alias.",
                        "redisPassword = blank for no AUTH at all.",
                        "redisSsl = true for TLS (rediss://). A TLS-only server drops the",
                        "connection mid-AUTH when this is false, which shows up in the log as",
                        "\"Unexpected end of stream\".",
                        "redisKeyPrefix namespaces the keys; keep it identical on every server.",
                        "networkServerId = blank to auto-generate. It only exists so a server",
                        "can ignore the messages it published itself.",
                        "Redis settings apply at startup; restart the server after changing them."
                );
                copy.accept("redisSyncEnabled");
                copy.accept("redisHost");
                copy.accept("redisPort");
                copy.accept("redisUser");
                copy.accept("redisPassword");
                copy.accept("redisDatabase");
                copy.accept("redisSsl");
                copy.accept("redisKeyPrefix");
                copy.accept("redisTimeoutMs");
                copy.accept("redisPoolSize");
                copy.accept("networkServerId");

                addInfoBlock(out, "__nameplates",
                        "Nameplate behavior.",
                        "nameplatesEnabled = master toggle",
                        "defaultTagEnabled = use defaultTagId when no tag equipped",
                        "defaultTagId must match tags.json id"
                );
                copy.accept("nameplatesEnabled");
                copy.accept("defaultTagEnabled");
                copy.accept("defaultTagId");

                addInfoBlock(out, "__endless",
                        "EndlessLeveling integration toggles."
                );
                copy.accept("endlessLevelingNameplatesEnabled");
                copy.accept("endlessRaceDisplay");
                copy.accept("endlessPrestigeDisplay");
                copy.accept("endlessPrimaryClassDisplay");
                copy.accept("endlessSecondaryClassDisplay");
                copy.accept("endlessPrestigePrefix");

                addInfoBlock(out, "__placeholders",
                        "Placeholder APIs.",
                        "Auto-detect flags control whether detection can override the enabled flags."
                );
                copy.accept("wiFlowPlaceholdersAutoDetect");
                copy.accept("wiFlowPlaceholdersEnabled");
                copy.accept("helpchPlaceholderApiAutoDetect");
                copy.accept("helpchPlaceholderApiEnabled");

                addInfoBlock(out, "__economy",
                        "Tag purchasing & permission gating.",
                        "fullPermissionGate = permission node fully gates tags (can hide/block access).",
                        "permissionGate = tag remains visible, but permission node is required to unlock/equip.",
                        "autoUnlockPermissionTags = non-paid tags with a permission node are instantly equippable while the player holds the permission (no UNLOCK click; revoked when the permission is removed)."
                );
                copy.accept("economySystemEnabled");
                copy.accept("useCoinSystem");
                copy.accept("usePhysicalCoinEconomy");
                copy.accept("fullPermissionGate");
                copy.accept("permissionGate");
                copy.accept("autoUnlockPermissionTags");

                addInfoBlock(out, "__rpg",
                        "RPGLeveling integration."
                );
                copy.accept("rpgLevelingNameplatesEnabled");
                copy.accept("rpgLevelingRefreshSeconds");

                addInfoBlock(out, "__playtime",
                        "Playtime provider + extra commands.",
                        "playtimeProvider = AUTO / INTERNAL / ZIB_PLAYTIME / NONE"
                );
                copy.accept("playtimeProvider");
                copy.accept("ownedTagsCommandEnabled");

                addInfoBlock(out, "__experimental_glyph_nameplates",
                        "⚠ EXPERIMENTAL ⚠",
                        "Glyph nameplates packet-spawn models and mount them to the player.",
                        "Keep disabled unless testing with low player counts.",
                        "experimentalGlyphFont = default / sans / serif / comic / cursive / impact / mono / thin",
                        "experimentalGlyphUpdateTicks = billboard refresh cadence; 1 is smoothest",
                        "experimentalGlyphViewerActivationDistance = activate nearest-viewer billboard inside this radius",
                        "experimentalGlyphViewerDropDistance = keep current viewer until they leave this larger radius",
                        "experimentalGlyphViewerRefreshActiveMs = viewer scan cadence while active",
                        "experimentalGlyphViewerRefreshIdleMs = viewer scan cadence while idle",
                        "experimentalGlyphIdleFollowIntervalMs = follow cadence when no valid nearby viewer exists",
                        "experimentalGlyphRotationSyncIntervalMs = packet glyph billboard/position sync cadence; lower = smoother, higher = fewer packets",
                        "experimentalGlyphMaxLines = maximum number of rendered lines",
                        "experimentalGlyphMaxCharsPerLine = visible glyph chars per line",
                        "experimentalGlyphLineSpacing = vertical spacing between line anchors",
                        "experimentalGlyphTintStrength = glyph color brightness multiplier (0.0 - 1.0, lower = dimmer/less glow)"
                );
                copy.accept("experimentalGlyphNameplatesEnabled");
                copy.accept("experimentalGlyphFont");
                copy.accept("experimentalGlyphMaxChars");
                copy.accept("experimentalGlyphUpdateTicks");
                copy.accept("experimentalGlyphMaxEntitiesPerPlayer");
                copy.accept("experimentalGlyphViewerActivationDistance");
                copy.accept("experimentalGlyphViewerDropDistance");
                copy.accept("experimentalGlyphViewerRefreshActiveMs");
                copy.accept("experimentalGlyphViewerRefreshIdleMs");
                copy.accept("experimentalGlyphIdleFollowIntervalMs");
                copy.accept("experimentalGlyphRotationSyncIntervalMs");
                copy.accept("experimentalGlyphMaxLines");
                copy.accept("experimentalGlyphMaxCharsPerLine");
                copy.accept("experimentalGlyphLineSpacing");
                copy.accept("experimentalGlyphTintStrength");

                addInfoBlock(out, "__banners",
                        "Tag banners: render a PNG above the player instead of the tag's text.",
                        "Drop art in the plugin's images/ folder, then set \"banner\": \"<file>\" on a tag in tags.json.",
                        "Banners ride the glyph nameplate pipeline, so experimentalGlyphNameplatesEnabled must also be true.",
                        "bannersEnabled = master toggle; when false, banner tags render their normal text display",
                        "bannerMaxWidthBlocks = widest a banner may render; larger art is scaled down keeping its aspect",
                        "bannerMaxHeightBlocks = tallest a banner may render; the tighter of the two caps wins",
                        "bannerMaxFileBytes = PNGs larger than this are skipped at load with a warning",
                        "bannerKeepNameLine = render the rest of nameplateFormat around the banner, with the banner",
                        "                     taking the {tag} slot. When false the banner is the whole nameplate."
                );
                copy.accept("bannersEnabled");
                copy.accept("bannerMaxWidthBlocks");
                copy.accept("bannerMaxHeightBlocks");
                copy.accept("bannerMaxFileBytes");
                copy.accept("bannerKeepNameLine");

                JsonObject other = new JsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("_")) continue;
                    if (copied.contains(key)) continue;
                    other.add(key, entry.getValue());
                }

                if (!other.entrySet().isEmpty()) {
                    addInfoBlock(out, "__other", "=== Other / future settings ===");
                    for (Map.Entry<String, JsonElement> e : other.entrySet()) {
                        out.add(e.getKey(), e.getValue());
                    }
                }

                GSON.toJson(out, writer);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save settings.json");
        }
    }

    // ---------------------------------------------------------------------
    // Auto-detection for placeholder backends
    // ---------------------------------------------------------------------

    private void applyAutoPlaceholderDetection(boolean firstRun) {
        boolean wiFlowPresent = classPresent("com.wiflow.placeholderapi.WiFlowPlaceholderAPI");
        boolean helpchPresent = classPresent("at.helpch.placeholderapi.PlaceholderAPI");

        if (firstRun || this.wiFlowPlaceholdersAutoDetect) {
            if (this.wiFlowPlaceholdersEnabled != wiFlowPresent) {
                this.wiFlowPlaceholdersEnabled = wiFlowPresent;
                dirty = true;
            }
        }

        if (firstRun || this.helpchPlaceholderApiAutoDetect) {
            if (this.helpchPlaceholderApiEnabled != helpchPresent) {
                this.helpchPlaceholderApiEnabled = helpchPresent;
                dirty = true;
            }
        }

        LOGGER.at(Level.INFO).log("[MysticNameTags] WiFlowPlaceholderAPI present=" + wiFlowPresent
                + " | enabled=" + wiFlowPlaceholdersEnabled
                + " | autoDetect=" + wiFlowPlaceholdersAutoDetect);

        LOGGER.at(Level.INFO).log("[MysticNameTags] at.helpch PlaceholderAPI present=" + helpchPresent
                + " | enabled=" + helpchPlaceholderApiEnabled
                + " | autoDetect=" + helpchPlaceholderApiAutoDetect);
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasKeyInJson(File file, String key) {
        try (FileReader r = new FileReader(file)) {
            JsonObject obj = GSON.fromJson(r, JsonObject.class);
            return obj != null && obj.has(key);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean safeEquals(@Nullable Object a, @Nullable Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    private static String nonBlankOr(@Nullable String value, @Nonnull String fallback) {
        if (value == null) return fallback;
        String t = value.trim();
        return t.isEmpty() ? fallback : t;
    }

    // ---------------------------------------------------------------------
    // Nameplate formatting
    // ---------------------------------------------------------------------

    @Nonnull
    public String getNameplateFormatRaw() {
        String preset = getNameplatePreset();
        if (!"CUSTOM".equals(preset)) {
            return switch (preset) {
                case "COMPACT" -> "{rank} {name} {tag}";
                case "TAG_ONLY" -> "{tag}";
                case "TWO_LINE" -> "{rank} {name}\\n{tag}";
                case "RPG" -> "{rank} {name}\\n{tag} Lv.{rpg_level}";
                case "ENDLESS" -> "{rank} {name}\\n{tag} Lv.{endless_level} {endless_prestige}";
                default -> "{rank} {name} {tag}";
            };
        }

        return (nameplateFormat == null || nameplateFormat.isBlank())
                ? "{rank} {name} {tag}"
                : nameplateFormat;
    }

    @Nonnull
    public String getNameplatePreset() {
        String value = nameplatePreset;
        if (value == null || value.isBlank()) {
            return "CUSTOM";
        }

        return switch (value.trim().toUpperCase()) {
            case "COMPACT", "TAG_ONLY", "TWO_LINE", "RPG", "ENDLESS" -> value.trim().toUpperCase();
            default -> "CUSTOM";
        };
    }

    public boolean isStripExtraSpacesEnabled() {
        return stripExtraSpaces;
    }

    // ---------------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------------

    public String getStorageBackendRaw() {
        return (storageBackend == null || storageBackend.isBlank())
                ? "FILE" : storageBackend.trim().toUpperCase();
    }

    public String getSqliteFile() {
        return (sqliteFile == null || sqliteFile.isBlank())
                ? "playerdata.db" : sqliteFile.trim();
    }

    public String getMysqlHost() {
        return mysqlHost == null ? "localhost" : mysqlHost.trim();
    }

    public int getMysqlPort() {
        return mysqlPort <= 0 ? 3306 : mysqlPort;
    }

    public String getMysqlDatabase() {
        return (mysqlDatabase == null || mysqlDatabase.isBlank())
                ? "mysticnametags" : mysqlDatabase.trim();
    }

    public String getMysqlUser() {
        return mysqlUser == null ? "root" : mysqlUser;
    }

    public String getMysqlPassword() {
        return mysqlPassword == null ? "" : mysqlPassword;
    }

    // ---------------------------------------------------------------------
    // Redis / network sync
    // ---------------------------------------------------------------------

    public String getRedisHost() {
        return (redisHost == null || redisHost.isBlank()) ? "localhost" : redisHost.trim();
    }

    public int getRedisPort() {
        return redisPort <= 0 ? 6379 : redisPort;
    }

    public String getRedisUser() {
        return redisUser == null ? "" : redisUser.trim();
    }

    public String getRedisPassword() {
        return redisPassword == null ? "" : redisPassword;
    }

    /** Redis ships with databases 0-15, but the limit is configurable server-side. */
    public int getRedisDatabase() {
        if (redisDatabase < 0) return 0;
        return Math.min(redisDatabase, 255);
    }

    public boolean isRedisSsl() {
        return redisSsl;
    }

    /** Always ends with a colon so keys read as prefix:tags:uuid. */
    public String getRedisKeyPrefix() {
        String raw = (redisKeyPrefix == null || redisKeyPrefix.isBlank())
                ? "mysticnametags:" : redisKeyPrefix.trim();
        return raw.endsWith(":") ? raw : raw + ":";
    }

    public int getRedisTimeoutMs() {
        if (redisTimeoutMs < 250) return 250;
        return Math.min(redisTimeoutMs, 60_000);
    }

    public int getRedisPoolSize() {
        if (redisPoolSize < 2) return 2;
        return Math.min(redisPoolSize, 64);
    }

    public boolean isRedisSyncEnabled() {
        return redisSyncEnabled;
    }

    public String getNetworkServerId() {
        if (networkServerId == null) return "";
        return networkServerId.trim().replaceAll("[^A-Za-z0-9._-]", "-");
    }

    public boolean isEconomySystemEnabled() {
        return economySystemEnabled;
    }

    public boolean isUseCoinSystem() {
        return useCoinSystem;
    }

    public boolean isUsePhysicalCoinEconomy() {
        return usePhysicalCoinEconomy;
    }

    public boolean isAutoUnlockPermissionTagsEnabled() {
        return autoUnlockPermissionTags;
    }

    public boolean isFullPermissionGateEnabled() {
        return fullPermissionGate;
    }

    public boolean isPermissionGateEnabled() {
        return permissionGate;
    }

    public boolean isRpgLevelingNameplatesEnabled() {
        return rpgLevelingNameplatesEnabled;
    }

    public int getRpgLevelingRefreshSeconds() {
        return Math.max(5, rpgLevelingRefreshSeconds);
    }

    public boolean isWiFlowPlaceholdersEnabled() {
        return wiFlowPlaceholdersEnabled;
    }

    public boolean isHelpchPlaceholderApiEnabled() {
        return helpchPlaceholderApiEnabled;
    }

    public boolean isNameplatesEnabled() {
        return nameplatesEnabled;
    }

    public boolean isDefaultTagEnabled() {
        return defaultTagEnabled;
    }

    public String getDefaultTagId() {
        return defaultTagId == null ? "mystic" : defaultTagId.trim();
    }

    public boolean isEndlessLevelingNameplatesEnabled() {
        return endlessLevelingNameplatesEnabled;
    }

    public boolean isEndlessRaceDisplayEnabled() {
        return endlessRaceDisplay;
    }

    public boolean isEndlessPrimaryClassDisplayEnabled() {
        return endlessPrimaryClassDisplay;
    }

    public boolean isEndlessSecondaryClassDisplayEnabled() {
        return endlessSecondaryClassDisplay;
    }

    public boolean isEndlessPrestigeDisplayEnabled() {
        return endlessPrestigeDisplay;
    }

    public String getEndlessPrestigePrefix() {
        return (endlessPrestigePrefix == null || endlessPrestigePrefix.isBlank())
                ? "P" : endlessPrestigePrefix.trim();
    }

    public String getLanguage() {
        return (language == null || language.trim().isEmpty()) ? "en_US" : language.trim();
    }

    public boolean isOwnedTagsCommandEnabled() {
        return ownedTagsCommandEnabled == null || ownedTagsCommandEnabled;
    }

    public String getPlaytimeProviderMode() {
        String value = playtimeProvider;
        if (value == null || value.trim().isEmpty()) {
            return "AUTO";
        }
        return value.trim().toUpperCase();
    }

    public int getTagEquipDelaySeconds() {
        return Math.max(0, tagDelaysecs);
    }

    public boolean isExperimentalGlyphNameplatesEnabled() {
        return experimentalGlyphNameplatesEnabled;
    }

    @Nonnull
    public String getExperimentalGlyphFont() {
        return GlyphAssets.normalizeFont(experimentalGlyphFont);
    }

    public int getExperimentalGlyphMaxChars() {
        return Math.max(8, experimentalGlyphMaxChars);
    }

    public int getExperimentalGlyphUpdateTicks() {
        return Math.max(1, experimentalGlyphUpdateTicks);
    }

    public int getExperimentalGlyphMaxEntitiesPerPlayer() {
        return Math.max(8, experimentalGlyphMaxEntitiesPerPlayer);
    }

    public double getExperimentalGlyphViewerActivationDistance() {
        return Math.max(2.0d, experimentalGlyphViewerActivationDistance);
    }

    public double getExperimentalGlyphViewerDropDistance() {
        return Math.max(getExperimentalGlyphViewerActivationDistance(), experimentalGlyphViewerDropDistance);
    }

    public int getExperimentalGlyphViewerRefreshActiveMs() {
        return Math.max(1, experimentalGlyphViewerRefreshActiveMs);
    }

    public int getExperimentalGlyphViewerRefreshIdleMs() {
        return Math.max(50, experimentalGlyphViewerRefreshIdleMs);
    }

    public int getExperimentalGlyphIdleFollowIntervalMs() {
        return Math.max(50, experimentalGlyphIdleFollowIntervalMs);
    }

    public int getExperimentalGlyphRotationSyncIntervalMs() {
        return Math.max(1, experimentalGlyphRotationSyncIntervalMs);
    }

    public int getExperimentalGlyphMaxLines() {
        return Math.max(1, experimentalGlyphMaxLines);
    }

    public int getExperimentalGlyphMaxCharsPerLine() {
        return Math.max(1, experimentalGlyphMaxCharsPerLine);
    }

    public double getExperimentalGlyphLineSpacing() {
        return Math.max(0.05d, experimentalGlyphLineSpacing);
    }

    public double getExperimentalGlyphTintStrength() {
        return Math.max(0.0d, Math.min(1.0d, experimentalGlyphTintStrength));
    }

    // --- Tag banners ---------------------------------------------------------

    public boolean isBannersEnabled() {
        return bannersEnabled;
    }

    public double getBannerMaxWidthBlocks() {
        return Math.max(0.25d, Math.min(8.0d, bannerMaxWidthBlocks));
    }

    public double getBannerMaxHeightBlocks() {
        return Math.max(0.25d, Math.min(8.0d, bannerMaxHeightBlocks));
    }

    public long getBannerMaxFileBytes() {
        return Math.max(1024L, bannerMaxFileBytes);
    }

    public boolean isBannerKeepNameLine() {
        return bannerKeepNameLine;
    }
}
