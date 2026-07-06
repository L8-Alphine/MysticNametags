package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MysticVanish integration using reflection only, so MysticVanish remains
 * a true optional dependency. If the MysticVanish jar isn't present or
 * anything fails, every viewer is treated as able to see every subject.
 *
 * Expected MysticVanish types (by name):
 *  - org.hyzionstudios.mysticvanish.api.MysticVanishProvider
 *  - org.hyzionstudios.mysticvanish.api.MysticVanishAPI
 *
 * Visibility is level-based ({@code viewer.seeLevel >= target.vanishLevel}),
 * so staff with a high enough see-level keep seeing vanished players'
 * nameplates — the same rule MysticVanish applies to the player entity.
 */
public final class MysticVanishSupport {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile boolean initialized = false;
    private static volatile boolean available   = false;
    private static volatile boolean loggedHookActive = false;

    private static Method isRegisteredMethod;
    private static Method getApiMethod;
    private static Method canSeeMethod;
    private static Method isVanishedMethod;

    private MysticVanishSupport() {
    }

    private static void init() {
        if (initialized) {
            return;
        }

        synchronized (MysticVanishSupport.class) {
            if (initialized) {
                return;
            }

            try {
                Class<?> providerClass = Class.forName("org.hyzionstudios.mysticvanish.api.MysticVanishProvider");
                Class<?> apiClass = Class.forName("org.hyzionstudios.mysticvanish.api.MysticVanishAPI");

                // static boolean isRegistered()
                isRegisteredMethod = providerClass.getMethod("isRegistered");

                // static MysticVanishAPI get()
                getApiMethod = providerClass.getMethod("get");

                // boolean canSee(UUID viewerId, UUID targetId)
                canSeeMethod = apiClass.getMethod("canSee", UUID.class, UUID.class);

                // boolean isVanished(UUID playerId)
                isVanishedMethod = apiClass.getMethod("isVanished", UUID.class);

                available = true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // MysticVanish not present or unexpected version – treat as unavailable
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
            Object registered = isRegisteredMethod.invoke(null);
            if (!(registered instanceof Boolean b) || !b) {
                return null;
            }

            Object api = getApiMethod.invoke(null);
            if (api != null && !loggedHookActive) {
                loggedHookActive = true;
                LOGGER.at(Level.INFO).log("[MysticNameTags] MysticVanish detected; glyph nameplates will follow vanish visibility.");
            }
            return api;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Whether the MysticVanish API is present on the classpath and currently
     * registered. Used for status/diagnostic output only.
     */
    public static boolean isAvailable() {
        return api() != null;
    }

    /**
     * Whether {@code viewerId} is allowed to see {@code targetId}'s nameplate.
     *
     * If MysticVanish is missing, not registered, or something fails, this
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

    /**
     * Whether {@code playerId} is currently vanished. Returns {@code false}
     * when MysticVanish is unavailable.
     */
    public static boolean isVanished(@Nullable UUID playerId) {
        if (playerId == null) {
            return false;
        }

        Object api = api();
        if (api == null) {
            return false;
        }

        try {
            Object result = isVanishedMethod.invoke(api, playerId);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
