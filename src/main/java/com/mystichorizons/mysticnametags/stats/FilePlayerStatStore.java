package com.mystichorizons.mysticnametags.stats;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

public final class FilePlayerStatStore implements PlayerStatStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final File statsFolder;
    private final Gson gson;

    public FilePlayerStatStore(@Nonnull File statsFolder, @Nonnull Gson gson) {
        this.statsFolder = statsFolder;
        this.gson = gson;

        //noinspection ResultOfMethodCallIgnored
        statsFolder.mkdirs();
    }

    @Nonnull
    private File fileFor(UUID uuid) {
        return new File(statsFolder, uuid.toString() + ".json");
    }

    @Override
    public @Nonnull PlayerStatsData load(@Nonnull UUID uuid) {
        File f = fileFor(uuid);
        if (!f.exists()) {
            return new PlayerStatsData();
        }

        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8)) {
            PlayerStatsData data = gson.fromJson(reader, PlayerStatsData.class);
            return (data != null) ? data : new PlayerStatsData();
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load stats file for " + uuid);
            return new PlayerStatsData();
        }
    }

    @Override
    public void save(@Nonnull UUID uuid, @Nonnull PlayerStatsData data) {
        File f = fileFor(uuid);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(f), StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save stats file for " + uuid);
        }
    }

    @Override
    public void delete(@Nonnull UUID uuid) {
        File f = fileFor(uuid);
        if (f.exists() && !f.delete()) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Failed to delete stats file for " + uuid);
        }
    }
}