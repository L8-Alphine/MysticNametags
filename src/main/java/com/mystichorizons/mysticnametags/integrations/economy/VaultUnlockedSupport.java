package com.mystichorizons.mysticnametags.integrations.economy;

import net.cfh.vault.VaultUnlockedServicesManager;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import net.milkbowl.vault2.economy.EconomyResponse.ResponseType;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

public final class VaultUnlockedSupport {

    // Cached provider; re-resolved if it goes null / disabled
    private static volatile Economy economy;

    private VaultUnlockedSupport() {}

    private static Economy resolveEconomy() {
        // If we already have a live provider, keep using it
        Economy cached = economy;
        if (cached != null && cached.isEnabled()) {
            return cached;
        }

        try {
            Economy eco = VaultUnlockedServicesManager.get().economyObj();
            if (eco != null && eco.isEnabled()) {
                economy = eco;
                return eco;
            }
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked not on classpath
            return null;
        } catch (Throwable ignored) {
            return null;
        }

        return null;
    }

    public static boolean isAvailable() {
        return resolveEconomy() != null;
    }

    public static double getBalance(String pluginName, UUID uuid) {
        Economy eco = resolveEconomy();
        if (eco == null || uuid == null) {
            return 0.0D;
        }

        // Prefer the typed API first
        try {
            BigDecimal bal = eco.balance(pluginName, uuid);
            return bal.doubleValue();
        } catch (Throwable ignored) {
            // Fallback: try a simpler getBalance(UUID) like EliteEssentials does
            try {
                Method m = eco.getClass().getMethod("getBalance", UUID.class);
                Object result = m.invoke(eco, uuid);

                if (result instanceof BigDecimal bd) {
                    return bd.doubleValue();
                } else if (result instanceof Number n) {
                    return n.doubleValue();
                }
            } catch (Throwable ignored2) {
                // Give up and return 0
            }
        }

        return 0.0D;
    }

    public static boolean withdraw(String pluginName, UUID uuid, double amount) {
        if (amount <= 0.0D || uuid == null) return false;

        Economy eco = resolveEconomy();
        if (eco == null) {
            return false;
        }

        BigDecimal value = BigDecimal.valueOf(amount);

        // 1) Try modern VaultUnlocked Economy API
        try {
            EconomyResponse response = eco.withdraw(pluginName, uuid, value);
            return response.type == ResponseType.SUCCESS;
        } catch (Throwable ignored) {
            // 2) Fallback: try an external-style withdraw(UUID, BigDecimal)
            try {
                Method m = eco.getClass().getMethod("withdraw", UUID.class, BigDecimal.class);
                Object resp = m.invoke(eco, uuid, value);

                // Some providers may still return EconomyResponse-like objects
                if (resp != null && resp.getClass().getName().contains("EconomyResponse")) {
                    // Try getType()
                    try {
                        Method typeMethod = resp.getClass().getMethod("getType");
                        Object type = typeMethod.invoke(resp);
                        if ("SUCCESS".equalsIgnoreCase(String.valueOf(type))) {
                            return true;
                        }
                    } catch (Throwable ignored2) {
                        // Try type()
                        try {
                            Method typeMethod = resp.getClass().getMethod("type");
                            Object type = typeMethod.invoke(resp);
                            if ("SUCCESS".equalsIgnoreCase(String.valueOf(type))) {
                                return true;
                            }
                        } catch (Throwable ignored3) {
                            // ignore
                        }
                    }
                }

                // Or they might just return a boolean
                if (resp instanceof Boolean b) {
                    return b;
                }
            } catch (Throwable ignored2) {
                // No compatible withdraw method
            }
        }

        return false;
    }
}
