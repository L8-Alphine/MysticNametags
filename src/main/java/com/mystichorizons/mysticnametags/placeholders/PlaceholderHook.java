package com.mystichorizons.mysticnametags.placeholders;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.tags.TagManager;
import cz.creeperface.hytale.placeholderapi.api.Placeholder;
import cz.creeperface.hytale.placeholderapi.api.PlaceholderAPI;
import kotlin.jvm.functions.Function1;

import java.util.logging.Level;

public final class PlaceholderHook {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public void register() {
        PlaceholderAPI api = PlaceholderAPI.getInstance();

        //
        // %mystictags_tag% -> colored tag only
        //
        api.builder("mystictags_tag", String.class)
                .visitorLoader(new Function1() {
                    @Override
                    public Object invoke(Object arg) {
                        // raw cast because the Builder is generic on T=String
                        Placeholder.VisitorEntry entry = (Placeholder.VisitorEntry) arg;
                        PlayerRef player = (PlayerRef) entry.getPlayer();
                        if (player == null) {
                            return "";
                        }
                        return TagManager.get()
                                .getColoredActiveTag(player.getUuid());
                    }
                })
                .updateInterval(0)        // calculate every call
                .autoUpdate(false)        // no background updater
                .processParameters(false)
                .build();

        //
        // %mystictags_tag_plain% -> tag without colors
        //
        api.builder("mystictags_tag_plain", String.class)
                .visitorLoader(new Function1() {
                    @Override
                    public Object invoke(Object arg) {
                        Placeholder.VisitorEntry entry = (Placeholder.VisitorEntry) arg;
                        PlayerRef player = (PlayerRef) entry.getPlayer();
                        if (player == null) {
                            return "";
                        }
                        return TagManager.get()
                                .getPlainActiveTag(player.getUuid());
                    }
                })
                .updateInterval(0)
                .autoUpdate(false)
                .processParameters(false)
                .build();

        //
        // %mystictags_full% -> [Rank] Name [Tag] with colors (same as chat format)
        //
        api.builder("mystictags_full", String.class)
                .visitorLoader(new Function1() {
                    @Override
                    public Object invoke(Object arg) {
                        Placeholder.VisitorEntry entry = (Placeholder.VisitorEntry) arg;
                        PlayerRef player = (PlayerRef) entry.getPlayer();
                        if (player == null) {
                            return "";
                        }
                        return TagManager.get()
                                .getColoredFullNameplate(player);
                    }
                })
                .updateInterval(0)
                .autoUpdate(false)
                .processParameters(false)
                .build();

        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] Registered PlaceholderAPI placeholders.");
    }
}
