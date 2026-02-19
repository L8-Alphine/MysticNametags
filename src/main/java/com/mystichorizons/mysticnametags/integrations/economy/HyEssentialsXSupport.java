package com.mystichorizons.mysticnametags.integrations.economy;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * HyEssentialsX economy support (xyz.thelegacyvoyage.hyessentialsx.*).
 *
 * Uses reflection so MysticNameTags can run without HyEssentialsX installed.
 *
 * We only integrate the EconomyApi portion:
 * - isEnabled()
 * - getBalance(uuid)
 * - withdraw(uuid, amount)
 * - deposit(uuid, amount)
 */
public final class HyEssentialsXSupport {

    private static volatile Object cachedApi;        // HyEssentialsXApi
    private static volatile Object cachedEconomyApi; // EconomyApi (inner interface in api package)

    private HyEssentialsXSupport() {}

    // ---------------------------------------------------------------------
    // Public API (used by IntegrationManager)
    // ---------------------------------------------------------------------

    public static boolean isAvailable() {
        return getEconomyApi() != null && isEconomyEnabled();
    }

    public static double getBalance(UUID uuid) {
        if (uuid == null) return 0.0D;
        Object eco = getEconomyApi();
        if (eco == null) return 0.0D;

        try {
            // long getBalance(UUID)
            Method m = eco.getClass().getMethod("getBalance", UUID.class);
            Object out = m.invoke(eco, uuid);
            if (out instanceof Long l) return (double) l;
            return 0.0D;
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    public static boolean has(UUID uuid, double amount) {
        if (uuid == null) return false;
        if (amount <= 0.0D) return true;

        long a = toLongAmount(amount);
        return getBalance(uuid) >= (double) a;
    }

    public static boolean withdraw(UUID uuid, double amount) {
        if (uuid == null) return false;
        if (amount <= 0.0D) return true;

        Object eco = getEconomyApi();
        if (eco == null) return false;

        long a = toLongAmount(amount);

        try {
            // boolean withdraw(UUID, long)
            Method m = eco.getClass().getMethod("withdraw", UUID.class, long.class);
            Object out = m.invoke(eco, uuid, a);
            return out instanceof Boolean b && b;
        } catch (NoSuchMethodException nsme) {
            // Some economies use (uuid, amount) and return void or long; try a couple fallbacks.
            return tryWithdrawFallbacks(eco, uuid, a);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private static long toLongAmount(double amount) {
        // Match your existing approach (coins path uses rounded int).
        // If HyEssentialsX uses whole-units longs, rounding is reasonable.
        // If it uses "cents", you’d want a multiplier here instead.
        return Math.max(0L, Math.round(amount));
    }

    private static boolean isEconomyEnabled() {
        Object eco = getEconomyApi();
        if (eco == null) return false;

        try {
            // boolean isEnabled()
            Method m = eco.getClass().getMethod("isEnabled");
            Object out = m.invoke(eco);
            return out instanceof Boolean b && b;
        } catch (Throwable ignored) {
            // If method missing, assume enabled if api exists.
            return true;
        }
    }

    @Nullable
    private static Object getEconomyApi() {
        Object cached = cachedEconomyApi;
        if (cached != null) return cached;

        Object api = getHyEssentialsXApi();
        if (api == null) return null;

        try {
            // EconomyApi economy()
            Method m = api.getClass().getMethod("economy");
            Object eco = m.invoke(api);
            cachedEconomyApi = eco;
            return eco;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Object getHyEssentialsXApi() {
        Object cached = cachedApi;
        if (cached != null) return cached;

        // Strategy A: common "getInstance()" / "get()" patterns on HyEssentialsXApi
        Object viaApiClass = tryStaticFactory(
                "xyz.thelegacyvoyage.hyessentialsx.api.HyEssentialsXApi",
                new String[]{"getInstance", "get", "instance"}
        );
        if (viaApiClass != null) {
            cachedApi = viaApiClass;
            return viaApiClass;
        }

        // Strategy B: plugin main class often exposes api()
        Object viaPlugin = tryStaticFactory(
                "xyz.thelegacyvoyage.hyessentialsx.HyEssentialsX",
                new String[]{"getApi", "getAPI", "api", "getInstance", "get"}
        );
        if (viaPlugin != null) {
            // If we got plugin instance, try instance.api() too
            Object api = unwrapApiFromPluginInstance(viaPlugin);
            if (api != null) {
                cachedApi = api;
                return api;
            }

            // Or it might have returned the API directly
            cachedApi = viaPlugin;
            return viaPlugin;
        }

        // Strategy C: try a known provider class name (if they have one)
        Object viaProvider = tryStaticFactory(
                "xyz.thelegacyvoyage.hyessentialsx.api.HyEssentialsXApiProvider",
                new String[]{"get", "getInstance", "api"}
        );
        if (viaProvider != null) {
            cachedApi = viaProvider;
            return viaProvider;
        }

        return null;
    }

    @Nullable
    private static Object unwrapApiFromPluginInstance(Object pluginInstance) {
        try {
            Method m = pluginInstance.getClass().getMethod("api");
            return m.invoke(pluginInstance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Object tryStaticFactory(String className, String[] methodNames) {
        try {
            Class<?> cls = Class.forName(className);
            for (String name : methodNames) {
                try {
                    Method m = cls.getMethod(name);
                    Object out = m.invoke(null);
                    if (out != null) return out;
                } catch (NoSuchMethodException ignored) {
                    // try next
                }
            }
        } catch (Throwable ignored) {
            // class not present or invocation failed
        }
        return null;
    }

    private static boolean tryWithdrawFallbacks(Object eco, UUID uuid, long amount) {
        // Fallback 1: withdraw(UUID, long) -> void/long (already tried boolean)
        try {
            Method m = eco.getClass().getMethod("withdraw", UUID.class, long.class);
            Object out = m.invoke(eco, uuid, amount);
            if (out == null) return true;
            if (out instanceof Boolean b) return b;
            if (out instanceof Long) return true;
        } catch (Throwable ignored) {}

        // Fallback 2: setBalance(uuid, newBalance)
        try {
            Method get = eco.getClass().getMethod("getBalance", UUID.class);
            Object balObj = get.invoke(eco, uuid);
            long bal = (balObj instanceof Long l) ? l : 0L;
            if (bal < amount) return false;

            Method set = eco.getClass().getMethod("setBalance", UUID.class, long.class);
            set.invoke(eco, uuid, (bal - amount));
            return true;
        } catch (Throwable ignored) {}

        return false;
    }
}
