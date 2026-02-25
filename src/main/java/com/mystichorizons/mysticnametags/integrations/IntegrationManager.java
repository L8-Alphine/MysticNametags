package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.economy.*;
import com.mystichorizons.mysticnametags.integrations.endlessleveling.EndlessLevelingNameplateSystem;
import com.mystichorizons.mysticnametags.integrations.permissions.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;


public class IntegrationManager {

    private static final String ECON_PLUGIN_NAME = "MysticNameTags";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable private EndlessLevelingNameplateSystem endlessNameplateSystem;
    @Nullable private PlaytimeProvider playtimeProvider;
    @Nullable private CoinsAndMarketsBackend coinsAndMarketsBackend;
    @Nullable private StatProvider statProvider;
    @Nullable private ItemRequirementHandler itemRequirementHandler;

    public void setStatProvider(@Nullable StatProvider statProvider) {
        this.statProvider = statProvider;
    }

    @Nullable
    public StatProvider getStatProvider() {
        return statProvider;
    }

    private java.util.List<EconomyBackend> ledgerBackends = java.util.List.of();

    // Permission backends
    private LuckPermsSupport luckPermsSupport;
    private PermissionSupport permissionsBackend; // active backend (LuckPerms / PermissionsPlus / Native)

    // Prefix backend
    private PrefixesPlusSupport prefixesPlusSupport;

    private enum PermissionBackendType {
        LUCKPERMS,
        PERMISSIONS_PLUS,
        NATIVE
    }

    public enum EconomyMode {
        NONE,
        LEDGER,   // your current UUID-based chain
        PHYSICAL  // CoinsAndMarkets pouch+inventory
    }

    private EconomyMode economyMode = EconomyMode.NONE;

    private PermissionBackendType activePermissionBackend = PermissionBackendType.NATIVE;

    // Economy flags
    private boolean loggedEconomyStatus = false;

    public void init() {
        setupPermissionBackends();
        setupPrefixBackends();
        setupEconomyBackends();
        setupItemRequirementHandler();
    }

    // ----------------------------------------------------------------
    // Backend detection
    // ----------------------------------------------------------------

    private void setupPermissionBackends() {
        // 1) Try LuckPerms (defensive: may not exist at all)
        try {
            this.luckPermsSupport = new LuckPermsSupport();
            if (luckPermsSupport.isAvailable()) {
                this.permissionsBackend = luckPermsSupport;
                this.activePermissionBackend = PermissionBackendType.LUCKPERMS;
                LOGGER.at(Level.INFO).log("[MysticNameTags] Using LuckPerms for permissions.");
                return;
            } else {
                // Not actually available at runtime
                this.luckPermsSupport = null;
            }
        } catch (NoClassDefFoundError e) {
            // LuckPerms API classes not present on classpath
            this.luckPermsSupport = null;
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] LuckPerms API not found – skipping LuckPerms integration.");
        } catch (Throwable t) {
            // Any other weirdness, just skip LP
            this.luckPermsSupport = null;
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Error probing LuckPerms – skipping LuckPerms integration.");
        }

        // 2) Try PermissionsPlus / PermissionsModule (defensive as well)
        try {
            PermissionsPlusSupport permsPlus = new PermissionsPlusSupport();
            if (permsPlus.isAvailable()) {
                this.permissionsBackend = permsPlus;
                this.activePermissionBackend = PermissionBackendType.PERMISSIONS_PLUS;
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] Using PermissionsPlus / PermissionsModule for permissions.");
                return;
            }
        } catch (NoClassDefFoundError e) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] PermissionsPlus API not found – skipping PermissionsPlus integration.");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Error probing PermissionsPlus – skipping PermissionsPlus integration.");
        }

        // 3) Native Hytale permissions only
        this.permissionsBackend = new NativePermissionsSupport();
        this.activePermissionBackend = PermissionBackendType.NATIVE;
        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] No external permission plugin found – using native Hytale permissions.");
    }

    private void setupPrefixBackends() {
        // Try PrefixesPlus first
        try {
            this.prefixesPlusSupport = new PrefixesPlusSupport();
            if (prefixesPlusSupport.isAvailable()) {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] Detected PrefixesPlus – using it for rank prefixes.");
                return;
            } else {
                this.prefixesPlusSupport = null;
            }
        } catch (NoClassDefFoundError e) {
            this.prefixesPlusSupport = null;
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] PrefixesPlus API not found – skipping PrefixesPlus prefix provider.");
        } catch (Throwable t) {
            this.prefixesPlusSupport = null;
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Error probing PrefixesPlus – skipping PrefixesPlus prefix provider.");
        }

        // Fall back to LuckPerms meta if we successfully wired LuckPerms
        if (luckPermsSupport != null && luckPermsSupport.isAvailable()) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Using LuckPerms meta data for rank prefixes.");
        } else {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] No prefix provider detected – nameplates will only show tags + player names.");
        }
    }

    private void setupEconomyBackends() {
        this.coinsAndMarketsBackend = null;
        this.economyMode = EconomyMode.NONE;
        this.ledgerBackends = java.util.List.of();

        if (!Settings.get().isEconomySystemEnabled()) return;

        // 1) Physical Coins (CoinsAndMarkets)
        if (Settings.get().isUsePhysicalCoinEconomy()) {
            try {
                Object apiObj = tryGetCoinsApiSingleton();
                if (apiObj instanceof com.coinsandmarkets.api.CoinsAndMarketsApi api) {
                    this.coinsAndMarketsBackend = new CoinsAndMarketsBackend(api);
                    this.economyMode = EconomyMode.PHYSICAL;
                    LOGGER.at(Level.INFO).log("[MysticNameTags] Using CoinsAndMarkets (physical coins) for tag purchases.");
                    return;
                }

                LOGGER.at(Level.WARNING).log("[MysticNameTags] CoinsAndMarkets enabled in settings, but API not available. Falling back to ledger.");
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Failed to init CoinsAndMarkets. Falling back to ledger.");
            }
        }

        // 2) Ledger chain
        boolean useCoins = Settings.get().isUseCoinSystem();

        java.util.List<EconomyBackend> chain = new java.util.ArrayList<>();
        chain.add(LedgerBackends.economySystemLedger(useCoins));
        chain.add(LedgerBackends.hyEssentialsX());
        chain.add(LedgerBackends.ecoTale());
        chain.add(LedgerBackends.vaultUnlocked(ECON_PLUGIN_NAME));
        chain.add(LedgerBackends.eliteEssentials());

        // Keep only those that are actually present
        chain.removeIf(b -> !b.isAvailable());

        this.ledgerBackends = java.util.Collections.unmodifiableList(chain);
        if (!ledgerBackends.isEmpty()) {
            this.economyMode = EconomyMode.LEDGER;
        }
    }

    private void setupItemRequirementHandler() {
        try {
            this.itemRequirementHandler = new HytaleInventoryItemRequirementHandler();
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Item requirement handler enabled – tags can now require & consume items.");
        } catch (Throwable t) {
            this.itemRequirementHandler = null;
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to initialize HytaleInventoryItemRequirementHandler – item requirements will be disabled.");
        }
    }

    private Object tryGetCoinsApiSingleton() {
        try {
            Class<?> pluginClass = Class.forName("com.coinsandmarkets.platform.CoinsAndMarketsPlugin");
            Object plugin = pluginClass.getMethod("getInstance").invoke(null);
            if (plugin == null) return null;

            // api(): CoinsAndMarketsApi
            return pluginClass.getMethod("api").invoke(plugin);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean hasAnyLedgerEconomy() {
        return ledgerBackends != null && !ledgerBackends.isEmpty();
    }

    // ----------------------------------------------------------------
    // Prefix helpers
    // ----------------------------------------------------------------

    /**
     * Return the best available rank prefix for this player.
     * Order: PrefixesPlus -> LuckPerms -> null.
     */
    @Nullable
    public String getPrimaryPrefix(@Nonnull UUID uuid) {
        if (prefixesPlusSupport != null && prefixesPlusSupport.isAvailable()) {
            String p = prefixesPlusSupport.getPrefix(uuid);
            if (p != null && !p.isEmpty()) {
                return p;
            }
        }

        if (luckPermsSupport != null && luckPermsSupport.isAvailable()) {
            String p = luckPermsSupport.getPrefix(uuid);
            if (p != null && !p.isEmpty()) {
                return p;
            }
        }

        return null;
    }

    /**
     * Backwards compatible name used by TagManager / API.
     * Delegates to {@link #getPrimaryPrefix(UUID)}.
     */
    @Nullable
    public String getLuckPermsPrefix(@Nonnull UUID uuid) {
        return getPrimaryPrefix(uuid);
    }

    // ----------------------------------------------------------------
    // Permission helpers
    // ----------------------------------------------------------------

    public boolean isLuckPermsAvailable() {
        return luckPermsSupport != null && luckPermsSupport.isAvailable();
    }

    public boolean isPermissionsPlusActive() {
        return activePermissionBackend == PermissionBackendType.PERMISSIONS_PLUS;
    }

    @Nullable
    public PermissionSupport getPermissionsBackend() {
        return permissionsBackend;
    }

    /**
     * Generic permission check used by TagManager (player-based).
     * If no backend knows about the UUID, it falls back to "fail-open"
     * when LuckPerms is entirely missing, so tags remain usable with
     * other systems (like crates).
     */
    public boolean hasPermission(@Nonnull PlayerRef playerRef,
                                 @Nonnull String permissionNode) {

        UUID uuid = getUuidFromPlayerRef(playerRef);

        // If we have a backend, trust it
        if (uuid != null && permissionsBackend != null) {
            try {
                return permissionsBackend.hasPermission(uuid, permissionNode);
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Permission backend threw while checking '%s' for %s",
                                permissionNode, uuid);
                return false;
            }
        }

        // Only fail-open if we truly have no backend at all
        if (permissionsBackend == null) {
            return true;
        }

        return false;
    }

    /**
     * Permission check for command senders.
     * - Tries UUID + backend first
     * - Always falls back to sender.hasPermission(...)
     */
    public boolean hasPermission(@Nonnull CommandSender sender,
                                 @Nonnull String permissionNode) {

        if (sender == null) {
            return false;
        }

        try {
            UUID uuid = sender.getUuid();
            if (uuid != null && permissionsBackend != null) {
                if (permissionsBackend.hasPermission(uuid, permissionNode)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // fall through to native perms
        }

        // Fallback to Hytale's PermissionHolder – covers OP, console, etc.
        return sender.hasPermission(permissionNode);
    }

    /**
     * Grant a permission node to a player using the active backend.
     * Returns true if it appears to succeed.
     */
    public boolean grantPermission(@Nonnull UUID uuid, @Nonnull String node) {
        if (permissionsBackend == null) {
            return false;
        }
        return permissionsBackend.grantPermission(uuid, node);
    }

    public boolean revokePermission(@Nonnull UUID uuid, @Nonnull String node) {
        if (permissionsBackend == null) {
            return false;
        }
        try {
            return permissionsBackend.revokePermission(uuid, node);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to revoke permission '%s' for %s via %s",
                            node, uuid, permissionsBackend.getBackendName());
            return false;
        }
    }

    // ----------------------------------------------------------------
    // Economy (unchanged)
    // ----------------------------------------------------------------

    public boolean isPrimaryEconomyAvailable() {
        if (!Settings.get().isEconomySystemEnabled()) {
            return false;
        }
        try {
            return EconomySystemSupport.isAvailable();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public boolean isVaultAvailable() {
        try {
            return VaultUnlockedSupport.isAvailable();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public boolean isEliteEconomyAvailable() {
        try {
            return EliteEconomySupport.isAvailable();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public boolean isEcoTaleAvailable() {
        try {
            return EcoTaleSupport.isAvailable();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public boolean isHyEssentialsXAvailable() {
        try {
            return HyEssentialsXSupport.isAvailable();
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    public EconomyMode getEconomyMode() {
        return economyMode;
    }

    public boolean isUsingPhysicalCoins() {
        return economyMode == EconomyMode.PHYSICAL
                && coinsAndMarketsBackend != null
                && coinsAndMarketsBackend.isAvailable();
    }

    public boolean hasAnyEconomy() {
        if (!Settings.get().isEconomySystemEnabled()) return false;
        return isUsingPhysicalCoins() || hasAnyLedgerEconomy();
    }

    private void logEconomyStatusIfNeeded() {
        if (loggedEconomyStatus) {
            return;
        }

        boolean primary = isPrimaryEconomyAvailable();
        boolean hyex    = isHyEssentialsXAvailable();
        boolean ecoTale = isEcoTaleAvailable();
        boolean vault   = isVaultAvailable();
        boolean elite   = isEliteEconomyAvailable();

        if (primary) {
            if (hyex || ecoTale || vault || elite) {
                LOGGER.at(Level.INFO).log(
                        "[MysticNameTags] EconomySystem (com.economy) detected as primary economy. " +
                                "HyEssentialsX: " + hyex +
                                ", EcoTale: " + ecoTale +
                                ", VaultUnlocked: " + vault +
                                ", EliteEssentials: " + elite + " (fallbacks)."
                );
            } else {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] EconomySystem (com.economy) detected – tag purchasing enabled.");
            }
            loggedEconomyStatus = true;
            return;
        }

        if (hyex) {
            if (ecoTale || vault || elite) {
                LOGGER.at(Level.INFO).log(
                        "[MysticNameTags] HyEssentialsX detected as primary economy backend. " +
                                "EcoTale: " + ecoTale +
                                ", VaultUnlocked: " + vault +
                                ", EliteEssentials: " + elite + " (fallbacks)."
                );
            } else {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] HyEssentialsX detected – tag purchasing enabled.");
            }
            loggedEconomyStatus = true;
            return;
        }

        if (ecoTale) {
            if (vault || elite) {
                LOGGER.at(Level.INFO).log(
                        "[MysticNameTags] EcoTale detected as primary economy backend. " +
                                "VaultUnlocked: " + vault +
                                ", EliteEssentials: " + elite + " (fallbacks)."
                );
            } else {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] EcoTale detected – tag purchasing enabled.");
            }
            loggedEconomyStatus = true;
            return;
        }

        if (vault) {
            if (elite) {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] VaultUnlocked + EliteEssentials detected – using VaultUnlocked as primary economy backend.");
            } else {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] VaultUnlocked detected – tag purchasing enabled.");
            }
            loggedEconomyStatus = true;
            return;
        }

        if (elite) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] EliteEssentials EconomyAPI detected – tag purchasing enabled.");
            loggedEconomyStatus = true;
        }
    }


    public boolean withdraw(@Nonnull PlayerRef playerRef, @Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) return true;

        logEconomyStatusIfNeeded();

        if (isUsingPhysicalCoins() && coinsAndMarketsBackend != null) {
            long copper = Math.round(amount);
            return coinsAndMarketsBackend.withdrawCopper(playerRef, copper).success();
        }

        return withdraw(uuid, amount);
    }

    public boolean hasBalance(@Nonnull PlayerRef playerRef, @Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) return true;

        logEconomyStatusIfNeeded();

        if (isUsingPhysicalCoins() && coinsAndMarketsBackend != null) {
            long copper = Math.round(amount);
            return coinsAndMarketsBackend.hasCopper(playerRef, copper);
        }

        return hasBalance(uuid, amount);
    }

    public double getBalance(@Nonnull PlayerRef playerRef, @Nonnull UUID uuid) {
        logEconomyStatusIfNeeded();

        if (isUsingPhysicalCoins() && coinsAndMarketsBackend != null) {
            return (double) coinsAndMarketsBackend.getPhysicalWealthCopper(playerRef);
        }

        return getBalance(uuid);
    }

    public boolean withdraw(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) return true;
        logEconomyStatusIfNeeded();

        // Ledger chain only (physical is handled in the PlayerRef overload)
        if (ledgerBackends != null) {
            for (EconomyBackend backend : ledgerBackends) {
                if (!backend.isAvailable()) continue;
                if (backend.has(uuid, amount) && backend.withdraw(uuid, amount)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean hasBalance(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) return true;
        logEconomyStatusIfNeeded();

        // Ledger chain only (physical is handled in the PlayerRef overload)
        if (ledgerBackends != null) {
            for (EconomyBackend backend : ledgerBackends) {
                if (!backend.isAvailable()) continue;
                if (backend.has(uuid, amount)) {
                    return true;
                }
            }
        }

        return false;
    }

    public double getBalance(@Nonnull UUID uuid) {
        logEconomyStatusIfNeeded();

        // Ledger chain only (physical is handled in the PlayerRef overload)
        if (ledgerBackends != null) {
            for (EconomyBackend backend : ledgerBackends) {
                if (!backend.isAvailable()) continue;
                double bal = backend.getBalance(uuid);
                if (bal > 0.0D) {
                    return bal;
                }
            }
        }

        return 0.0D;
    }

    @Nonnull
    private UUID getUuidFromPlayerRef(@Nonnull PlayerRef ref) {
        return ref.getUuid();
    }

    public void setEndlessNameplateSystem(@Nullable EndlessLevelingNameplateSystem sys) {
        this.endlessNameplateSystem = sys;
    }

    public void invalidateEndlessLevelingNameplate(@Nonnull UUID uuid) {
        EndlessLevelingNameplateSystem sys = this.endlessNameplateSystem;
        if (sys != null) sys.invalidate(uuid);
    }

    public @Nullable Integer getPlaytimeMinutes(UUID uuid) {
        if (playtimeProvider == null) return null;
        try { return playtimeProvider.getPlaytimeMinutes(uuid); }
        catch (Throwable t) { return null; }
    }

    public void setPlaytimeProvider(@Nullable StatProvider statProvider) {
        this.playtimeProvider = (PlaytimeProvider) statProvider;
    }

    // ----------------------------------------------------------------
    // Stats / challenge integration
    // ----------------------------------------------------------------

    /**
     * Expose stat values for tag requirements.
     *
     * @return current stat value or null if backend missing / error / zero.
     */
    @Nullable
    public Integer getStatValue(@Nonnull UUID uuid, @Nonnull String key) {
        if (statProvider == null) {
            return null;
        }

        try {
            return statProvider.getStatValue(uuid, key);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] StatProvider threw while resolving stat '%s' for %s",
                            key, uuid);
            return null;
        }
    }

    // ----------------------------------------------------------------
    // Item requirement integration
    // ----------------------------------------------------------------

    /**
     * Check if the player has ALL items needed for a tag requirement.
     *
     * If no handler is configured, we treat item requirements as disabled
     * (always satisfied) so tags aren't hard-locked by missing integrations.
     */
    public boolean hasItems(@Nonnull PlayerRef playerRef,
                            @Nonnull java.util.List<com.mystichorizons.mysticnametags.tags.TagDefinition.ItemRequirement> requirements) {

        ItemRequirementHandler handler = this.itemRequirementHandler;
        if (handler == null) {
            // Item requirements disabled -> behave as if satisfied
            return true;
        }

        try {
            return handler.hasItems(playerRef, requirements);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] ItemRequirementHandler threw while checking items for %s",
                            playerRef.getUuid());
            // Fail-safe: if the integration blows up, treat as satisfied so we don't brick tags.
            return true;
        }
    }

    /**
     * Consume ALL required items from the player's inventory.
     *
     * If no handler is configured, this is treated as a no-op success.
     */
    public boolean consumeItems(@Nonnull PlayerRef playerRef,
                                @Nonnull java.util.List<com.mystichorizons.mysticnametags.tags.TagDefinition.ItemRequirement> requirements) {

        ItemRequirementHandler handler = this.itemRequirementHandler;
        if (handler == null) {
            // Item requirements disabled -> no-op success
            return true;
        }

        try {
            return handler.consumeItems(playerRef, requirements);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] ItemRequirementHandler threw while consuming items for %s",
                            playerRef.getUuid());
            return false;
        }
    }

    public void setItemRequirementHandler(@Nullable ItemRequirementHandler handler) {
        this.itemRequirementHandler = handler;
    }
}
