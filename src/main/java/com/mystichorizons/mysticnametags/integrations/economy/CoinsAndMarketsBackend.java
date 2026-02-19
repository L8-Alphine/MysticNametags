package com.mystichorizons.mysticnametags.integrations.economy;

import com.coinsandmarkets.api.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Objects;
import java.util.UUID;

/**
 * CoinsAndMarkets physical currency backend (pouch + inventory).
 *
 * CoinsAndMarkets uses com.coinsandmarkets.api.PlayerRef which is a simple
 * wrapper over a string player id (usually UUID string).
 */
public final class CoinsAndMarketsBackend {

    private final CoinsAndMarketsApi api;

    public CoinsAndMarketsBackend(CoinsAndMarketsApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public String name() {
        return "CoinsAndMarkets(Physical)";
    }

    public boolean isAvailable() {
        return api != null;
    }

    public long getPhysicalWealthCopper(PlayerRef hytalePlayer) {
        if (hytalePlayer == null || api == null) return 0L;
        return api.getPhysicalWealth(toCoinsPlayerRef(hytalePlayer));
    }

    public boolean hasCopper(PlayerRef hytalePlayer, long amountCopper) {
        if (amountCopper <= 0L) return true;
        return getPhysicalWealthCopper(hytalePlayer) >= amountCopper;
    }

    public PaymentResult withdrawCopper(PlayerRef hytalePlayer, long amountCopper) {
        if (api == null) {
            return PaymentResult.chargeFailure(amountCopper, ChargeSource.POUCH_THEN_INVENTORY, PaymentFailureReason.PLAYER_UNAVAILABLE);
        }
        return api.tryChargeDetailed(toCoinsPlayerRef(hytalePlayer), amountCopper, ChargeSource.POUCH_THEN_INVENTORY);
    }

    public String formatCopper(long copper) {
        if (api == null) return String.valueOf(copper);
        try {
            return api.formatAmount(copper);
        } catch (Throwable ignored) {
            return String.valueOf(copper);
        }
    }

    private static com.coinsandmarkets.api.PlayerRef toCoinsPlayerRef(PlayerRef hytale) {
        // CoinsAndMarketsPlugin resolves playerId from UUID first, username fallback.
        UUID uuid = hytale.getUuid();
        String username = hytale.getUsername();

        String id = (uuid != null) ? uuid.toString() : (username != null ? username : "");
        return new com.coinsandmarkets.api.PlayerRef(id);
    }
}
