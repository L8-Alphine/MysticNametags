package com.mystichorizons.mysticnametags.listeners;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.nameplate.GlyphNameplateManager;
import com.mystichorizons.mysticnametags.playtime.PlaytimeService;
import com.mystichorizons.mysticnametags.stats.PlayerStatManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PlaytimeService playtimeService;

    public PlayerListener(@Nonnull PlaytimeService playtimeService) {
        this.playtimeService = playtimeService;
    }

    public void register(@Nonnull EventRegistry eventBus) {
        try {
            eventBus.register(PlayerConnectEvent.class, this::onPlayerConnect);
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered PlayerConnectEvent listener");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to register PlayerConnectEvent");
        }

        try {
            eventBus.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered PlayerReadyEvent listener");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to register PlayerReadyEvent");
        }

        try {
            eventBus.registerGlobal(AddPlayerToWorldEvent.class, this::onAddPlayerToWorld);
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered AddPlayerToWorldEvent listener");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to register AddPlayerToWorldEvent");
        }

        try {
            eventBus.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered PlayerDisconnectEvent listener");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to register PlayerDisconnectEvent");
        }
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        World world = event.getWorld();

        String playerName = (playerRef != null ? playerRef.getUsername() : "Unknown");
        String worldName = (world != null ? world.getName() : "unknown");

        LOGGER.at(Level.INFO).log("[MysticNameTags] Player %s connected to world %s", playerName, worldName);

        if (playerRef == null || world == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();

        try {
            PlayerStatManager mgr = PlayerStatManager.get();
            if (mgr != null) {
                mgr.onPlayerJoin(uuid);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to initialize PlayerStatManager session on join for %s", uuid);
        }

        try {
            playtimeService.markOnline(uuid);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to mark player online for playtime tracking: %s", uuid);
        }

        // Do not do the real glyph/nameplate apply here.
        // PlayerReadyEvent is the better lifecycle point.
    }

    private void onAddPlayerToWorld(AddPlayerToWorldEvent event) {
        Holder<EntityStore> holder = event.getHolder();
        World world = event.getWorld();

        if (holder == null || world == null) {
            return;
        }

        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        TagManager tagManager = TagManager.get();
        World previousWorld = tagManager.getOnlineWorld(uuid);

        try {
            if (previousWorld != null && !previousWorld.getName().equals(world.getName())) {
                try {
                    GlyphNameplateManager.get().remove(uuid, previousWorld);
                } catch (Throwable ignored) {
                    GlyphNameplateManager.get().forget(uuid);
                }

                tagManager.trackOnlinePlayer(playerRef, world);
                tagManager.refreshNameplate(playerRef, world);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed AddPlayerToWorld refresh for %s", uuid);
        }
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }

        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        World world = event.getPlayer().getWorld();
        if (world == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        TagManager tagManager = TagManager.get();

        tagManager.trackOnlinePlayer(playerRef, world);

        try {
            tagManager.refreshNameplate(playerRef, world);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] PlayerReady nameplate refresh failed for %s", uuid);
        }

        try {
            for (UUID onlineUuid : tagManager.getTrackedOnlinePlayerIds()) {
                PlayerRef onlineRef = tagManager.getOnlinePlayer(onlineUuid);
                World onlineWorld = tagManager.getOnlineWorld(onlineUuid);
                if (onlineRef == null || onlineWorld == null) continue;
                if (!world.getName().equals(onlineWorld.getName())) continue;

                try {
                    tagManager.refreshNameplate(onlineRef, onlineWorld);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed global refresh pass after PlayerReady for %s", uuid);
        }

        // Update Notifier

        try {
            MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
            if (plugin != null) {
                UpdateChecker checker = plugin.getUpdateChecker();
                if (checker != null && checker.hasVersionInfo() && checker.isUpdateAvailable()) {

                    IntegrationManager integrations = tagManager.getIntegrations();
                    if (integrations != null &&
                            integrations.hasPermission(playerRef, "mysticnametags.admin.update")) {

                        String current = checker.getCurrentVersion();
                        String latest = checker.getLatestVersion();

                        playerRef.sendMessage(
                                ColorFormatter.toMessage(
                                        "&7[&bMysticNameTags&7] &eA new version is available: &f"
                                                + latest + " &7(current: &f" + current + "&7)&e."
                                )
                        );
                        playerRef.sendMessage(
                                ColorFormatter.toMessage(
                                        "&7[&bMysticNameTags&7] &eDownload it on &fCurseForge &ewhen convenient."
                                )
                        );
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.at(Level.FINE).withCause(ex)
                    .log("[MysticNameTags] Failed to send update notice on join.");
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        String playerName = (playerRef != null ? playerRef.getUsername() : "Unknown");

        LOGGER.at(Level.INFO).log("[MysticNameTags] Player %s disconnected", playerName);

        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();

        try {
            PlayerStatManager mgr = PlayerStatManager.get();
            if (mgr != null) {
                mgr.onPlayerQuit(uuid);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to finalize PlayerStatManager session on quit for %s", uuid);
        }

        World world = TagManager.get().getOnlineWorld(uuid);
        try {
            if (world != null) {
                GlyphNameplateManager.get().remove(uuid, world);
            }
        } catch (Throwable ignored) {
        } finally {
            GlyphNameplateManager.get().forget(uuid);
        }

        try {
            playtimeService.markOffline(uuid);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to mark player offline for playtime tracking: %s", uuid);
        }

        TagManager.get().untrackOnlinePlayer(uuid);
    }
}