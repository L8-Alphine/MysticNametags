package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.tags.TagManager;
import org.zuxaw.plugin.api.RPGLevelingAPI;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class LevelNameplateRefreshTask implements Runnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Tracks last seen RPG level so we only refresh when it changes.
     */
    private final Map<UUID, Integer> lastKnownLevels = new ConcurrentHashMap<>();

    @Override
    public void run() {
        if (!Settings.get().isRpgLevelingNameplatesEnabled()) return;
        if (!MysticNameTagsPlugin.isRpgLevelingAvailable()) return;

        Universe universe = Universe.get();
        if (universe == null) return;

        RPGLevelingAPI api = RPGLevelingAPI.get();
        if (api == null) return;

        TagManager tagManager = TagManager.get();
        if (tagManager == null) return;

        for (World world : universe.getWorlds().values()) {
            if (world == null || !world.isAlive()) continue;
            world.execute(() -> refreshWorld(world, tagManager, api));
        }
    }

    private void refreshWorld(@Nonnull World world,
                              @Nonnull TagManager tagManager,
                              @Nonnull RPGLevelingAPI api) {

        EntityStore entityStore = world.getEntityStore();
        Store<EntityStore> store = entityStore.getStore();

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null) continue;

            UUID uuid = playerRef.getUuid();
            if (uuid == null) continue;

            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) continue;

            String baseName = playerRef.getUsername();
            if (baseName == null || baseName.isBlank()) {
                baseName = "Player";
            }

            int level = 1;
            try {
                RPGLevelingAPI.PlayerLevelInfo info = api.getPlayerLevelInfo(playerRef, store);
                if (info != null && info.getLevel() > 0) {
                    level = info.getLevel();
                }
            } catch (Throwable t) {
                LOGGER.at(Level.FINE).withCause(t)
                        .log("[MysticNameTags] Failed to fetch RPG level for %s (%s)", baseName, uuid);
            }

            Integer previous = lastKnownLevels.put(uuid, level);
            if (previous != null && previous == level) {
                continue;
            }

            try {
                tagManager.refreshNameplate(playerRef, world);
            } catch (Throwable t) {
                LOGGER.at(Level.FINE).withCause(t)
                        .log("[MysticNameTags] Failed to refresh RPG-driven nameplate for %s (%s)", baseName, uuid);
            }
        }
    }

    public void invalidate(@Nonnull UUID uuid) {
        lastKnownLevels.remove(uuid);
    }

    public void forget(@Nonnull UUID uuid) {
        lastKnownLevels.remove(uuid);
    }
}