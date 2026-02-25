package com.mystichorizons.mysticnametags.stats.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.mystichorizons.mysticnametags.stats.PlayerStatManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class DamageStatSystem extends EntityEventSystem<EntityStore, Damage> {

    public DamageStatSystem() {
        super(Damage.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> buffer,
                       @Nonnull Damage event) {

        if (event.isCancelled()) {
            return;
        }

        PlayerStatManager mgr = PlayerStatManager.get();
        if (mgr == null) return;

        // Damage taken (victim)
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null && player != null && player.getGameMode() != GameMode.Creative) {
            UUID playerUuid = playerRef.getUuid();
            mgr.addDamageTaken(playerUuid, event.getAmount());
        }

        // Damage dealt (source)
        Damage.Source source = event.getSource();
        PlayerRef damagingPlayerRef = null;

        if (source instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> srcRef = entitySource.getRef();
            if (srcRef.isValid()) {
                Store<EntityStore> srcStore = srcRef.getStore();
                damagingPlayerRef = (PlayerRef) srcStore.getComponent(srcRef, PlayerRef.getComponentType());
            }
        }

        if (damagingPlayerRef != null) {
            UUID attackerUuid = damagingPlayerRef.getUuid();
            mgr.addDamageDealt(attackerUuid, event.getAmount());
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.or(
                new Query[]{ PlayerRef.getComponentType(), NPCEntity.getComponentType() }
        );
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}