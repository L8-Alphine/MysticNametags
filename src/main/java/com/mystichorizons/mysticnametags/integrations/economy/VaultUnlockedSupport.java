package com.mystichorizons.mysticnametags.integrations.economy;

import net.cfh.vault.VaultUnlockedServicesManager;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import net.milkbowl.vault2.economy.EconomyResponse.ResponseType;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public final class VaultUnlockedSupport {

    // Cached provider; re-resolved when the services manager offers a new one
    private static volatile Economy economy;
    private static volatile boolean loggedProvider;
    private static volatile boolean loggedApiOnly;

    private VaultUnlockedSupport() {}

    public static boolean isApiAvailable() {
        try {
            return VaultUnlockedServicesManager.get() != null;
        } catch (NoClassDefFoundError e) {
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static List<String> getProviderNames() {
        try {
            LinkedList<String> names = VaultUnlockedServicesManager.get().economyProviderNames();
            if (names == null || names.isEmpty()) {
                return List.of();
            }
            return List.copyOf(names);
        } catch (NoClassDefFoundError e) {
            return List.of();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static Economy resolveEconomy() {
        try {
            VaultUnlockedServicesManager manager = VaultUnlockedServicesManager.get();

            Economy eco = manager.economy().orElse(null);

            // Some services-manager builds return an empty default lookup even
            // though providers are registered by name - fall back to the first
            // registered provider name.
            if (eco == null) {
                for (String name : manager.economyProviderNames()) {
                    if (name == null) continue;
                    eco = manager.economyObj(name);
                    if (eco != null) break;
                }
            }

            if (eco == null) {
                if (!loggedApiOnly) {
                    loggedApiOnly = true;
                    com.mystichorizons.mysticnametags.util.MysticLog.info(
                            "VaultUnlocked API detected, but no economy provider is registered yet.");
                }
                return economy; // last known good provider, if any
            }

            // Do NOT require isEnabled() here: several providers register a
            // working economy that misreports isEnabled() as false, which
            // previously made this plugin claim the economy was disabled.
            if (economy != eco) {
                economy = eco;
                if (!loggedProvider) {
                    loggedProvider = true;
                    boolean enabled;
                    try {
                        enabled = eco.isEnabled();
                    } catch (Throwable t) {
                        enabled = true;
                    }
                    com.mystichorizons.mysticnametags.util.MysticLog.info(
                            "VaultUnlocked economy provider resolved: "
                                    + eco.getClass().getName() + " (isEnabled=" + enabled + ")");
                }
            }
            return eco;
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked not on classpath
            return null;
        } catch (Throwable ignored) {
            return economy;
        }
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
