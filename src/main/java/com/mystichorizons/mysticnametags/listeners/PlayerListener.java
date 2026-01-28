package com.mystichorizons.mysticnametags.listeners;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public void register(@Nonnull EventRegistry eventBus) {
        try {
            eventBus.register(PlayerConnectEvent.class, this::onPlayerConnect);
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered PlayerConnectEvent listener");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to register PlayerConnectEvent");
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

        // Track + refresh in one place; TagManager handles dedupe + world thread
        TagManager tagManager = TagManager.get();
        tagManager.trackOnlinePlayer(playerRef, world);
        tagManager.refreshNameplate(playerRef, world);
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        String playerName = (playerRef != null ? playerRef.getUsername() : "Unknown");

        LOGGER.at(Level.INFO).log("[MysticNameTags] Player %s disconnected", playerName);

        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        TagManager.get().untrackOnlinePlayer(uuid);
    }
}
