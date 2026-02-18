package com.mystichorizons.mysticnametags.integrations.endlessleveling;

import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EndlessLevelingNameplateSystem extends TickingSystem<EntityStore> {

    private static final Query<EntityStore> PLAYER_QUERY = Query.any();

    private final PlayerDataManager playerDataManager;
    private final TagManager tagManager;

    // Cache last applied FULL label
    private final Map<UUID, String> lastLabels = new ConcurrentHashMap<>();

    public EndlessLevelingNameplateSystem(@Nonnull PlayerDataManager playerDataManager,
                                          @Nonnull TagManager tagManager) {
        this.playerDataManager = playerDataManager;
        this.tagManager = tagManager;
    }

    @Override
    public void tick(float deltaSeconds, int tickCount, Store<EntityStore> store) {
        if (store == null || store.isShutdown()) return;

        Settings s = Settings.get();
        if (!s.isNameplatesEnabled()) return;
        if (!s.isEndlessLevelingNameplatesEnabled()) return;

        final boolean showRace = s.isEndlessRaceDisplayEnabled();

        store.forEachChunk(PLAYER_QUERY, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (ref == null) continue;

                PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null || !playerRef.isValid()) continue;

                UUID uuid = playerRef.getUuid();
                if (uuid == null) continue;

                String baseName = playerRef.getUsername() != null ? playerRef.getUsername() : "Player";

                PlayerData data = playerDataManager.get(uuid);
                if (data == null) {
                    data = playerDataManager.loadOrCreate(uuid, baseName);
                    if (data == null) continue;
                }

                int level = Math.max(1, data.getLevel());

                // Mystic base (plain; rank/name/tag, stripped)
                String plain = tagManager.buildPlainNameplate(playerRef, baseName, uuid);

                // Build single-line label:
                // RaceId • Plain - Lvl.X
                // or Plain - Lvl.X
                String label;
                if (showRace) {
                    String raceId = data.getRaceId();
                    if (raceId == null || raceId.isBlank()) raceId = PlayerData.DEFAULT_RACE_ID;

                    label = raceId + " \u2022 " + plain + " - Lvl." + level;
                } else {
                    label = plain + " - Lvl." + level;
                }

                String prev = lastLabels.get(uuid);
                if (label.equals(prev)) continue;

                Nameplate nameplate = commandBuffer.ensureAndGetComponent(ref, Nameplate.getComponentType());
                if (nameplate == null) continue;

                nameplate.setText(label);
                lastLabels.put(uuid, label);
            }
        });
    }

    /** Force a re-apply next tick (used when other systems overwrite the nameplate). */
    public void invalidate(@Nonnull UUID uuid) {
        lastLabels.remove(uuid);
    }

    public void forget(@Nonnull UUID uuid) {
        lastLabels.remove(uuid);
    }
}
