package com.mystichorizons.mysticnametags.integrations.economy;

import com.economy.api.EconomyAPI;
import com.economy.economy.EconomyManager;

import java.util.UUID;

/**
 * Primary EconomySystem (com.economy.*) support.
 *
 * This is treated as the main economy backend. Other systems
 * (VaultUnlocked, EliteEssentials) are fallbacks only.
 */
public final class EconomySystemSupport {

    private static volatile EconomyAPI api;
    private static volatile EconomyManager manager;

    private EconomySystemSupport() {}

    // ---------------------------------------------------------------------
    // Balance (bank) API – existing behaviour
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Coin / cash API – backed by EconomyManager
    // ---------------------------------------------------------------------

    private static EconomyManager getManager() {
        EconomyManager cached = manager;
        if (cached != null) {
            return cached;
        }

        try {
            EconomyManager instance = EconomyManager.getInstance();
            manager = instance;
            return instance;
        } catch (Throwable t) {
            return null;
        }
    }

    public static int getCoins(UUID uuid) {
        EconomyManager mgr = getManager();
        if (mgr == null || uuid == null) {
            return 0;
        }
        try {
            return mgr.getCash(uuid);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean hasCoins(UUID uuid, int amount) {
        if (amount <= 0 || uuid == null) {
            return true;
        }
        EconomyManager mgr = getManager();
        if (mgr == null) {
            return false;
        }
        try {
            return mgr.hasCash(uuid, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean withdrawCoins(UUID uuid, int amount) {
        if (amount <= 0 || uuid == null) {
            return false;
        }
        EconomyManager mgr = getManager();
        if (mgr == null) {
            return false;
        }
        try {
            return mgr.subtractCash(uuid, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
