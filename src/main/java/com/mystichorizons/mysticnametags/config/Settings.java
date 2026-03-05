package com.mystichorizons.mysticnametags.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.logging.Level;

public class Settings {

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
    private int tagDelaysecs = 60;

    // ---------------------------------------------------------------------
    // Storage backend (FILE / SQLITE / MYSQL)
    // ---------------------------------------------------------------------

    /**
     * Storage backend for player tag data.
     *
     * FILE  = existing playerdata/*.json files (default, backwards compatible)
     * SQLITE = single local SQLite DB file
     * MYSQL = external MySQL/MariaDB database
     */
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

    // --- Placeholder toggles -------------------------------------------------

    /**
     * If true, MysticNameTags will run WiFlowPlaceholderAPI on the
     * built nameplate text before colorization.
     *
     * NOTE: This flag is automatically updated at runtime based on
     * whether the WiFlowPlaceholderAPI classes are present.
     */
    private boolean wiFlowPlaceholdersEnabled = false;

    /**
     * If true, MysticNameTags will run at.helpch.placeholderapi on the
     * built nameplate text before colorization.
     *
     * NOTE: This flag is automatically updated at runtime based on
     * whether the at.helpch PlaceholderAPI classes are present.
     */
    private boolean helpchPlaceholderApiEnabled = false;

    // --- Economy / permission / RPG flags (existing) -------------------------

    /**
     * If true, MysticNameTags will treat the EconomySystem
     * (com.economy.*) as a valid primary economy backend.
     */
    private boolean economySystemEnabled = true;

    /**
     * When using the EconomySystem and this flag is true, MysticNameTags
     * will use the "cash / coin" balance instead of the standard balance
     * for tag purchasing.
     */
    private boolean useCoinSystem = false;

    /**
     * If true, MysticNameTags will use CoinsAndMarkets physical coins (pouch+inventory)
     * for tag purchasing instead of ledger/bank economies.
     */
    private boolean usePhysicalCoinEconomy = false;

    /**
     * When enabled, tag permission nodes act as a full gate.
     */
    private boolean fullPermissionGate = false;

    /**
     * If true, MysticNameTags will append the RPGLeveling player level
     * to nameplates (assuming the API is available).
     */
    private boolean rpgLevelingNameplatesEnabled = false;

    /**
     * Interval (in seconds) for refreshing RPGLeveling
     * levels on nameplates for online players.
     */
    private int rpgLevelingRefreshSeconds = 30;

    // --- Commands / features -------------------------------------------------

    /**
     * If true, enables the "owned tags" command/UI (e.g. /tags owned).
     */
    private Boolean ownedTagsCommandEnabled = Boolean.TRUE; // nullable for backward compat

    // --- Experimental glyph / hologram nameplates ----------------------------

    /**
     * EXPERIMENTAL: Use glyph-based hologram nameplates (one entity per character).
     * This is VERY expensive and can cause lag/crashes on large servers.
     */
    private boolean experimentalGlyphNameplatesEnabled = false;

    /** Maximum visible characters for glyph nameplates (hard cap to prevent abuse). */
    private int experimentalGlyphMaxChars = 32;

    /** Update interval in ticks/seconds for repositioning glyphs. */
    private int experimentalGlyphUpdateTicks = 10; // ~0.5s if 20tps

    /** Distance culling (don’t render for viewers far away). */
    private float experimentalGlyphRenderDistance = 24.0f;

    // ---------------------------------------------------------------------

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
            applyAutoPlaceholderDetection();
            saveToDisk();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            if (loaded != null) {
                this.nameplateFormat   = loaded.nameplateFormat;
                this.stripExtraSpaces  = loaded.stripExtraSpaces;
                this.language          = loaded.language;
                this.tagDelaysecs      = Math.max(0, loaded.tagDelaysecs);

                this.storageBackend = loaded.storageBackend != null ? loaded.storageBackend : "FILE";
                this.sqliteFile     = loaded.sqliteFile != null ? loaded.sqliteFile : "playerdata.db";

                this.mysqlHost      = loaded.mysqlHost != null ? loaded.mysqlHost : "localhost";
                this.mysqlPort      = loaded.mysqlPort;
                this.mysqlDatabase  = loaded.mysqlDatabase != null ? loaded.mysqlDatabase : "mysticnametags";
                this.mysqlUser      = loaded.mysqlUser != null ? loaded.mysqlUser : "root";
                this.mysqlPassword  = loaded.mysqlPassword != null ? loaded.mysqlPassword : "password";

                this.playtimeProvider = loaded.playtimeProvider;

                this.nameplatesEnabled      = loaded.nameplatesEnabled;
                this.defaultTagEnabled      = loaded.defaultTagEnabled;
                this.defaultTagId           = loaded.defaultTagId;

                this.endlessLevelingNameplatesEnabled = loaded.endlessLevelingNameplatesEnabled;
                this.endlessRaceDisplay               = loaded.endlessRaceDisplay;

                this.wiFlowPlaceholdersEnabled   = loaded.wiFlowPlaceholdersEnabled;
                this.helpchPlaceholderApiEnabled = loaded.helpchPlaceholderApiEnabled;

                this.economySystemEnabled         = loaded.economySystemEnabled;
                this.useCoinSystem                = loaded.useCoinSystem;
                this.usePhysicalCoinEconomy       = loaded.usePhysicalCoinEconomy;
                this.fullPermissionGate           = loaded.fullPermissionGate;
                this.rpgLevelingNameplatesEnabled = loaded.rpgLevelingNameplatesEnabled;
                this.rpgLevelingRefreshSeconds    = loaded.rpgLevelingRefreshSeconds;

                this.ownedTagsCommandEnabled = loaded.ownedTagsCommandEnabled;

                this.experimentalGlyphNameplatesEnabled  = loaded.experimentalGlyphNameplatesEnabled;
                this.experimentalGlyphMaxChars = loaded.experimentalGlyphMaxChars;
                this.experimentalGlyphUpdateTicks = loaded.experimentalGlyphUpdateTicks;
                this.experimentalGlyphRenderDistance = loaded.experimentalGlyphRenderDistance;

            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load settings.json, using defaults.");
        }

        // After loading, auto-enable/disable placeholder backends
        applyAutoPlaceholderDetection();

        // Always write back, so settings.json reflects actual environment
        saveToDisk();
    }

    /**
     * Writes the current in-memory settings to settings.json.
     *
     * NOTE:
     *   JSON does not support real comments, so we add a few special
     *   "_comment_*" fields to the root object. These act as inline
     *   documentation for server owners while remaining safe:
     *     - Settings has no fields with those names, so Gson ignores
     *       them when reading.
     *     - We regenerate them on each save so they don’t disappear.
     */
    private void saveToDisk() {
        File file = getFile();
        try (FileWriter writer = new FileWriter(file)) {
            // Serialize the current settings to a JsonObject (flat)
            JsonElement tree = GSON.toJsonTree(this);
            JsonObject root = tree.getAsJsonObject();

            JsonObject out = new JsonObject();

            // ------------------------------------------------------------------
            // Global header
            // ------------------------------------------------------------------
            out.addProperty("_", "MysticNameTags settings.json – edit & reload/restart to apply changes.");

            // Helper to copy a single field if present
            java.util.function.BiConsumer<String, String> copyField =
                    (field, section) -> {
                        if (root.has(field)) {
                            out.add(field, root.get(field));
                        }
                    };

            // Track which fields we’ve already copied so we can add unknowns later
            java.util.Set<String> copied = new java.util.HashSet<>();

            java.util.function.Consumer<String[]> markCopied = keys -> {
                for (String k : keys) {
                    copied.add(k);
                }
            };

            // ------------------------------------------------------------------
            // 1) Core nameplate settings
            // ------------------------------------------------------------------
            out.addProperty("__core",
                    "Core nameplate settings. \n" +
                            "nameplateFormat = layout of the visible nameplate (tokens: {rank}, {name}, {tag}). \n" +
                            "stripExtraSpaces = clean up double spaces. \n" +
                            "language = translation bundle (e.g. en_US). \n" +
                            "tagDelaysecs = cooldown (seconds) before equipping a DIFFERENT tag again (0 = no cooldown).\n");
            String[] coreKeys = {
                    "nameplateFormat",
                    "stripExtraSpaces",
                    "language",
                    "tagDelaysecs"
            };
            for (String key : coreKeys) copyField.accept(key, "__core");
            markCopied.accept(coreKeys);

            // ------------------------------------------------------------------
            // 2) Storage backend
            // ------------------------------------------------------------------
            out.addProperty("__storage",
                    "Storage backend for tag ownership data. \n" +
                            "storageBackend = FILE / SQLITE / MYSQL. \n" +
                            "FILE keeps JSON in playerdata/. SQLITE stores everything in sqliteFile. \n" +
                            "MYSQL uses mysqlHost/mysqlPort/mysqlDatabase/mysqlUser/mysqlPassword.\n");
            String[] storageKeys = {
                    "storageBackend",
                    "sqliteFile",
                    "mysqlHost",
                    "mysqlPort",
                    "mysqlDatabase",
                    "mysqlUser",
                    "mysqlPassword"
            };
            for (String key : storageKeys) copyField.accept(key, "__storage");
            markCopied.accept(storageKeys);

            // ------------------------------------------------------------------
            // 3) Nameplates + default tag
            // ------------------------------------------------------------------
            out.addProperty("__nameplates",
                    "Nameplate / default tag behavior. \n" +
                            "nameplatesEnabled = master toggle for MysticNameTags nameplates. \n" +
                            "defaultTagEnabled = use defaultTagId when a player has no tag equipped. \n" +
                            "defaultTagId must match an id from tags.json (e.g. mystic).\n");
            String[] nameplateKeys = {
                    "nameplatesEnabled",
                    "defaultTagEnabled",
                    "defaultTagId"
            };
            for (String key : nameplateKeys) copyField.accept(key, "__nameplates");
            markCopied.accept(nameplateKeys);

            // ------------------------------------------------------------------
            // 4) EndlessLeveling integration
            // ------------------------------------------------------------------
            out.addProperty("__endless",
                    "EndlessLeveling integration. \n" +
                            "endlessLevelingNameplatesEnabled = let MysticNameTags override EndlessLeveling's name label. \n" +
                            "endlessRaceDisplay = append the EndlessLeveling race to the nameplate when available.\n");
            String[] endlessKeys = {
                    "endlessLevelingNameplatesEnabled",
                    "endlessRaceDisplay"
            };
            for (String key : endlessKeys) copyField.accept(key, "__endless");
            markCopied.accept(endlessKeys);

            // ------------------------------------------------------------------
            // 5) Placeholder backends
            // ------------------------------------------------------------------
            out.addProperty("__placeholders",
                    "Placeholder APIs. \n" +
                            "wiFlowPlaceholdersEnabled = use WiFlowPlaceholderAPI in nameplates. \n" +
                            "helpchPlaceholderApiEnabled = use at.helpch PlaceholderAPI in nameplates. \n" +
                            "These are usually auto-detected, but can be forced on/off here.\n");
            String[] placeholderKeys = {
                    "wiFlowPlaceholdersEnabled",
                    "helpchPlaceholderApiEnabled"
            };
            for (String key : placeholderKeys) copyField.accept(key, "__placeholders");
            markCopied.accept(placeholderKeys);

            // ------------------------------------------------------------------
            // 6) Economy & permissions
            // ------------------------------------------------------------------
            out.addProperty("__economy",
                    "Tag purchasing & permission gating. \n" +
                            "economySystemEnabled = master toggle for ALL economy support (including EcoTale, HyEssentialsX, VaultUnlocked, etc). \n" +
                            "useCoinSystem = if your primary backend supports a 'cash/coin' balance, use that instead of the main balance. \n" +
                            "usePhysicalCoinEconomy = use CoinsAndMarkets physical coins (pouch+inventory) instead of ledger/bank balances. \n" +
                            "fullPermissionGate = if true, permission nodes fully gate tags (no permission = tag stays hidden/unusable).\n");
            String[] economyKeys = {
                    "economySystemEnabled",
                    "useCoinSystem",
                    "usePhysicalCoinEconomy",
                    "fullPermissionGate"
            };
            for (String key : economyKeys) copyField.accept(key, "__economy");
            markCopied.accept(economyKeys);

            // ------------------------------------------------------------------
            // 7) RPGLeveling integration
            // ------------------------------------------------------------------
            out.addProperty("__rpg",
                    "RPGLeveling integration. \n" +
                            "rpgLevelingNameplatesEnabled = append RPGLeveling level to nameplates when the API is available. \n" +
                            "rpgLevelingRefreshSeconds = how often to refresh levels for online players (min 5 seconds).\n");
            String[] rpgKeys = {
                    "rpgLevelingNameplatesEnabled",
                    "rpgLevelingRefreshSeconds"
            };
            for (String key : rpgKeys) copyField.accept(key, "__rpg");
            markCopied.accept(rpgKeys);

            // ------------------------------------------------------------------
            // 8) Playtime & commands
            // ------------------------------------------------------------------
            out.addProperty("__playtime",
                    "Playtime provider + extra commands. \n" +
                            "playtimeProvider = AUTO (prefer Zib's Playtime), INTERNAL (use MysticNameTags built-in), \n" +
                            "ZIB_PLAYTIME (force external mod), or NONE (disable playtime requirements). \n" +
                            "ownedTagsCommandEnabled = enable/disable the '/tags owned' command and related UI.");
            String[] playtimeKeys = {
                    "playtimeProvider",
                    "ownedTagsCommandEnabled"
            };
            for (String key : playtimeKeys) copyField.accept(key, "__playtime");
            markCopied.accept(playtimeKeys);

            out.addProperty("__experimental_glyph_nameplates",
                    "⚠ EXPERIMENTAL / UNSUPPORTED ⚠\n" +
                            "Glyph nameplates spawn an entity per character to achieve true hex/gradient text.\n" +
                            "This is HIGHLY unstable and EXTREMELY resource intensive.\n" +
                            "Recommended: keep disabled unless testing with very low player counts.\n" +
                            "Hard limits exist to prevent runaway entity counts.");
            String[] glyphKeys = {
                    "experimentalGlyphNameplatesEnabled",
                    "experimentalGlyphMaxChars",
                    "experimentalGlyphUpdateTicks",
                    "experimentalGlyphRenderDistance"
            };
            for (String key : glyphKeys) copyField.accept(key, "__experimental_glyph_nameplates");
            markCopied.accept(glyphKeys);

            // ------------------------------------------------------------------
            // 9) Any future/unknown fields (backwards/forwards compatibility)
            // ------------------------------------------------------------------
            JsonObject other = new JsonObject();
            for (java.util.Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("_")) continue;        // ignore any existing comment keys
                if (copied.contains(key)) continue;       // already added in a section
                other.add(key, entry.getValue());
            }

            if (!other.entrySet().isEmpty()) {
                out.addProperty("__other", "=== Other / future settings ===");
                for (java.util.Map.Entry<String, JsonElement> entry : other.entrySet()) {
                    out.add(entry.getKey(), entry.getValue());
                }
            }

            // Finally, write the ordered object
            GSON.toJson(out, writer);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save settings.json");
        }
    }

    // ---------------------------------------------------------------------
    // Auto-detection for placeholder backends
    // ---------------------------------------------------------------------

    /**
     * Detect whether WiFlowPlaceholderAPI / at.helpch PlaceholderAPI
     * are available on the classpath and automatically toggle the
     * corresponding flags.
     */
    private void applyAutoPlaceholderDetection() {
        boolean wiFlowPresent = false;
        boolean helpchPresent = false;

        // WiFlow: com.wiflow.placeholderapi.WiFlowPlaceholderAPI
        try {
            Class.forName("com.wiflow.placeholderapi.WiFlowPlaceholderAPI");
            wiFlowPresent = true;
        } catch (ClassNotFoundException ignored) {
            wiFlowPresent = false;
        }

        // at.helpch PlaceholderAPI
        try {
            Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
            helpchPresent = true;
        } catch (ClassNotFoundException ignored) {
            helpchPresent = false;
        }

        if (this.wiFlowPlaceholdersEnabled != wiFlowPresent) {
            this.wiFlowPlaceholdersEnabled = wiFlowPresent;
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] WiFlowPlaceholderAPI "
                            + (wiFlowPresent ? "detected – enabling WiFlow placeholders." :
                            "not found – disabling WiFlow placeholders."));
        }

        if (this.helpchPlaceholderApiEnabled != helpchPresent) {
            this.helpchPlaceholderApiEnabled = helpchPresent;
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] at.helpch PlaceholderAPI "
                            + (helpchPresent ? "detected – enabling helpch placeholders." :
                            "not found – disabling helpch placeholders."));
        }
    }

    // ---------------------------------------------------------------------
    // Nameplate formatting
    // ---------------------------------------------------------------------

    /**
     * Build “[Rank] Name [Tag]” according to the config format,
     * without applying colorization. Used as the base for
     * placeholder resolution.
     *
     * The format can contain:
     *   {rank}, {name}, {tag}
     */
    public String formatNameplateRaw(String rank, String name, String tag) {
        String result = nameplateFormat
                .replace("{rank}", rank == null ? "" : rank)
                .replace("{name}", name == null ? "" : name)
                .replace("{tag}", tag == null ? "" : tag);

        if (stripExtraSpaces) {
            result = result.replaceAll("\\s+", " ").trim();
        }

        return result;
    }

    /**
     * Backwards-compatible helper that also applies ColorFormatter.
     * For nameplates, prefer using formatNameplateRaw(...) +
     * placeholder resolution + final ColorFormatter.colorize(...).
     */
    public String formatNameplate(String rank, String name, String tag) {
        String raw = formatNameplateRaw(rank, name, tag);
        return ColorFormatter.colorize(raw);
    }

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
    // Getters for flags
    // ---------------------------------------------------------------------

    public boolean isEconomySystemEnabled() {
        return economySystemEnabled;
    }

    public boolean isUseCoinSystem() {
        return useCoinSystem;
    }

    public boolean isUsePhysicalCoinEconomy() { return  usePhysicalCoinEconomy; }

    public boolean isFullPermissionGateEnabled() {
        return fullPermissionGate;
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
        return defaultTagId;
    }

    public boolean isEndlessLevelingNameplatesEnabled() {
        return endlessLevelingNameplatesEnabled;
    }

    public boolean isEndlessRaceDisplayEnabled() {
        return endlessRaceDisplay;
    }

    public String getLanguage() {
        return (language == null || language.trim().isEmpty()) ? "en_US" : language.trim();
    }

    public boolean isOwnedTagsCommandEnabled() {
        // Default to true if field is missing in old configs
        return ownedTagsCommandEnabled == null || ownedTagsCommandEnabled;
    }

    /**
     * Playtime provider mode.
     *
     * Values:
     *  - "AUTO"        – prefer Zid's Playtime mod if present, else internal
     *  - "INTERNAL"    – always use MysticNameTags internal PlaytimeService
     *  - "ZIB_PLAYTIME"– force Zid's Playtime mod (falls back to internal if missing)
     *  - "NONE"        – disable playtime requirements (they always pass)
     */
    public String getPlaytimeProviderMode() {
        String value = playtimeProvider;
        if (value == null || value.trim().isEmpty()) {
            return "AUTO";
        }
        return value.trim().toUpperCase();
    }

    /**
     * @return tag equip delay in seconds, clamped to >= 0.
     */
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
}