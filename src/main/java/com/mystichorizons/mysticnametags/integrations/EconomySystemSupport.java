package com.mystichorizons.mysticnametags.integrations;

import com.economy.api.EconomyAPI;

import java.util.UUID;

/**
 * Primary EconomySystem (com.economy.api.EconomyAPI) support.
 *
 * This is treated as the main economy backend. Other systems
 * (VaultUnlocked, EliteEssentials) are fallbacks only.
 */
public final class EconomySystemSupport {

    private static volatile EconomyAPI api;

    private EconomySystemSupport() {}

    private static EconomyAPI getApi() {
        EconomyAPI cached = api;
        if (cached != null) {
            return cached;
        }

        try {
            EconomyAPI instance = EconomyAPI.getInstance();
            api = instance;
            return instance;
        } catch (Throwable t) {
            // API not present or plugin not loaded
            return null;
        }
    }

    public static boolean isAvailable() {
        return getApi() != null;
    }

    public static double getBalance(UUID uuid) {
        EconomyAPI api = getApi();
        if (api == null || uuid == null) {
            return 0.0D;
        }
        try {
            return api.getBalance(uuid);
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    public static boolean has(UUID uuid, double amount) {
        if (amount <= 0.0D || uuid == null) {
            return true;
        }

        EconomyAPI api = getApi();
        if (api == null) {
            return false;
        }

        try {
            return api.hasBalance(uuid, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean withdraw(UUID uuid, double amount) {
        if (amount <= 0.0D || uuid == null) {
            return false;
        }

        EconomyAPI api = getApi();
        if (api == null) {
            return false;
        }

        try {
            // EconomySystem uses "removeBalance" for withdrawals
            return api.removeBalance(uuid, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean deposit(UUID uuid, double amount) {
        if (amount <= 0.0D || uuid == null) {
            return false;
        }

        EconomyAPI api = getApi();
        if (api == null) {
            return false;
        }

        try {
            api.addBalance(uuid, amount);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
