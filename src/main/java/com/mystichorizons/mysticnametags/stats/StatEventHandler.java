package com.mystichorizons.mysticnametags.stats;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import java.util.UUID;

public final class StatEventHandler {

    public void register(MysticNameTagsPlugin plugin) {
        plugin.getEventRegistry().registerGlobal(EventPriority.LATE, PlayerConnectEvent.class, this::onPlayerConnect);
        plugin.getEventRegistry().registerGlobal(EventPriority.LATE, PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        plugin.getEventRegistry().registerGlobal(EventPriority.LATE, PlayerChatEvent.class, this::onPlayerChat);
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerStatManager mgr = PlayerStatManager.get();
        if (mgr == null) return;

        UUID uuid = event.getPlayerRef().getUuid();
        mgr.onPlayerJoin(uuid);
        mgr.addToStat(uuid, "custom.times_connected", 1L);
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerStatManager mgr = PlayerStatManager.get();
        if (mgr == null) return;

        UUID uuid = event.getPlayerRef().getUuid();
        mgr.onPlayerQuit(uuid);
    }

    private void onPlayerChat(PlayerChatEvent event) {
        if (event.isCancelled()) return;

        PlayerStatManager mgr = PlayerStatManager.get();
        if (mgr == null) return;

        UUID uuid = event.getSender().getUuid();
        mgr.addToStat(uuid, "custom.messages_sent", 1L);
    }
}