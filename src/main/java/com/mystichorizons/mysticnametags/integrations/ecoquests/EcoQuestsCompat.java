package com.mystichorizons.mysticnametags.integrations.ecoquests;

import com.crystalrealm.ecotalequests.EcoTaleQuestsPlugin;
import com.crystalrealm.ecotalequests.model.QuestRank;
import com.crystalrealm.ecotalequests.service.QuestRankService;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

public final class EcoQuestsCompat {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static boolean resolved = false;
    private static boolean available = false;

    @Nullable
    private static QuestRankService rankService;

    private EcoQuestsCompat() {
    }

    public static boolean isAvailable() {
        resolve();
        return available;
    }

    @Nonnull
    public static String getRankId(@Nonnull UUID uuid) {
        resolve();

        if (!available || rankService == null) {
            return "E";
        }

        try {
            QuestRank rank = rankService.getPlayerRank(uuid);
            if (rank == null) {
                return "E";
            }

            String id = rank.getId();
            if (id == null || id.isBlank()) {
                return "E";
            }

            return id.trim();
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to query EcoQuests rank.");
            return "E";
        }
    }

    @Nullable
    public static String getRankColor(@Nonnull UUID uuid) {
        resolve();

        if (!available || rankService == null) {
            return null;
        }

        try {
            QuestRank rank = rankService.getPlayerRank(uuid);
            if (rank == null) {
                return null;
            }

            String color = rank.getColor();
            return (color == null || color.isBlank()) ? null : color.trim();
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to query EcoQuests rank color.");
            return null;
        }
    }

    @Nullable
    public static String getRankDisplayNameEn(@Nonnull UUID uuid) {
        resolve();

        if (!available || rankService == null) {
            return null;
        }

        try {
            QuestRank rank = rankService.getPlayerRank(uuid);
            if (rank == null) {
                return null;
            }

            String name = rank.getDisplayNameEn();
            return (name == null || name.isBlank()) ? null : name.trim();
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to query EcoQuests rank display name.");
            return null;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;

        try {
            Class<?> pluginClass = Class.forName("com.crystalrealm.ecotalequests.EcoTaleQuestsPlugin");

            Object plugin = null;

            try {
                Method get = pluginClass.getMethod("get");
                plugin = get.invoke(null);
            } catch (NoSuchMethodException ignored) {
            }

            if (plugin == null) {
                try {
                    Method getInstance = pluginClass.getMethod("getInstance");
                    plugin = getInstance.invoke(null);
                } catch (NoSuchMethodException ignored) {
                }
            }

            if (plugin == null) {
                available = false;
                LOGGER.at(Level.INFO).log("[MysticNameTags] EcoQuests plugin class found, but no singleton getter was exposed.");
                return;
            }

            Object service = pluginClass.getMethod("getRankService").invoke(plugin);
            if (!(service instanceof QuestRankService qrs)) {
                available = false;
                LOGGER.at(Level.INFO).log("[MysticNameTags] EcoQuests plugin found, but getRankService() returned null or wrong type.");
                return;
            }

            rankService = qrs;
            available = true;

            LOGGER.at(Level.INFO).log("[MysticNameTags] EcoQuests integration active.");
        } catch (Throwable t) {
            available = false;
            rankService = null;
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to resolve EcoQuests integration.");
        }
    }
}