package com.mystichorizons.mysticnametags.integrations.mmoskilltree;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.ziggfreed.mmoskilltree.api.MMOSkillTreeAPI;
import com.ziggfreed.mmoskilltree.api.events.PlayerAchievementEvent;
import com.ziggfreed.mmoskilltree.api.events.PlayerGainXpEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Refreshes nameplates that use MMOSkillTree tokens when their backing data
 * changes. This class is only loaded after the optional integration has been
 * detected.
 */
public final class MMOSkillTreeNameplateHook {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Consumer<PlayerGainXpEvent> XP_LISTENER =
            event -> refresh(event == null ? null : event.getPlayerRef());
    private static final Consumer<PlayerAchievementEvent> ACHIEVEMENT_LISTENER =
            event -> refresh(event == null ? null : event.getPlayerRef());

    private static boolean registered;

    private MMOSkillTreeNameplateHook() {
    }

    public static synchronized void register() {
        if (registered || !MMOSkillTreeCompat.isAvailable()) {
            return;
        }

        try {
            MMOSkillTreeAPI.addXpListener(XP_LISTENER);
            MMOSkillTreeAPI.addAchievementListener(ACHIEVEMENT_LISTENER);
            registered = true;
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Registered MMOSkillTree nameplate refresh listeners.");
        } catch (Throwable t) {
            try {
                MMOSkillTreeAPI.removeXpListener(XP_LISTENER);
                MMOSkillTreeAPI.removeAchievementListener(ACHIEVEMENT_LISTENER);
            } catch (Throwable ignored) {
            }
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to register MMOSkillTree nameplate refresh listeners.");
        }
    }

    public static synchronized void unregister() {
        if (!registered) {
            return;
        }

        try {
            MMOSkillTreeAPI.removeXpListener(XP_LISTENER);
            MMOSkillTreeAPI.removeAchievementListener(ACHIEVEMENT_LISTENER);
        } catch (Throwable ignored) {
        } finally {
            registered = false;
        }
    }

    private static void refresh(@Nullable PlayerRef playerRef) {
        if (playerRef == null
                || !Settings.get().getNameplateFormatRaw().contains("{mmoskilltree.")) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        TagManager manager = TagManager.get();
        if (uuid != null && manager != null) {
            manager.onExternalNameplateDataChanged(uuid);
        }
    }
}
