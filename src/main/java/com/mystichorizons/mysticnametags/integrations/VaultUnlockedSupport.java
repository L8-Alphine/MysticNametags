package com.mystichorizons.mysticnametags.integrations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

public final class VaultUnlockedSupport {

    private static boolean initialized = false;
    private static boolean available   = false;

    private static Class<?> servicesClass;
    private static Class<?> economyClass;
    private static Method getMethod;
    private static Method economyWithdrawMethod;
    private static Method economyGetBalanceMethod;

    private static Object economyInstance;

    private VaultUnlockedSupport() {}

    private static void init() {
        if (initialized) return;

        try {
            servicesClass = Class.forName("net.cfh.vault.VaultUnlockedServicesManager");
            economyClass  = Class.forName("net.milkbowl.vault2.economy.Economy");

            // static VaultUnlockedServicesManager.get()
            getMethod = servicesClass.getMethod("get");

            // Economy methods: withdraw(String, UUID, BigDecimal) and getBalance(String, UUID)
            economyWithdrawMethod = economyClass.getMethod(
                    "withdraw", String.class, UUID.class, BigDecimal.class
            );
            economyGetBalanceMethod = economyClass.getMethod(
                    "getBalance", String.class, UUID.class
            );

            // Resolve the economy instance once
            Object services = getMethod.invoke(null);
            Method economyObj = servicesClass.getMethod("economyObj");
            Object eco = economyObj.invoke(services);

            if (eco != null) {
                economyInstance = eco;
                available = true;
            }
        } catch (ClassNotFoundException | NoSuchMethodException |
                 IllegalAccessException | InvocationTargetException e) {
            available = false;
        } finally {
            initialized = true;
        }
    }

    public static boolean isAvailable() {
        if (!initialized) init();
        return available && economyInstance != null;
    }

    public static boolean withdraw(String pluginName, UUID uuid, double amount) {
        if (!isAvailable() || amount <= 0.0D) return false;
        try {
            BigDecimal value = BigDecimal.valueOf(amount);
            Object response = economyWithdrawMethod.invoke(economyInstance, pluginName, uuid, value);
            // EconomyResponse has transactionSuccess():boolean – reflect it:
            Method successMethod = response.getClass().getMethod("transactionSuccess");
            Object ok = successMethod.invoke(response);
            return ok instanceof Boolean && (Boolean) ok;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static double getBalance(String pluginName, UUID uuid) {
        if (!isAvailable()) return 0.0D;
        try {
            Object bal = economyGetBalanceMethod.invoke(economyInstance, pluginName, uuid);
            if (bal instanceof BigDecimal bd) {
                return bd.doubleValue();
            }
        } catch (Throwable ignored) {
        }
        return 0.0D;
    }
}
