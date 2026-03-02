package com.mystichorizons.mysticnametags.placeholders;

import at.helpch.placeholderapi.PlaceholderAPI;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.logging.Level;

public final class HelpchPlaceholderHook {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile boolean initialized = false;
    private static volatile boolean available   = false;

    private static Class<?> apiClass;
    private static Method setPlaceholdersMethod;

    public void register() {
        // Runtime check so we don't hard-fail if PlaceholderAPI isn't on the classpath
        try {
            Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
        } catch (ClassNotFoundException ex) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] at.helpch PlaceholderAPI not found; skipping helpch expansion.");
            return;
        }

        initReflection();

        boolean success = new MysticTagsHelpchExpansion(MysticNameTagsPlugin.getInstance()).register();

        if (success) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Registered at.helpch PlaceholderAPI expansion 'mystictags'. "
                            + "Placeholders: %mystictags_tag%, %mystictags_tag_plain%, "
                            + "%mystictags_full%, %mystictags_full_plain%");
        } else {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Failed to register at.helpch PlaceholderAPI expansion 'mystictags'.");
        }
    }

    /**
     * Lazily initialize reflection handle for PlaceholderAPI#setPlaceholders.
     *
     * We do this via reflection so the dependency remains optional and so we
     * can handle different signatures more gracefully.
     */
    private static void initReflection() {
        if (initialized) return;

        synchronized (HelpchPlaceholderHook.class) {
            if (initialized) return;

            try {
                apiClass = Class.forName("at.helpch.placeholderapi.PlaceholderAPI");

                // Try to find a static setPlaceholders(.., String) method with 2 parameters.
                // We don't hardcode the first parameter type, to allow different player types.
                for (Method m : apiClass.getMethods()) {
                    if (!m.getName().equals("setPlaceholders")) continue;
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length != 2) continue;
                    if (!String.class.equals(params[1])) continue; // second param must be String
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;

                    setPlaceholdersMethod = m;
                    break;
                }

                if (setPlaceholdersMethod != null) {
                    available = true;
                } else {
                    available = false;
                }
            } catch (ClassNotFoundException e) {
                available = false;
            } finally {
                initialized = true;
            }
        }
    }

    private static boolean isApiReady() {
        if (!initialized) {
            initReflection();
        }
        return available && setPlaceholdersMethod != null;
    }

    /**
     * Resolve a *single* placeholder using Helpch PlaceholderAPI.
     *
     * Returns:
     *  - resolved, trimmed string if it changed and is non-empty
     *  - null if unchanged / empty / API unavailable / error
     */
    @Nullable
    public static String resolve(@Nonnull PlayerRef playerRef,
                                 @Nonnull String placeholder) {
        if (placeholder == null || placeholder.isEmpty()) {
            return null;
        }

        if (!isApiReady()) {
            return null;
        }

        try {
            Object out = setPlaceholdersMethod.invoke(null, playerRef, placeholder);
            if (!(out instanceof String result)) {
                return null;
            }

            result = result.trim();
            if (result.isEmpty() || result.equals(placeholder)) {
                return null;
            }

            return result;
        } catch (Throwable t) {
            // Don't spam logs – this is called quite frequently in some contexts.
            return null;
        }
    }
}