package com.mystichorizons.mysticnametags.tags;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Backwards-compatible file-based storage using playerdata/*.json.
 */
public final class FilePlayerTagStore implements PlayerTagStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final File playerDataFolder;
    private final Gson gson;

    public FilePlayerTagStore(@Nonnull File playerDataFolder,
                              @Nonnull Gson gson) {
        this.playerDataFolder = playerDataFolder;
        this.gson = gson;
        this.playerDataFolder.mkdirs();
    }

    @Nonnull
    @Override
    public PlayerTagData load(@Nonnull UUID uuid) {
        File file = new File(playerDataFolder, uuid.toString() + ".json");
        if (!file.exists()) {
            return new PlayerTagData();
        }

        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {

            PlayerTagData data = gson.fromJson(reader, PlayerTagData.class);
            return data != null ? data : new PlayerTagData();
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load tag data for " + uuid);
            return new PlayerTagData();
        }
    }

    @Override
    public void save(@Nonnull UUID uuid, @Nonnull PlayerTagData data) {
        File file = new File(playerDataFolder, uuid.toString() + ".json");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {

            gson.toJson(data, writer);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save tag data for " + uuid);
        }
    }

    @Override
    public void delete(@Nonnull UUID uuid) {
        File file = new File(playerDataFolder, uuid.toString() + ".json");
        if (file.exists() && !file.delete()) {
            // non-fatal
            LOGGER.at(Level.FINE)
                    .log("[MysticNameTags] Could not delete playerdata file for " + uuid);
        }
    }
}
