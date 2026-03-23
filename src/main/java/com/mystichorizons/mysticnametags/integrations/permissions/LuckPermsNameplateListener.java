package com.mystichorizons.mysticnametags.integrations.permissions;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

public final class LuckPermsNameplateListener {

    private final LuckPerms api;
    @Nullable
    private final Consumer<UUID> refreshCallback;

    public LuckPermsNameplateListener(LuckPerms api, @Nullable Consumer<UUID> refreshCallback) {
        this.api = api;
        this.refreshCallback = refreshCallback;
    }

    public void register(Object pluginOwner) {
        if (api == null || pluginOwner == null) {
            return;
        }

        EventBus bus = api.getEventBus();
        bus.subscribe(pluginOwner, UserDataRecalculateEvent.class, this::onUserDataRecalculate);
    }

    private void onUserDataRecalculate(UserDataRecalculateEvent event) {
        if (event == null || event.getUser() == null) {
            return;
        }

        UUID uuid = event.getUser().getUniqueId();
        if (uuid == null) {
            return;
        }

        if (refreshCallback != null) {
            try {
                refreshCallback.accept(uuid);
            } catch (Throwable ignored) {
            }
        }
    }
}