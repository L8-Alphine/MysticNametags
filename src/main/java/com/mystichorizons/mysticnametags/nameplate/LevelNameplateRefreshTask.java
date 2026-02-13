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
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import org.zuxaw.plugin.api.RPGLevelingAPI;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

public class LevelNameplateRefreshTask implements Runnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public void run() {
        // Config + availability guard
        if (!Settings.get().isRpgLevelingNameplatesEnabled()) {
            return;
        }
        if (!MysticNameTagsPlugin.isRpgLevelingAvailable()) {
            return;
        }

        Universe universe = Universe.get();
        if (universe == null) {
            return;
        }

        RPGLevelingAPI api = RPGLevelingAPI.get();
        if (api == null) {
            return;
        }

        TagManager tagManager = TagManager.get();

        for (World world : universe.getWorlds().values()) {
            if (world == null || !world.isAlive()) continue;

            // Ensure we run the component writes on the world thread
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
            if (baseName == null) baseName = "Player";

            // Build the "rank + tag" piece using MysticNameTags (plain text for nameplate)
            String plainNameplate = tagManager.buildPlainNameplate(playerRef, baseName, uuid);

            int level = 1; // sensible default

            try {
                // IMPORTANT: use the PlayerRef + Store overload so we hit live data
                RPGLevelingAPI.PlayerLevelInfo info = api.getPlayerLevelInfo(playerRef, store);
                if (info != null) {
                    int apiLevel = info.getLevel();
                    if (apiLevel > 0) {
                        level = apiLevel;
                    }
                }
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).log(
                        "[MysticNameTags] Failed to fetch RPG level for " +
                                baseName + " (" + uuid + "), defaulting to 1" + t
                );
            }

            String finalText = plainNameplate + " [Lvl. " + level + "]";
            finalText = ColorFormatter.stripFormatting(finalText);
            NameplateManager.get().apply(uuid, store, entityRef, finalText);
        }
    }
}
