package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.Settings;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class GlyphNameplateFollowTask implements Runnable {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean CLEARED_WHILE_DISABLED = new AtomicBoolean(false);

    @Override
    public void run() {
        Universe universe = Universe.get();
        if (universe == null) return;

        boolean enabled = Settings.get().isExperimentalGlyphNameplatesEnabled();

        if (!enabled) {
            if (CLEARED_WHILE_DISABLED.compareAndSet(false, true)) {
                for (World world : universe.getWorlds().values()) {
                    if (world == null || !world.isAlive()) continue;

                    try {
                        GlyphNameplateManager.get().clearAllInWorld(world);
                    } catch (Throwable ignored) {
                    }
                }
            }
            return;
        }

        CLEARED_WHILE_DISABLED.set(false);

        for (World world : universe.getWorlds().values()) {
            if (world == null || !world.isAlive()) continue;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    store.assertThread();

                    for (PlayerRef playerRef : world.getPlayerRefs()) {
                        if (playerRef == null) continue;

                        UUID uuid = playerRef.getUuid();
                        if (uuid == null) continue;
                        if (!GlyphNameplateManager.get().hasState(uuid)) continue;

                        Ref<EntityStore> entityRef = playerRef.getReference();
                        if (entityRef == null || !entityRef.isValid()) continue;

                        try {
                            GlyphNameplateManager.get().followOnly(store, entityRef, uuid);
                        } catch (Throwable t) {
                            LOGGER.at(Level.FINE).withCause(t)
                                    .log("[MysticNameTags] Glyph follow failed for player=" + uuid);
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.at(Level.FINE).withCause(t)
                            .log("[MysticNameTags] Glyph follow tick failed for world=" + world.getName());
                }
            });
        }
    }
}