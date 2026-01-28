package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * WiFlow integration using reflection only, so WiFlowPlaceholderAPI remains
 * a true optional dependency. If the WiFlow jar isn't present or anything
 * fails, this just returns the original text unchanged.
 *
 * Expected WiFlow types (by name):
 *  - com.wiflow.placeholderapi.WiFlowPlaceholderAPI
 *  - com.wiflow.placeholderapi.context.PlaceholderContext
 */
public final class WiFlowPlaceholderSupport {

    private static volatile boolean initialized = false;
    private static volatile boolean available   = false;

    private static Class<?> apiClass;
    private static Class<?> contextClass;

    private static Method containsPlaceholdersMethod;
    private static Method setPlaceholdersMethod;
    private static Method offlineContextMethod;
    private static Method isInitializedMethod;

    private WiFlowPlaceholderSupport() {
    }

    private static void init() {
        if (initialized) {
            return;
        }

        synchronized (WiFlowPlaceholderSupport.class) {
            if (initialized) {
                return;
            }

            try {
                apiClass = Class.forName("com.wiflow.placeholderapi.WiFlowPlaceholderAPI");
                contextClass = Class.forName("com.wiflow.placeholderapi.context.PlaceholderContext");

                // static boolean isInitialized()
                isInitializedMethod = apiClass.getMethod("isInitialized");

                // static boolean containsPlaceholders(String)
                containsPlaceholdersMethod = apiClass.getMethod("containsPlaceholders", String.class);

                // static PlaceholderContext offline(UUID, String)
                offlineContextMethod = contextClass.getMethod("offline", UUID.class, String.class);

                // static String setPlaceholders(PlaceholderContext, String)
                setPlaceholdersMethod = apiClass.getMethod("setPlaceholders", contextClass, String.class);

                available = true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                // WiFlow not present or unexpected version – treat as unavailable
                available = false;
            } finally {
                initialized = true;
            }
        }
    }

    private static boolean isApiReady() {
        if (!initialized) {
            init();
        }
        if (!available) {
            return false;
        }

        try {
            Object result = isInitializedMethod.invoke(null);
            if (result instanceof Boolean b) {
                return b;
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }

        return false;
    }

    /**
     * Apply WiFlow placeholders for the given player and text.
     *
     * If WiFlow is missing, not initialized, or something fails, the
     * original text is returned unchanged.
     */
    public static String apply(@Nullable PlayerRef playerRef,
                               @Nullable String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (!isApiReady()) {
            return text;
        }

        try {
            // Fast path – if string doesn't contain any WiFlow placeholders, skip everything
            Object has = containsPlaceholdersMethod.invoke(null, text);
            if (!(has instanceof Boolean) || !((Boolean) has)) {
                return text;
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            return text;
        }

        if (playerRef == null) {
            // You could later add a "global" context here if WiFlow supports it.
            return text;
        }

        UUID uuid = playerRef.getUuid();
        String name = playerRef.getUsername();
        if (uuid == null || name == null) {
            return text;
        }

        try {
            Object context = offlineContextMethod.invoke(null, uuid, name);
            Object out = setPlaceholdersMethod.invoke(null, context, text);
            return (out instanceof String s) ? s : text;
        } catch (IllegalAccessException | InvocationTargetException e) {
            return text;
        }
    }

    /**
     * Overload for UUID + name only.
     */
    public static String apply(@Nullable UUID uuid,
                               @Nullable String name,
                               @Nullable String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (!isApiReady()) {
            return text;
        }

        try {
            Object has = containsPlaceholdersMethod.invoke(null, text);
            if (!(has instanceof Boolean) || !((Boolean) has)) {
                return text;
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            return text;
        }

        if (uuid == null || name == null) {
            return text;
        }

        try {
            Object context = offlineContextMethod.invoke(null, uuid, name);
            Object out = setPlaceholdersMethod.invoke(null, context, text);
            return (out instanceof String s) ? s : text;
        } catch (IllegalAccessException | InvocationTargetException e) {
            return text;
        }
    }
}
