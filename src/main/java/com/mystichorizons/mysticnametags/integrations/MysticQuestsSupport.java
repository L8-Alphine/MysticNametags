package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MysticQuests integration using reflection only, so MysticQuests remains a
 * true optional dependency. If the MysticQuests jar isn't present, isn't
 * running, or anything fails, every viewer is treated as able to see every
 * subject.
 *
 * Expected MysticQuests type (by name):
 *  - org.hyzionstudios.mysticquests.api.MysticQuestsApi
 *    static boolean isAvailable(), static MysticQuestsApi get(),
 *    boolean canSee(UUID viewer, UUID target)
 *
 * MysticQuests hides players per viewer for quest and story scenes. Its own
 * systems hide the player entity and the engine nameplate, but packet glyph
 * nameplates are drawn here, so they must ask too, or a quest-hidden player
 * leaves their glyph floating in place. This is checked alongside MysticVanish,
 * never instead of it: a glyph shows only when both allow it, so a quest
 * ending can never undo a vanish, and the reverse. canSee already honours
 * MysticQuests' staff visibility bypass.
 *
 * MysticQuests detects this class by name to report the integration as active.
 */
public final class MysticQuestsSupport {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile boolean initialized = false;
    private static volatile boolean available   = false;
    private static volatile boolean loggedHookActive = false;

    private static Method isAvailableMethod;
    private static Method getMethod;
    private static Method canSeeMethod;

    private MysticQuestsSupport() {
    }

    private static void init() {
        if (initialized) {
            return;
        }

        synchronized (MysticQuestsSupport.class) {
            if (initialized) {
                return;
            }

            try {
                Class<?> apiClass = Class.forName("org.hyzionstudios.mysticquests.api.MysticQuestsApi");

                // static boolean isAvailable()
                isAvailableMethod = apiClass.getMethod("isAvailable");

                // static MysticQuestsApi get()
                getMethod = apiClass.getMethod("get");

                // boolean canSee(UUID viewer, UUID target)
                canSeeMethod = apiClass.getMethod("canSee", UUID.class, UUID.class);

                available = true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // MysticQuests not present, or a release before canSee existed
                available = false;
            } finally {
                initialized = true;
            }
        }
    }

    @Nullable
    private static Object api() {
        if (!initialized) {
            init();
        }
        if (!available) {
            return null;
        }

        try {
            Object running = isAvailableMethod.invoke(null);
            if (!(running instanceof Boolean b) || !b) {
                return null;
            }

            Object api = getMethod.invoke(null);
            if (api != null && !loggedHookActive) {
                loggedHookActive = true;
                LOGGER.at(Level.INFO).log("[MysticNameTags] MysticQuests detected; glyph nameplates will follow quest visibility.");
            }
            return api;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Whether the MysticQuests API is present and running. Used for
     * status/diagnostic output only.
     */
    public static boolean isAvailable() {
        return api() != null;
    }

    /**
     * Whether MysticQuests lets {@code viewerId} see {@code targetId}'s nameplate.
     *
     * If MysticQuests is missing, not running, or something fails, this
     * returns {@code true} so nameplates behave exactly as before.
     */
    public static boolean canSee(@Nullable UUID viewerId, @Nullable UUID targetId) {
        if (viewerId == null || targetId == null || viewerId.equals(targetId)) {
            return true;
        }

        Object api = api();
        if (api == null) {
            return true;
        }

        try {
            Object result = canSeeMethod.invoke(api, viewerId, targetId);
            return !(result instanceof Boolean b) || b;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
