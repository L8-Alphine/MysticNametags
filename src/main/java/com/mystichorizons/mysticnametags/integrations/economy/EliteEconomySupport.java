package com.mystichorizons.mysticnametags.integrations.economy;

import com.eliteessentials.api.EconomyAPI;

import java.util.UUID;

/**
 * Direct EliteEssentials EconomyAPI hook.
 *
 * Priority rules (see IntegrationManager):
 *  - VaultUnlockedSupport is checked first.
 *  - EliteEconomySupport is only used if VaultUnlocked is NOT available.
 */
public final class EliteEconomySupport {

    private EliteEconomySupport() {}

    public static boolean isAvailable() {
        try {
            return EconomyAPI.isEnabled();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public static double getBalance(UUID uuid) {
        if (!isAvailable()) {
            return 0.0D;
        }
        try {
            return EconomyAPI.getBalance(uuid);
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    public static boolean has(UUID uuid, double amount) {
        if (!isAvailable() || amount <= 0.0D) {
            return true;
        }
        try {
            return EconomyAPI.has(uuid, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean withdraw(UUID uuid, double amount) {
        if (!isAvailable() || amount <= 0.0D) {
            return false;
        }
        try {
            return EconomyAPI.withdraw(uuid, amount);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
