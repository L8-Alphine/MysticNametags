package com.mystichorizons.mysticnametags.stats.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.stats.PlayerStatManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class BlockPlaceStatSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public BlockPlaceStatSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> buffer,
                       @Nonnull PlaceBlockEvent event) {

        if (event.isCancelled()) {
            return;
        }

        PlayerStatManager mgr = PlayerStatManager.get();
        if (mgr == null) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;

        UUID uuid = playerRef.getUuid();

        if (event.getItemInHand() != null) {
            Item item = event.getItemInHand().getItem();
            String itemOrBlockId = item.getBlockId() != null ? item.getBlockId() : item.getId();
            if (itemOrBlockId != null && !"Empty".equals(itemOrBlockId)) {
                mgr.incrementBlockPlaced(uuid, itemOrBlockId);
            }
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}