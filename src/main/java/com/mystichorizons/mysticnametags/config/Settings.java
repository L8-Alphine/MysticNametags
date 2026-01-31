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

    // Example default: "{rank} {name} {tag}"
    private String nameplateFormat = "{rank} {name} {tag}";
    private boolean stripExtraSpaces = true;

    /**
     * If true, MysticNameTags will treat the EconomySystem
     * (com.economy.*) as a valid primary economy backend.
     *
     * If false, the integration layer will pretend the primary economy
     * does not exist and will fall back to VaultUnlocked / EliteEssentials
     * only.
     */
    private boolean economySystemEnabled = true;

    /**
     * When using the EconomySystem and this flag is true, MysticNameTags
     * will use the "cash / coin" balance instead of the standard balance
     * for tag purchasing.
     *
     * This only affects the EconomySystem backend – VaultUnlocked and
     * EliteEssentials continue to use their normal balances.
     */
    private boolean useCoinSystem = false;

    /**
     * When enabled, tag permission nodes act as a full gate:
     *
     *  - If a tag has a permission (`permission` in tags.json), that
     *    permission MUST be granted for the player to:
     *       * see the tag as usable, and
     *       * successfully purchase/equip it.
     *
     *  - Ownership alone (crate unlocks, etc.) is not enough if the
     *    permission is missing.
     *
     * When disabled, permissions are treated as an alternate way to
     * access tags (as in earlier builds): owning the tag OR having the
     * permission is enough.
     */
    private boolean fullPermissionGate = false;

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

        // If the file doesn't exist, write out a brand-new one with defaults.
        if (!file.exists()) {
            saveToDisk();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            if (loaded != null) {
                this.nameplateFormat     = loaded.nameplateFormat;
                this.stripExtraSpaces    = loaded.stripExtraSpaces;
                this.economySystemEnabled = loaded.economySystemEnabled;
                this.useCoinSystem        = loaded.useCoinSystem;
                this.fullPermissionGate   = loaded.fullPermissionGate;
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load settings.json, using defaults.");
        }

        // Always re-save after loading so any *new* fields get written
        // back out with their current values (including defaults).
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

    /**
     * Build and colorize “[Rank] Name [Tag]” according to the config format.
     * The format can include hex and legacy color codes.
     */
    public String formatNameplate(String rank, String name, String tag) {
        String result = nameplateFormat
                .replace("{rank}", rank == null ? "" : rank)
                .replace("{name}", name == null ? "" : name)
                .replace("{tag}", tag == null ? "" : tag);

        if (stripExtraSpaces) {
            result = result.replaceAll("\\s+", " ").trim();
        }

        // Apply & / hex colors after placeholder substitution
        return ColorFormatter.colorize(result);
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

    public boolean isFullPermissionGateEnabled() {
        return fullPermissionGate;
    }
}
