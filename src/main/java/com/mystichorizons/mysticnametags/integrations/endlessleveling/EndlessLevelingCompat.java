package com.mystichorizons.mysticnametags.integrations.endlessleveling;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.api.PlayerSnapshot;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;

/**
 * API-only compatibility bridge for EndlessLeveling.
 *
 * MysticNameTags should prefer the public EndlessLevelingAPI surface
 * rather than directly touching internal managers.
 */
public final class EndlessLevelingCompat {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static volatile boolean playerNameplatesSuppressed;

    private EndlessLevelingCompat() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName("com.airijko.endlessleveling.api.EndlessLevelingAPI");
            EndlessLevelingAPI api = EndlessLevelingAPI.get();
            return api != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean suppressPlayerNameplates() {
        EndlessLevelingAPI api = getApi();
        if (api == null) {
            return false;
        }

        try {
            if (api.arePlayerNameplatesEnabled()) {
                api.setPlayerNameplatesEnabled(false);
                playerNameplatesSuppressed = true;
            }
            return true;
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to suppress EndlessLeveling player nameplates.");
            return false;
        }
    }

    public static void restorePlayerNameplates() {
        if (!playerNameplatesSuppressed) {
            return;
        }

        EndlessLevelingAPI api = getApi();
        if (api == null) {
            return;
        }

        try {
            api.resetPlayerNameplatesEnabled();
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to restore EndlessLeveling player nameplate setting.");
        } finally {
            playerNameplatesSuppressed = false;
        }
    }

    @Nullable
    public static EndlessLevelingAPI getApi() {
        try {
            return EndlessLevelingAPI.get();
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to access EndlessLevelingAPI.");
            return null;
        }
    }

    @Nullable
    private static PlayerSnapshot getSnapshot(@Nonnull EndlessLevelingAPI api, @Nonnull UUID uuid) {
        try {
            return api.getPlayerSnapshot(uuid);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read EndlessLeveling snapshot for %s", uuid);
            return null;
        }
    }

    @Nonnull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    @Nonnull
    public static String getLevel(@Nonnull UUID uuid) {
        EndlessLevelingAPI api = getApi();
        if (api == null) {
            return "";
        }

        try {
            PlayerSnapshot snapshot = getSnapshot(api, uuid);
            int level = Math.max(1, snapshot != null ? snapshot.level() : api.getPlayerLevel(uuid));
            return String.valueOf(level);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read EndlessLeveling level for %s", uuid);
            return "";
        }
    }

    @Nonnull
    public static String getPrestige(@Nonnull UUID uuid,
                                     boolean enabled,
                                     @Nullable String prefix) {
        if (!enabled) {
            return "";
        }

        EndlessLevelingAPI api = getApi();
        if (api == null) {
            return "";
        }

        try {
            int prestige = Math.max(0, api.getPlayerPrestigeLevel(uuid));
            if (prestige <= 0) {
                return "";
            }

            return (prefix == null ? "" : prefix) + prestige;
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read EndlessLeveling prestige for %s", uuid);
            return "";
        }
    }

    @Nonnull
    public static String getRaceId(@Nonnull UUID uuid) {
        EndlessLevelingAPI api = getApi();
        if (api == null) {
            return "";
        }

        try {
            PlayerSnapshot snapshot = getSnapshot(api, uuid);
            return clean(snapshot != null ? snapshot.raceId() : api.getRaceId(uuid));
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read EndlessLeveling race for %s", uuid);
            return "";
        }
    }

    @Nonnull
    public static String getPrimaryClassId(@Nonnull UUID uuid) {
        EndlessLevelingAPI api = getApi();
        if (api == null) {
            return "";
        }

        try {
            PlayerSnapshot snapshot = getSnapshot(api, uuid);
            return clean(snapshot != null ? snapshot.primaryClassId() : api.getPrimaryClassId(uuid));
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read EndlessLeveling primary class for %s", uuid);
            return "";
        }
    }

    @Nonnull
    public static String getSecondaryClassId(@Nonnull UUID uuid) {
        EndlessLevelingAPI api = getApi();
        if (api == null) {
            return "";
        }

        try {
            PlayerSnapshot snapshot = getSnapshot(api, uuid);
            return clean(snapshot != null ? snapshot.secondaryClassId() : api.getSecondaryClassId(uuid));
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read EndlessLeveling secondary class for %s", uuid);
            return "";
        }
    }

    @Nonnull
    public static String buildStateKey(@Nonnull UUID uuid) {
        EndlessLevelingAPI api = getApi();
        if (api == null) {
            return "";
        }

        try {
            PlayerSnapshot snapshot = getSnapshot(api, uuid);
            int level = Math.max(1, snapshot != null ? snapshot.level() : api.getPlayerLevel(uuid));
            int prestige = Math.max(0, api.getPlayerPrestigeLevel(uuid));
            String race = clean(snapshot != null ? snapshot.raceId() : api.getRaceId(uuid));
            String primary = clean(snapshot != null ? snapshot.primaryClassId() : api.getPrimaryClassId(uuid));
            String secondary = clean(snapshot != null ? snapshot.secondaryClassId() : api.getSecondaryClassId(uuid));

            return level + "|" + prestige + "|" + race + "|" + primary + "|" + secondary;
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to build EndlessLeveling state key for %s", uuid);
            return "";
        }
    }
}
