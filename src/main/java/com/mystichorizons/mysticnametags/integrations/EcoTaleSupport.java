package com.mystichorizons.mysticnametags.integrations;

import com.ecotale.api.EcotaleAPI;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Thin wrapper around EcoTale's API so IntegrationManager
 * doesn't need to know implementation details.
 */
public final class EcoTaleSupport {

    private static final String REASON_PREFIX = "MysticNameTags: ";

    private EcoTaleSupport() {
    }

    public static boolean isAvailable() {
        try {
            // If this class is present, EcoTale is on the classpath.
            Class.forName("com.ecotale.api.EcotaleAPI", false,
                    EcoTaleSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public static double getBalance(@Nonnull UUID uuid) {
        return EcotaleAPI.getBalance(uuid);
    }

    public static boolean withdraw(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        // EcoTale expects a "reason" string; keep it simple.
        EcotaleAPI.withdraw(uuid, amount, REASON_PREFIX + "Tag purchase");
        return true; // adjust if EcoTale returns boolean in your build
    }
}
