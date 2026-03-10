package com.mystichorizons.mysticnametags.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private boolean stripExtraSpaces = true;
    private String language = "en_US";

    /**
     * Delay in seconds before a player can EQUIP a *different* tag again.
     * 0 = no cooldown.
     */
    private int tagDelaysecs = 20;

    // ---------------------------------------------------------------------
    // Storage backend (FILE / SQLITE / MYSQL)
    // ---------------------------------------------------------------------

    private String storageBackend = "FILE"; // FILE, SQLITE, MYSQL

    // SQLite options (relative to plugin data folder)
    private String sqliteFile = "playerdata.db";

    // MySQL options
    private String mysqlHost = "localhost";
    private int    mysqlPort = 3306;
    private String mysqlDatabase = "mysticnametags";
    private String mysqlUser = "root";
    private String mysqlPassword = "password";

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
    private int experimentalGlyphMaxChars = 32;
    private int experimentalGlyphUpdateTicks = 10; // 10 ticks -> 500ms
    private float experimentalGlyphRenderDistance = 24.0f;
    private int experimentalGlyphMaxEntitiesPerPlayer = 40;

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
            // First run – defaults + auto-detection
            applyAutoPlaceholderDetection(true);
            dirty = true;
            saveIfDirty();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            if (loaded != null) {
                // Core
                this.nameplateFormat  = nonBlankOr(loaded.nameplateFormat, this.nameplateFormat);
                this.stripExtraSpaces = loaded.stripExtraSpaces;
                this.language         = nonBlankOr(loaded.language, this.language);
                this.tagDelaysecs     = Math.max(0, loaded.tagDelaysecs);

                // Storage
                this.storageBackend = nonBlankOr(loaded.storageBackend, this.storageBackend);
                this.sqliteFile     = nonBlankOr(loaded.sqliteFile, this.sqliteFile);

                this.mysqlHost      = nonBlankOr(loaded.mysqlHost, this.mysqlHost);
                this.mysqlPort      = (loaded.mysqlPort <= 0 ? this.mysqlPort : loaded.mysqlPort);
                this.mysqlDatabase  = nonBlankOr(loaded.mysqlDatabase, this.mysqlDatabase);
                this.mysqlUser      = nonBlankOr(loaded.mysqlUser, this.mysqlUser);
                this.mysqlPassword  = (loaded.mysqlPassword == null ? this.mysqlPassword : loaded.mysqlPassword);

                // Playtime
                this.playtimeProvider = nonBlankOr(loaded.playtimeProvider, this.playtimeProvider);

                // Nameplates
                this.nameplatesEnabled = loaded.nameplatesEnabled;
                this.defaultTagEnabled = loaded.defaultTagEnabled;
                this.defaultTagId      = nonBlankOr(loaded.defaultTagId, this.defaultTagId);

                this.endlessLevelingNameplatesEnabled = loaded.endlessLevelingNameplatesEnabled;
                this.endlessRaceDisplay               = loaded.endlessRaceDisplay;
                this.endlessPrestigeDisplay           = loaded.endlessPrestigeDisplay;
                this.endlessPrestigePrefix            = nonBlankOr(loaded.endlessPrestigePrefix, this.endlessPrestigePrefix);

                // Placeholder toggles + auto flags (new fields default to true if missing)
                this.wiFlowPlaceholdersEnabled   = loaded.wiFlowPlaceholdersEnabled;
                this.helpchPlaceholderApiEnabled = loaded.helpchPlaceholderApiEnabled;

                this.wiFlowPlaceholdersAutoDetect =
                        loaded.wiFlowPlaceholdersAutoDetect; // default false if missing in very old? handled below
                this.helpchPlaceholderApiAutoDetect =
                        loaded.helpchPlaceholderApiAutoDetect;

                // If config predates these flags, Gson will deserialize them as false.
                // We want "auto" to be the default for older configs.
                if (!hasKeyInJson(file, "wiFlowPlaceholdersAutoDetect")) {
                    this.wiFlowPlaceholdersAutoDetect = true;
                    dirty = true;
                }
                if (!hasKeyInJson(file, "helpchPlaceholderApiAutoDetect")) {
                    this.helpchPlaceholderApiAutoDetect = true;
                    dirty = true;
                }

                // Economy & permissions
                this.economySystemEnabled   = loaded.economySystemEnabled;
                this.useCoinSystem          = loaded.useCoinSystem;
                this.usePhysicalCoinEconomy = loaded.usePhysicalCoinEconomy;
                this.fullPermissionGate     = loaded.fullPermissionGate;
                this.permissionGate         = loaded.permissionGate;

                // RPG
                this.rpgLevelingNameplatesEnabled = loaded.rpgLevelingNameplatesEnabled;
                this.rpgLevelingRefreshSeconds    = loaded.rpgLevelingRefreshSeconds;

                // Commands
                this.ownedTagsCommandEnabled = loaded.ownedTagsCommandEnabled;

                // Glyph
                this.experimentalGlyphNameplatesEnabled = loaded.experimentalGlyphNameplatesEnabled;
                this.experimentalGlyphMaxChars          = loaded.experimentalGlyphMaxChars;
                this.experimentalGlyphUpdateTicks       = loaded.experimentalGlyphUpdateTicks;
                this.experimentalGlyphRenderDistance    = loaded.experimentalGlyphRenderDistance;
                this.experimentalGlyphMaxEntitiesPerPlayer = loaded.experimentalGlyphMaxEntitiesPerPlayer;
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load settings.json, using defaults.");
            dirty = true;
        }

        // After loading, auto-enable/disable placeholder backends
        applyAutoPlaceholderDetection(false);

        // Clamp/normalize any values (may mark dirty)
        normalizeAndClamp();

        saveToDisk();
        dirty = false;
    }

    private void normalizeAndClamp() {
        // normalize string fields
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

        before = this.defaultTagId;
        this.defaultTagId = (this.defaultTagId == null ? "mystic" : this.defaultTagId.trim());
        if (!safeEquals(before, this.defaultTagId)) dirty = true;

        before = this.endlessPrestigePrefix;
        this.endlessPrestigePrefix = nonBlankOr(this.endlessPrestigePrefix, "P");
        if (!safeEquals(before, this.endlessPrestigePrefix)) dirty = true;

        // clamps
        int oldDelay = this.tagDelaysecs;
        this.tagDelaysecs = Math.max(0, this.tagDelaysecs);
        if (oldDelay != this.tagDelaysecs) dirty = true;

        int oldRpg = this.rpgLevelingRefreshSeconds;
        this.rpgLevelingRefreshSeconds = Math.max(5, this.rpgLevelingRefreshSeconds);
        if (oldRpg != this.rpgLevelingRefreshSeconds) dirty = true;

        int oldGlyphChars = this.experimentalGlyphMaxChars;
        this.experimentalGlyphMaxChars = Math.max(8, this.experimentalGlyphMaxChars);
        if (oldGlyphChars != this.experimentalGlyphMaxChars) dirty = true;

        int oldGlyphTicks = this.experimentalGlyphUpdateTicks;
        this.experimentalGlyphUpdateTicks = Math.max(1, this.experimentalGlyphUpdateTicks);
        if (oldGlyphTicks != this.experimentalGlyphUpdateTicks) dirty = true;

        float oldDist = this.experimentalGlyphRenderDistance;
        this.experimentalGlyphRenderDistance = Math.max(8f, this.experimentalGlyphRenderDistance);
        if (Float.compare(oldDist, this.experimentalGlyphRenderDistance) != 0) dirty = true;

        float oldMax = this.experimentalGlyphMaxEntitiesPerPlayer;
        this.experimentalGlyphMaxEntitiesPerPlayer = Math.max(8, this.experimentalGlyphMaxEntitiesPerPlayer);
        if (Float.compare(oldMax, this.experimentalGlyphMaxEntitiesPerPlayer) !=0 ) dirty  = true;
    }

    private void saveIfDirty() {
        if (!dirty) return;
        saveToDisk();
        dirty = false;
    }

    /**
     * Writes the current in-memory settings to settings.json with ordered sections and inline doc fields.
     */
    private void saveToDisk() {
        File file = getFile();
        try (FileWriter writer = new FileWriter(file)) {
            JsonObject root = GSON.toJsonTree(this).getAsJsonObject();
            JsonObject out = new JsonObject();

            // Track copied keys so we can preserve unknown future keys
            Set<String> copied = new HashSet<>();

            Consumer<String> copy = (key) -> {
                if (root.has(key)) {
                    out.add(key, root.get(key));
                    copied.add(key);
                }
            };

            out.addProperty("_", "MysticNameTags settings.json – edit & reload/restart to apply changes.");

            // ------------------------------------------------------------------
            // 1) Core
            // ------------------------------------------------------------------
            out.addProperty("__core",
                    "Core nameplate settings.\n" +
                            "nameplateFormat = tokens: {rank}, {name}, {tag}\n" +
                            "stripExtraSpaces = condense multiple spaces\n" +
                            "language = locale bundle (e.g. en_US)\n" +
                            "tagDelaysecs = cooldown (seconds) before equipping a DIFFERENT tag again (0 = off)");
            copy.accept("nameplateFormat");
            copy.accept("stripExtraSpaces");
            copy.accept("language");
            copy.accept("tagDelaysecs");

            // ------------------------------------------------------------------
            // 2) Storage
            // ------------------------------------------------------------------
            out.addProperty("__storage",
                    "Storage backend for tag ownership data.\n" +
                            "storageBackend = FILE / SQLITE / MYSQL");
            copy.accept("storageBackend");
            copy.accept("sqliteFile");
            copy.accept("mysqlHost");
            copy.accept("mysqlPort");
            copy.accept("mysqlDatabase");
            copy.accept("mysqlUser");
            copy.accept("mysqlPassword");

            // ------------------------------------------------------------------
            // 3) Nameplates
            // ------------------------------------------------------------------
            out.addProperty("__nameplates",
                    "Nameplate behavior.\n" +
                            "nameplatesEnabled = master toggle\n" +
                            "defaultTagEnabled = use defaultTagId when no tag equipped\n" +
                            "defaultTagId must match tags.json id");
            copy.accept("nameplatesEnabled");
            copy.accept("defaultTagEnabled");
            copy.accept("defaultTagId");

            // ------------------------------------------------------------------
            // 4) EndlessLeveling
            // ------------------------------------------------------------------
            out.addProperty("__endless",
                    "EndlessLeveling integration toggles.");
            copy.accept("endlessLevelingNameplatesEnabled");
            copy.accept("endlessRaceDisplay");
            copy.accept("endlessPrestigeDisplay");
            copy.accept("endlessPrestigePrefix");

            // ------------------------------------------------------------------
            // 5) Placeholders
            // ------------------------------------------------------------------
            out.addProperty("__placeholders",
                    "Placeholder APIs.\n" +
                            "Auto-detect flags control whether detection can override the enabled flags.");
            copy.accept("wiFlowPlaceholdersAutoDetect");
            copy.accept("wiFlowPlaceholdersEnabled");
            copy.accept("helpchPlaceholderApiAutoDetect");
            copy.accept("helpchPlaceholderApiEnabled");

            // ------------------------------------------------------------------
            // 6) Economy / permissions
            // ------------------------------------------------------------------
            out.addProperty("__economy",
                    "Tag purchasing & permission gating.\n" +
                            "fullPermissionGate = permission node fully gates tags (can hide/block access).\n" +
                            "permissionGate = tag remains visible, but permission node is required to unlock/equip.");
            copy.accept("economySystemEnabled");
            copy.accept("useCoinSystem");
            copy.accept("usePhysicalCoinEconomy");
            copy.accept("fullPermissionGate");
            copy.accept("permissionGate");

            // ------------------------------------------------------------------
            // 7) RPGLeveling
            // ------------------------------------------------------------------
            out.addProperty("__rpg",
                    "RPGLeveling integration.");
            copy.accept("rpgLevelingNameplatesEnabled");
            copy.accept("rpgLevelingRefreshSeconds");

            // ------------------------------------------------------------------
            // 8) Playtime + commands
            // ------------------------------------------------------------------
            out.addProperty("__playtime",
                    "Playtime provider + extra commands.\n" +
                            "playtimeProvider = AUTO / INTERNAL / ZIB_PLAYTIME / NONE");
            copy.accept("playtimeProvider");
            copy.accept("ownedTagsCommandEnabled");

            // ------------------------------------------------------------------
            // 9) Experimental glyph nameplates
            // ------------------------------------------------------------------
            out.addProperty("__experimental_glyph_nameplates",
                    "⚠ EXPERIMENTAL ⚠\n" +
                            "Glyph nameplates spawn an entity per character (expensive).\n" +
                            "Keep disabled unless testing with low player counts.");
            copy.accept("experimentalGlyphNameplatesEnabled");
            copy.accept("experimentalGlyphMaxChars");
            copy.accept("experimentalGlyphUpdateTicks");
            copy.accept("experimentalGlyphRenderDistance");
            copy.accept("experimentalGlyphMaxEntitiesPerPlayer");

            // ------------------------------------------------------------------
            // Preserve unknown / future fields
            // ------------------------------------------------------------------
            JsonObject other = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("_")) continue;
                if (copied.contains(key)) continue;
                other.add(key, entry.getValue());
            }
            if (!other.entrySet().isEmpty()) {
                out.addProperty("__other", "=== Other / future settings ===");
                for (Map.Entry<String, JsonElement> e : other.entrySet()) {
                    out.add(e.getKey(), e.getValue());
                }
            }

            GSON.toJson(out, writer);
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

        // Only auto-toggle if auto-detect is enabled OR it's the first run (defaults)
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

        // Log state
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

    /**
     * Detect if a JSON root key exists without fully modeling it.
     * Used to treat missing new fields as "default true" behavior.
     */
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

    public String formatNameplateRaw(String rank, String name, String tag) {
        String result = nameplateFormat
                .replace("{rank}", rank == null ? "" : rank)
                .replace("{name}", name == null ? "" : name)
                .replace("{tag}",  tag == null ? "" : tag);

        if (stripExtraSpaces) {
            result = result.replaceAll("\\s+", " ").trim();
        }

        return result;
    }

    public String formatNameplate(String rank, String name, String tag) {
        String raw = formatNameplateRaw(rank, name, tag);
        return ColorFormatter.colorize(raw);
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

    public boolean isEconomySystemEnabled() {
        return economySystemEnabled;
    }

    public boolean isUseCoinSystem() {
        return useCoinSystem;
    }

    public boolean isUsePhysicalCoinEconomy() {
        return usePhysicalCoinEconomy;
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

    public int getExperimentalGlyphMaxChars() {
        return Math.max(8, experimentalGlyphMaxChars);
    }

    public int getExperimentalGlyphUpdateTicks() {
        return Math.max(1, experimentalGlyphUpdateTicks);
    }

    public float getExperimentalGlyphRenderDistance() {
        return Math.max(8f, experimentalGlyphRenderDistance);
    }

    public int getExperimentalGlyphMaxEntitiesPerPlayer() {
        return Math.max(8, experimentalGlyphMaxEntitiesPerPlayer);
    }
}