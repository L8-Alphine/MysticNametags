package com.mystichorizons.mysticnametags.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Central logging utility for MysticNameTags.
 *
 * - Mirrors logs into the main Hytale server log via HytaleLogger.
 * - Also writes to a dedicated rotating file in
 *   <dataDirectory>/logs/mysticnametags-*.log.
 *
 * Logs only when explicitly invoked (e.g. Debug Snapshot, caught exceptions).
 */
public final class MysticLog {

    private static volatile boolean initialized = false;

    private static HytaleLogger coreLogger;   // server log (Hytale)
    private static Logger       fileLogger;   // plugin log (JUL)
    private static FileHandler  fileHandler;

    private MysticLog() {
    }

    public static void init(@Nonnull MysticNameTagsPlugin plugin) {
        if (initialized) return;

        synchronized (MysticLog.class) {
            if (initialized) return;

            coreLogger = plugin.getLogger();

            try {
                // Uses the same data directory style as Settings.getFile()
                Path dataDir = plugin.getDataDirectory();
                Path logDir  = dataDir.resolve("logs");
                Files.createDirectories(logDir);

                // Rotating log: 1 MB per file, up to 10 files, append = true
                Path pattern = logDir.resolve("mysticnametags-%u-%g.log");
                fileHandler = new FileHandler(pattern.toString(), 1024 * 1024, 10, true);
                fileHandler.setFormatter(new SimpleFormatter());

                fileLogger = Logger.getLogger("MysticNameTagsFileLogger");
                fileLogger.setUseParentHandlers(false);
                fileLogger.setLevel(Level.ALL);
                fileLogger.addHandler(fileHandler);

                info("MysticLog initialized. Writing logs to " + pattern);
            } catch (IOException e) {
                if (coreLogger != null) {
                    coreLogger.at(Level.WARNING)
                            .log("[MysticNameTags] Failed to initialize file logger: " + e.getMessage());
                }
            }

            initialized = true;
        }
    }

    public static void shutdown() {
        synchronized (MysticLog.class) {
            if (!initialized) return;

            if (fileHandler != null) {
                try {
                    fileHandler.flush();
                    fileHandler.close();
                } catch (Exception ignored) {
                }
            }

            fileHandler = null;
            fileLogger  = null;
            coreLogger  = null;
            initialized = false;
        }
    }

    // ---------- Public helpers ----------

    public static void debug(String msg) {
        log(Level.FINE, msg, null);
    }

    public static void info(String msg) {
        log(Level.INFO, msg, null);
    }

    public static void warn(String msg) {
        log(Level.WARNING, msg, null);
    }

    public static void warn(String msg, Throwable t) {
        log(Level.WARNING, msg, t);
    }

    public static void error(String msg, Throwable t) {
        log(Level.SEVERE, msg, t);
    }

    // ---------- Internal fan-out ----------

    private static void log(Level level, String msg, Throwable throwable) {
        String safeMsg = Objects.toString(msg, "null");

        // 1) Main server log via HytaleLogger
        if (coreLogger != null) {
            if (throwable != null) {
                coreLogger.at(level).log(safeMsg + " (cause: " + throwable.toString() + ")");
            } else {
                coreLogger.at(level).log(safeMsg);
            }
        }

        // 2) Plugin log via java.util.logging
        if (fileLogger != null) {
            if (throwable != null) {
                fileLogger.log(level, safeMsg, throwable);
            } else {
                fileLogger.log(level, safeMsg);
            }
        }
    }
}
