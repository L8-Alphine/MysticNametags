package com.mystichorizons.mysticnametags.integrations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Soft hook for EliteEssentials EconomyAPI.
 *
 * Priority rules (in IntegrationManager):
 *  - VaultUnlockedSupport is checked first.
 *  - EliteEconomySupport is used only if VaultUnlocked is NOT available.
 */
public final class EliteEconomySupport {

    private static boolean initialized = false;
    private static boolean available   = false;

    private static Class<?> apiClass;
    private static Method isEnabledMethod;
    private static Method getBalanceMethod;
    private static Method hasMethod;
    private static Method withdrawMethod;

    private EliteEconomySupport() {}

    private static void init() {
        if (initialized) {
            return;
        }

        try {
            // com.eliteessentials.api.EconomyAPI
            apiClass = Class.forName("com.eliteessentials.api.EconomyAPI");

            isEnabledMethod   = apiClass.getMethod("isEnabled");
            getBalanceMethod  = apiClass.getMethod("getBalance", UUID.class);
            hasMethod         = apiClass.getMethod("has", UUID.class, double.class);
            withdrawMethod    = apiClass.getMethod("withdraw", UUID.class, double.class);

            // Respect EliteEssentials' own economy.enabled flag
            Object enabledObj = isEnabledMethod.invoke(null);
            boolean enabled = (enabledObj instanceof Boolean) && (Boolean) enabledObj;
            available = enabled;
        } catch (ClassNotFoundException | NoSuchMethodException |
                 IllegalAccessException | InvocationTargetException e) {
            available = false;
        } finally {
            initialized = true;
        }
    }

    public static boolean isAvailable() {
        if (!initialized) {
            init();
        }
        return available;
    }

    public static double getBalance(UUID uuid) {
        if (!isAvailable()) {
            return 0.0D;
        }
        try {
            Object result = getBalanceMethod.invoke(null, uuid);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return 0.0D;
    }

    public static boolean has(UUID uuid, double amount) {
        if (!isAvailable() || amount <= 0.0D) {
            return true;
        }
        try {
            Object result = hasMethod.invoke(null, uuid, amount);
            return (result instanceof Boolean b) && b;
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean withdraw(UUID uuid, double amount) {
        if (!isAvailable() || amount <= 0.0D) {
            return false;
        }
        try {
            Object result = withdrawMethod.invoke(null, uuid, amount);
            return (result instanceof Boolean b) && b;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
