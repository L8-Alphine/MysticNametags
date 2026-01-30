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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static Settings INSTANCE;

    // Example default: "{rank} {name} {tag}"
    private String nameplateFormat = "{rank} {name} {tag}";
    private boolean stripExtraSpaces = true;

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
            saveDefaults();
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            if (loaded != null) {
                this.nameplateFormat = loaded.nameplateFormat;
                this.stripExtraSpaces = loaded.stripExtraSpaces;
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load settings.json, using defaults.");
        }
    }

    private void saveDefaults() {
        File file = getFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save default settings.json");
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
}
