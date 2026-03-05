package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.Settings;

import java.util.UUID;

public class GlyphNameplateFollowTask implements Runnable {

    @Override
    public void run() {
        Universe u = Universe.get();
        if (u == null) return;

        boolean enabled = Settings.get().isExperimentalGlyphNameplatesEnabled();

        for (World world : u.getWorlds().values()) {
            if (world == null || !world.isAlive()) continue;

            if (!enabled) {
                // If disabled: make sure any leftover glyph entities are removed
                GlyphNameplateManager.get().clearAllInWorld(world);
                continue;
            }

            world.execute(() -> {
                Store<EntityStore> store = world.getEntityStore().getStore();

                for (PlayerRef pr : world.getPlayerRefs()) {
                    if (pr == null) continue;

                    UUID uuid = pr.getUuid();
                    if (uuid == null) continue;

                    Ref<EntityStore> pref = pr.getReference();
                    if (pref == null || !pref.isValid()) continue;

                    GlyphNameplateManager.get().followOnly(store, pref, uuid);
                }
            });
        }
    }
}