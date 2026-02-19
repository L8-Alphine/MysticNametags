package com.mystichorizons.mysticnametags.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
                this.nameplateFormat       = loaded.nameplateFormat;
                this.stripExtraSpaces      = loaded.stripExtraSpaces;
                this.language           = loaded.language;

                this.storageBackend = loaded.storageBackend != null ? loaded.storageBackend : "FILE";
                this.sqliteFile     = loaded.sqliteFile != null ? loaded.sqliteFile : "playerdata.db";

                this.mysqlHost      = loaded.mysqlHost != null ? loaded.mysqlHost : "localhost";
                this.mysqlPort      = loaded.mysqlPort;
                this.mysqlDatabase  = loaded.mysqlDatabase != null ? loaded.mysqlDatabase : "mysticnametags";
                this.mysqlUser      = loaded.mysqlUser != null ? loaded.mysqlUser : "root";
                this.mysqlPassword  = loaded.mysqlPassword != null ? loaded.mysqlPassword : "password";

                this.nameplatesEnabled      = loaded.nameplatesEnabled;
                this.defaultTagEnabled   = loaded.defaultTagEnabled;
                this.defaultTagId         = loaded.defaultTagId;

                this.endlessLevelingNameplatesEnabled = loaded.endlessLevelingNameplatesEnabled;
                this.endlessRaceDisplay = loaded.endlessRaceDisplay;

                // Start with whatever was in the file
                this.wiFlowPlaceholdersEnabled   = loaded.wiFlowPlaceholdersEnabled;
                this.helpchPlaceholderApiEnabled = loaded.helpchPlaceholderApiEnabled;

                this.economySystemEnabled  = loaded.economySystemEnabled;
                this.useCoinSystem         = loaded.useCoinSystem;
                this.usePhysicalCoinEconomy = loaded.usePhysicalCoinEconomy;
                this.fullPermissionGate    = loaded.fullPermissionGate;
                this.rpgLevelingNameplatesEnabled = loaded.rpgLevelingNameplatesEnabled;
                this.rpgLevelingRefreshSeconds    = loaded.rpgLevelingRefreshSeconds;
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
     * This is used both for first-run defaults and for upgrading old files.
     */
    private void saveToDisk() {
        File file = getFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
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
}
