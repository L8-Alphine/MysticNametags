package com.mystichorizons.mysticnametags.integrations.permissions;

import games.player.chat.PrefixesPlusPlugin;

import javax.annotation.Nullable;
import java.util.UUID;

public final class PrefixesPlusSupport implements PrefixSupport {

    private final boolean detected;

    public PrefixesPlusSupport() {
        boolean found;
        try {
            Class.forName("games.player.chat.PrefixesPlusPlugin");
            found = PrefixesPlusPlugin.get() != null;
        } catch (Throwable t) {
            found = false;
        }
        this.detected = found;
    }

    @Override
    public boolean isAvailable() {
        return detected && PrefixesPlusPlugin.get() != null;
    }

    @Override
    @Nullable
    public String getPrefix(UUID uuid) {
        if (!isAvailable() || uuid == null) return null;
        try {
            return PrefixesPlusPlugin.get().getPlayerPrefix(uuid); // raw text, includes [Rank]
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public String getBackendName() {
        return "PrefixesPlus";
    }
}
