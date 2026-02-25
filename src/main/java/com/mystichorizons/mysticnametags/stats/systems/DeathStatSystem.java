package com.mystichorizons.mysticnametags.stats.systems;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.stats.PlayerStatManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * DeathStatSystem
 *
 * Tracks:
 *  - Player deaths (custom.deaths_total)
 *  - Player kills vs players/NPCs:
 *      - custom.kills_total
 *      - killed.<entityId>
 *
 * Uses the same DeathSystems.OnDeathSystem hook pattern as EcoTaleQuests'
 * MobDeathQuestSystem to ensure we're only called when DeathComponent is added.
 */
public final class DeathStatSystem extends DeathSystems.OnDeathSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, NPCEntity> npcType    = NPCEntity.getComponentType();
    private final ComponentType<EntityStore, Player>    playerType = Player.getComponentType();

    // Cached reflective getComponent(accessor, Ref, ComponentType) method
    private volatile Method getComponentMethod;
    private volatile boolean methodResolved = false;

    public DeathStatSystem() {
    }

    // ------------------------------------------------------------------------
    // Query – which entities we care about having a DeathComponent added
    // ------------------------------------------------------------------------

    @Override
    public Query<EntityStore> getQuery() {
        // We care about deaths of both players and NPCs
        return Query.or(playerType, npcType);
    }

    // ------------------------------------------------------------------------
    // OnDeath hook
    // ------------------------------------------------------------------------

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                                 @Nonnull DeathComponent death,
                                 @Nonnull Store<EntityStore> store,
                                 @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        try {
            processDeath(ref, death, store, commandBuffer);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING)
                    .withCause(t)
                    .log("[MysticNameTags] Error in DeathStatSystem.onComponentAdded for ref=%s", ref);
        }
    }

    // ------------------------------------------------------------------------
    // Core logic
    // ------------------------------------------------------------------------

    private void processDeath(@Nonnull Ref<EntityStore> victimRef,
                              @Nonnull DeathComponent death,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        PlayerStatManager mgr = PlayerStatManager.get();
        if (mgr == null) {
            return; // stats system not initialized
        }

        Object accessor = commandBuffer != null ? commandBuffer : store;

        // ----- Victim -----
        PlayerRef victimPlayerRef = getComp(accessor, victimRef, PlayerRef.getComponentType());
        if (victimPlayerRef == null && accessor == commandBuffer) {
            // Fallback to store if buffer accessor failed
            victimPlayerRef = getComp(store, victimRef, PlayerRef.getComponentType());
        }

        if (victimPlayerRef != null && victimPlayerRef.isValid()) {
            UUID victimUuid = victimPlayerRef.getUuid();
            mgr.incrementDeath(victimUuid);
        }

        // Resolve a string ID for the killed entity (for killed.<entityId> stat)
        String entityId = resolveEntityId(accessor, store, victimRef);

        // ----- Killer -----
        Ref<EntityStore> attackerRef = resolveAttackerRef(accessor, store, victimRef, death);
        if (attackerRef == null || !attackerRef.isValid()) {
            return; // environmental death or no valid killer
        }

        PlayerRef killerPlayerRef = getComp(accessor, attackerRef, PlayerRef.getComponentType());
        if (killerPlayerRef == null && accessor == commandBuffer) {
            killerPlayerRef = getComp(store, attackerRef, PlayerRef.getComponentType());
        }

        if (killerPlayerRef == null || !killerPlayerRef.isValid()) {
            // Killer wasn't a player (mob vs mob, etc.)
            return;
        }

        UUID killerUuid = killerPlayerRef.getUuid();

        // Don't award a kill for suicide; we already counted the death
        if (victimPlayerRef != null
                && victimPlayerRef.isValid()
                && killerUuid.equals(victimPlayerRef.getUuid())) {
            return;
        }

        mgr.incrementEntityKill(killerUuid, entityId);
    }

    // ------------------------------------------------------------------------
    // Attacker resolution (mirrors EcoTale MobDeathQuestSystem)
    // ------------------------------------------------------------------------

    @Nullable
    private Ref<EntityStore> resolveAttackerRef(@Nonnull Object accessor,
                                                @Nonnull Store<EntityStore> store,
                                                @Nonnull Ref<EntityStore> victimRef,
                                                @Nonnull DeathComponent death) {
        // First try DeathComponent.getDeathInfo(), like EcoTale
        try {
            Damage damage = death.getDeathInfo();
            if (damage != null) {
                Damage.Source source = damage.getSource();
                if (source instanceof Damage.EntitySource entitySource) {
                    Ref<EntityStore> ref = entitySource.getRef();
                    if (ref != null && ref.isValid()) {
                        return ref;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE)
                    .withCause(e)
                    .log("[MysticNameTags] DeathStatSystem: deathInfo resolution failed");
        }

        // If the victim is an NPC, try its damageData as a fallback
        NPCEntity npc = getComp(accessor, victimRef, npcType);
        if (npc == null && accessor == store) {
            npc = getComp(store, victimRef, npcType);
        }

        if (npc != null) {
            try {
                Method getDmg = npc.getClass().getMethod("getDamageData");
                Object damageData = getDmg.invoke(npc);
                if (damageData != null) {
                    Method getMost = damageData.getClass().getMethod("getMostDamagingAttacker");
                    Ref<EntityStore> ref = (Ref<EntityStore>) getMost.invoke(damageData);
                    if (ref != null && ref.isValid()) {
                        return ref;
                    }

                    Method getAny = damageData.getClass().getMethod("getAnyAttacker");
                    Ref<EntityStore> any = (Ref<EntityStore>) getAny.invoke(damageData);
                    if (any != null && any.isValid()) {
                        return any;
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // Older / different NPC API – just ignore
            } catch (Exception e) {
                LOGGER.at(Level.FINE)
                        .withCause(e)
                        .log("[MysticNameTags] DeathStatSystem: NPC damageData resolution failed");
            }
        }

        return null;
    }

    // ------------------------------------------------------------------------
    // Entity ID resolution for killed.<entityId>
    // ------------------------------------------------------------------------

    @Nonnull
    private String resolveEntityId(@Nonnull Object accessor,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull Ref<EntityStore> victimRef) {

        // Player victim?
        Player player = getComp(accessor, victimRef, playerType);
        if (player == null && accessor == store) {
            player = getComp(store, victimRef, playerType);
        }
        if (player != null) {
            return "Player";
        }

        // NPC victim?
        NPCEntity npc = getComp(accessor, victimRef, npcType);
        if (npc == null && accessor == store) {
            npc = getComp(store, victimRef, npcType);
        }
        if (npc != null) {
            String typeId;
            try {
                typeId = npc.getNPCTypeId();
            } catch (Exception e) {
                typeId = null;
            }

            if (typeId != null && !typeId.isBlank()) {
                return typeId.trim().toLowerCase(Locale.ROOT);
            }

            // Fallback to role name if type ID missing
            String roleName = safeRoleName(npc);
            if (!roleName.isEmpty()) {
                return roleName.trim().toLowerCase(Locale.ROOT);
            }

            return "npc";
        }

        // Unknown victim type
        return "unknown";
    }

    private static String safeRoleName(@Nonnull NPCEntity npc) {
        try {
            String roleName = npc.getRoleName();
            if (roleName != null && !roleName.isBlank()) {
                return roleName;
            }
            Role role = npc.getRole();
            if (role != null) {
                String rn = role.getRoleName();
                if (rn != null && !rn.isBlank()) {
                    return rn;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    // ------------------------------------------------------------------------
    // Reflective getComponent helper (same pattern as EcoTale)
    // ------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Nullable
    private <C extends Component<EntityStore>> C getComp(@Nonnull Object accessor,
                                                         @Nonnull Ref<EntityStore> ref,
                                                         @Nonnull ComponentType<EntityStore, C> type) {
        try {
            // Fast path: once we've resolved the signature, reuse it
            if (!methodResolved || getComponentMethod == null) {
                getComponentMethod = findGetComponentMethod(accessor);
                methodResolved = true;
                if (getComponentMethod != null) {
                    LOGGER.at(Level.FINE)
                            .log("[MysticNameTags] DeathStatSystem resolved getComponent: %s on %s",
                                    getComponentMethod.toGenericString(),
                                    accessor.getClass().getName());
                }
            }

            if (getComponentMethod != null) {
                return (C) getComponentMethod.invoke(accessor, ref, type);
            }

            // Bruteforce fallback (should normally never happen)
            for (Method m : accessor.getClass().getMethods()) {
                if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                    try {
                        m.setAccessible(true);
                        Object result = m.invoke(accessor, ref, type);
                        getComponentMethod = m;
                        LOGGER.at(Level.FINE)
                                .log("[MysticNameTags] DeathStatSystem found getComponent via brute force: %s",
                                        m.toGenericString());
                        return (C) result;
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.at(Level.FINE)
                    .withCause(e)
                    .log("[MysticNameTags] DeathStatSystem getComp reflection failed");
        }

        return null;
    }

    @Nullable
    private Method findGetComponentMethod(@Nonnull Object accessor) {
        try {
            Method m = accessor.getClass().getMethod("getComponent", Ref.class, ComponentType.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ex) {
            for (Method m : accessor.getClass().getMethods()) {
                if (m.getName().equals("getComponent") && m.getParameterCount() == 2) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && Ref.class.isAssignableFrom(params[0])) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
        }
        return null;
    }
}