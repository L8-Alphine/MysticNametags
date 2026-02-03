package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.tags.TagManager;
import org.zuxaw.plugin.api.RPGLevelingAPI;

import javax.annotation.Nonnull;
import java.util.UUID;

public class LevelNameplateRefreshTask implements Runnable {

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

        RPGLevelingAPI api;
        try {
            api = RPGLevelingAPI.get();
        } catch (Throwable t) {
            return;
        }
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

        Store<EntityStore> store = world.getEntityStore().getStore();

        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null) continue;

            UUID uuid = playerRef.getUuid();
            if (uuid == null) continue;

            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) continue;

            String baseName = playerRef.getUsername();
            if (baseName == null) baseName = "Player";

            // Build the "rank + tag" piece using MysticNameTags
            String plainNameplate = tagManager.buildPlainNameplate(playerRef, baseName, uuid);

            // Fetch RPG level
            int level;
            try {
                level = api.getPlayerLevel(uuid);
            } catch (Throwable t) {
                level = 1;
            }

            String finalText = plainNameplate + " [Lvl. " + level + "]";

            NameplateManager.get().apply(uuid, store, entityRef, finalText);
        }
    }
}
