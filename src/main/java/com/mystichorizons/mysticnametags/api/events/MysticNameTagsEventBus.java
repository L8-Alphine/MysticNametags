package com.mystichorizons.mysticnametags.api.events;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public final class MysticNameTagsEventBus {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final CopyOnWriteArrayList<MysticNameTagsEventListener> LISTENERS =
            new CopyOnWriteArrayList<>();

    private MysticNameTagsEventBus() {}

    @Nonnull
    public static AutoCloseable register(@Nonnull MysticNameTagsEventListener listener) {
        LISTENERS.addIfAbsent(listener);
        return () -> unregister(listener);
    }

    public static boolean unregister(@Nonnull MysticNameTagsEventListener listener) {
        return LISTENERS.remove(listener);
    }

    public static int listenerCount() {
        return LISTENERS.size();
    }

    public static void publish(@Nonnull MysticNameTagsEvent event) {
        for (MysticNameTagsEventListener listener : LISTENERS) {
            try {
                listener.onMysticNameTagsEvent(event);
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Public API event listener failed for " + event.getType());
            }
        }
    }
}
