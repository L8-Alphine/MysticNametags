package com.mystichorizons.mysticnametags.integrations.economy;

import java.util.UUID;

/**
 * Small wrappers that adapt your existing support classes to EconomyBackend.
 */
public final class LedgerBackends {

    private LedgerBackends() {}

    public static EconomyBackend economySystemLedger(boolean useCoins) {
        return new EconomyBackend() {
            @Override public String name() { return useCoins ? "EconomySystem(Cash)" : "EconomySystem(Bank)"; }
            @Override public boolean isAvailable() { return EconomySystemSupport.isAvailable(); }

            @Override
            public double getBalance(UUID uuid) {
                if (!isAvailable()) return 0.0D;
                return useCoins ? EconomySystemSupport.getCoins(uuid) : EconomySystemSupport.getBalance(uuid);
            }

            @Override
            public boolean has(UUID uuid, double amount) {
                if (amount <= 0.0D) return true;
                if (!isAvailable()) return false;
                if (useCoins) return EconomySystemSupport.hasCoins(uuid, (int)Math.round(amount));
                return EconomySystemSupport.has(uuid, amount);
            }

            @Override
            public boolean withdraw(UUID uuid, double amount) {
                if (amount <= 0.0D) return true;
                if (!isAvailable()) return false;
                if (useCoins) return EconomySystemSupport.withdrawCoins(uuid, (int)Math.round(amount));
                return EconomySystemSupport.withdraw(uuid, amount);
            }
        };
    }

    public static EconomyBackend ecoTale() {
        return new EconomyBackend() {
            @Override public String name() { return "EcoTale"; }
            @Override public boolean isAvailable() { return EcoTaleSupport.isAvailable(); }

            @Override public double getBalance(UUID uuid) { return isAvailable() ? EcoTaleSupport.getBalance(uuid) : 0.0D; }
            @Override public boolean has(UUID uuid, double amount) { return amount <= 0.0D || (isAvailable() && EcoTaleSupport.getBalance(uuid) >= amount); }
            @Override public boolean withdraw(UUID uuid, double amount) { return amount <= 0.0D || (isAvailable() && EcoTaleSupport.withdraw(uuid, amount)); }
        };
    }

    public static EconomyBackend hyEssentialsX() {
        return new EconomyBackend() {
            @Override public String name() { return "HyEssentialsX"; }
            @Override public boolean isAvailable() { return HyEssentialsXSupport.isAvailable(); }

            @Override public double getBalance(UUID uuid) { return isAvailable() ? HyEssentialsXSupport.getBalance(uuid) : 0.0D; }
            @Override public boolean has(UUID uuid, double amount) { return amount <= 0.0D || (isAvailable() && HyEssentialsXSupport.has(uuid, amount)); }
            @Override public boolean withdraw(UUID uuid, double amount) { return amount <= 0.0D || (isAvailable() && HyEssentialsXSupport.withdraw(uuid, amount)); }
        };
    }

    public static EconomyBackend vaultUnlocked(String pluginName) {
        return new EconomyBackend() {
            @Override public String name() { return "VaultUnlocked"; }
            @Override public boolean isAvailable() { return VaultUnlockedSupport.isAvailable(); }

            @Override
            public double getBalance(UUID uuid) {
                return isAvailable() ? VaultUnlockedSupport.getBalance(pluginName, uuid) : 0.0D;
            }

            @Override
            public boolean has(UUID uuid, double amount) {
                return amount <= 0.0D || (isAvailable() && VaultUnlockedSupport.getBalance(pluginName, uuid) >= amount);
            }

            @Override
            public boolean withdraw(UUID uuid, double amount) {
                return amount <= 0.0D || (isAvailable() && VaultUnlockedSupport.withdraw(pluginName, uuid, amount));
            }
        };
    }

    public static EconomyBackend eliteEssentials() {
        return new EconomyBackend() {
            @Override public String name() { return "EliteEssentials"; }
            @Override public boolean isAvailable() { return EliteEconomySupport.isAvailable(); }

            @Override public double getBalance(UUID uuid) { return isAvailable() ? EliteEconomySupport.getBalance(uuid) : 0.0D; }
            @Override public boolean has(UUID uuid, double amount) { return amount <= 0.0D || (isAvailable() && EliteEconomySupport.has(uuid, amount)); }
            @Override public boolean withdraw(UUID uuid, double amount) { return amount <= 0.0D || (isAvailable() && EliteEconomySupport.withdraw(uuid, amount)); }
        };
    }
}
