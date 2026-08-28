package com.mystichorizons.mysticnametags.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class TagAuditLogger {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private TagAuditLogger() {}

    public static void log(@Nonnull String action,
                           @Nullable String actor,
                           @Nullable UUID targetUuid,
                           @Nullable String targetName,
                           @Nullable String tagId,
                           @Nullable String result,
                           @Nullable Map<String, ?> details) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            return;
        }

        File logFile = getLogFile(plugin);
        File parent = logFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("action", action);
        if (actor != null && !actor.isBlank()) entry.put("actor", actor);
        if (targetUuid != null) entry.put("targetUuid", targetUuid.toString());
        if (targetName != null && !targetName.isBlank()) entry.put("targetName", targetName);
        if (tagId != null && !tagId.isBlank()) entry.put("tagId", tagId);
        if (result != null && !result.isBlank()) entry.put("result", result);
        if (details != null && !details.isEmpty()) entry.put("details", details);

        synchronized (TagAuditLogger.class) {
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(GSON.toJson(entry));
                writer.write(System.lineSeparator());
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e)
                        .log("[MysticNameTags] Failed to write audit log entry.");
            }
        }
    }

    @Nonnull
    public static List<String> tail(int maxLines) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            return List.of();
        }

        File logFile = getLogFile(plugin);
        if (!logFile.exists()) {
            return List.of();
        }

        int safeMax = Math.max(1, Math.min(100, maxLines));
        ArrayDeque<String> lines = new ArrayDeque<>(safeMax);

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines.size() >= safeMax) {
                    lines.removeFirst();
                }
                lines.addLast(line);
            }
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to read audit log.");
        }

        return List.copyOf(lines);
    }

    @Nonnull
    public static File getLogFile(@Nonnull MysticNameTagsPlugin plugin) {
        return new File(new File(plugin.getDataDirectory().toFile(), "logs"), "audit.jsonl");
    }
}
