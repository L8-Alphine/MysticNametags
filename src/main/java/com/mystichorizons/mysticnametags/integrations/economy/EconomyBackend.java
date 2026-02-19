package com.mystichorizons.mysticnametags.integrations.economy;

import java.util.UUID;

/**
 * Unified economy backend abstraction for MysticNameTags.
 *
 * NOTE:
 * - This interface is UUID/ledger-centric (bank balance style).
 * - Physical / inventory-based economies should be used via a separate PlayerRef-aware adapter.
 */
public interface EconomyBackend {

    /** Backend identifier for logs. */
    String name();

    /** True if this backend is usable at runtime. */
    boolean isAvailable();

    /** Return current balance for a player (ledger currency). */
    double getBalance(UUID uuid);

    /** Check if player has at least amount. */
    boolean has(UUID uuid, double amount);

    /** Withdraw amount from player. Returns true if successful. */
    boolean withdraw(UUID uuid, double amount);
}
