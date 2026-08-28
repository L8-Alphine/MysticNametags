package com.mystichorizons.mysticnametags.integrations.rpgleveling;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.zuxaw.plugin.RPGLevelingPlugin;
import org.zuxaw.plugin.api.RPGLevelingAPI;
import org.zuxaw.plugin.components.PlayerLevelData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * API-only compatibility bridge for RPGLeveling.
 */
public final class RPGLevelingCompat {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private RPGLevelingCompat() {
    }

    public static boolean isAvailable() {
        try {
            Class.forName("org.zuxaw.plugin.api.RPGLevelingAPI");
            return RPGLevelingAPI.isAvailable() && RPGLevelingAPI.get() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public static RPGLevelingAPI getApi() {
        try {
            return RPGLevelingAPI.get();
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to access RPGLevelingAPI.");
            return null;
        }
    }

    @Nullable
    public static RPGLevelingAPI.PlayerLevelInfo getPlayerLevelInfo(@Nonnull UUID uuid) {
        RPGLevelingAPI api = getApi();
        if (api == null) {
            return null;
        }

        try {
            return api.getPlayerLevelInfo(uuid);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read RPGLeveling level info for %s", uuid);
            return null;
        }
    }

    @Nullable
    public static RPGLevelingAPI.PlayerLevelInfo getPlayerLevelInfo(@Nonnull PlayerRef playerRef) {
        RPGLevelingAPI api = getApi();
        if (api == null) {
            return null;
        }

        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                return api.getPlayerLevelInfo(playerRef, store);
            }
        } catch (Throwable ignored) {
        }

        return getPlayerLevelInfo(playerRef.getUuid());
    }

    public static int getPlayerLevel(@Nonnull UUID uuid) {
        RPGLevelingAPI.PlayerLevelInfo info = getPlayerLevelInfo(uuid);
        if (info != null) {
            return info.getLevel();
        }

        RPGLevelingAPI api = getApi();
        if (api == null) {
            return -1;
        }

        try {
            return api.getPlayerLevel(uuid);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read RPGLeveling level for %s", uuid);
            return -1;
        }
    }

    public static int getPlayerLevel(@Nonnull PlayerRef playerRef) {
        RPGLevelingAPI.PlayerLevelInfo info = getPlayerLevelInfo(playerRef);
        return info != null ? info.getLevel() : getPlayerLevel(playerRef.getUuid());
    }

    @Nullable
    public static PlayerLevelData getPlayerData(@Nonnull UUID uuid) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(uuid);
            if (playerRef == null) {
                return null;
            }
            return getPlayerData(playerRef);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read RPGLeveling player data for %s", uuid);
            return null;
        }
    }

    @Nullable
    public static PlayerLevelData getPlayerData(@Nonnull PlayerRef playerRef) {
        try {
            RPGLevelingPlugin plugin = RPGLevelingPlugin.get();
            if (plugin == null || plugin.getLevelingService() == null) {
                return null;
            }

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                return plugin.getLevelingService().getPlayerData(playerRef, store);
            }

            return plugin.getLevelingService().getPlayerData(playerRef);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read RPGLeveling player data for %s", playerRef.getUuid());
            return null;
        }
    }

    @Nullable
    public static RPGLevelingAPI.PlayerClassInfo getPlayerClassInfo(@Nonnull UUID uuid) {
        RPGLevelingAPI api = getApi();
        if (api == null) {
            return null;
        }

        try {
            return api.getPlayerClassInfo(uuid);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read RPGLeveling class info for %s", uuid);
            return null;
        }
    }

    @Nonnull
    public static String getPlayerClassId(@Nonnull UUID uuid) {
        RPGLevelingAPI.PlayerClassInfo info = getPlayerClassInfo(uuid);
        return clean(info != null ? info.getClassId() : null);
    }

    public static int getSelectedClassTier(@Nonnull UUID uuid) {
        RPGLevelingAPI.PlayerClassInfo info = getPlayerClassInfo(uuid);
        if (info != null && info.hasClass()) {
            return info.getSelectedClassTier();
        }

        RPGLevelingAPI api = getApi();
        if (api == null) {
            return -1;
        }

        try {
            return api.getPlayerClassTier(uuid);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to read RPGLeveling class tier for %s", uuid);
            return -1;
        }
    }

    public static int getClassTier(@Nonnull UUID uuid, @Nonnull String classId) {
        String wanted = clean(classId).toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return -1;
        }

        RPGLevelingAPI.PlayerClassInfo info = getPlayerClassInfo(uuid);
        if (info == null) {
            return -1;
        }

        Map<String, Integer> tiers = info.getAllClassTiers();
        if (tiers == null || tiers.isEmpty()) {
            return classIdMatches(info.getClassId(), wanted) ? info.getSelectedClassTier() : -1;
        }

        for (Map.Entry<String, Integer> entry : tiers.entrySet()) {
            if (entry == null || !classIdMatches(entry.getKey(), wanted)) {
                continue;
            }
            Integer tier = entry.getValue();
            return tier != null ? tier : -1;
        }

        return -1;
    }

    public static boolean classIdMatches(@Nullable String actual, @Nonnull String expected) {
        String cleanActual = clean(actual).toLowerCase(Locale.ROOT);
        String cleanExpected = clean(expected).toLowerCase(Locale.ROOT);
        return !cleanActual.isEmpty() && cleanActual.equals(cleanExpected);
    }

    @Nonnull
    private static String clean(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
