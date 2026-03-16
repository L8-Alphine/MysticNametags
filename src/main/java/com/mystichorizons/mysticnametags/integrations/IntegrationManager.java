package com.mystichorizons.mysticnametags.integrations;

import com.airijko.endlessleveling.data.PlayerData;
import com.airijko.endlessleveling.managers.PlayerDataManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.economy.*;
import com.mystichorizons.mysticnametags.integrations.ecoquests.EcoQuestsCompat;
import com.mystichorizons.mysticnametags.integrations.endlessleveling.EndlessLevelingNameplateSystem;
import com.mystichorizons.mysticnametags.integrations.endlessleveling.EndlessLevelingStatBridge;
import com.mystichorizons.mysticnametags.integrations.permissions.*;
import com.mystichorizons.mysticnametags.placeholders.HelpchPlaceholderHook;
import com.mystichorizons.mysticnametags.playtime.PlaytimeService;
import org.zuxaw.plugin.api.RPGLevelingAPI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;


public class IntegrationManager {

    private static final String ECON_PLUGIN_NAME = "MysticNameTags";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable private EndlessLevelingNameplateSystem endlessNameplateSystem;
    @Nonnull
    private final PlaytimeService playtimeService;

    @Nonnull
    private PlaytimeProvider playtimeProvider;
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

    @Nonnull
    public PlaytimeProvider getPlaytimeProvider() {
        return playtimeProvider;
    }

    @Nonnull
    public PlaytimeService getPlaytimeService() {
        return playtimeService;
    }

    private java.util.List<EconomyBackend> ledgerBackends = java.util.List.of();

    // Permission backends
    private LuckPermsSupport luckPermsSupport;
    private HyperPermsSupport hyperPermsSupport;
    private PermissionSupport permissionsBackend; // active backend (LuckPerms / PermissionsPlus / Hyperperms / Native)

    // Prefix backend
    private PrefixesPlusSupport prefixesPlusSupport;

    private enum PermissionBackendType {
        LUCKPERMS,
        HYPERPERMS,
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
        setupPlaytimeBackend();
        setupPermissionBackends();
        setupPrefixBackends();
        setupEconomyBackends();
        setupItemRequirementHandler();
    }

    public IntegrationManager(@Nonnull PlaytimeService playtimeService) {
        this.playtimeService = playtimeService;

        // Default to our internal playtime provider
        this.playtimeProvider = new InternalPlaytimeProvider(playtimeService);
    }

    // ----------------------------------------------------------------
    // Playtime backend selection
    // ----------------------------------------------------------------
    private void setupPlaytimeBackend() {
        String mode = Settings.get().getPlaytimeProviderMode(); // AUTO / INTERNAL / ZIB_PLAYTIME / NONE

        // Default from constructor is InternalPlaytimeProvider(playtimeService)

        // NONE = disable playtime requirements (always pass)
        if ("NONE".equals(mode)) {
            this.playtimeProvider = uuid -> Long.MAX_VALUE;
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Playtime requirements disabled (playtimeProvider=NONE).");
            return;
        }

        // AUTO or ZIB_PLAYTIME: try Zid's Playtime first
        if ("ZIB_PLAYTIME".equals(mode) || "AUTO".equals(mode)) {
            try {
                // Only attempt if the class is present
                Class.forName("com.zib.playtime.api.PlaytimeAPI");

                com.zib.playtime.api.PlaytimeAPI api = com.zib.playtime.api.PlaytimeAPI.get();
                if (api != null) {
                    this.playtimeProvider = new ZibPlaytimeProvider(api);
                    LOGGER.at(Level.INFO)
                            .log("[MysticNameTags] Using Zid's Playtime mod as playtime provider.");
                    return;
                } else {
                    LOGGER.at(Level.WARNING)
                            .log("[MysticNameTags] Zid Playtime API returned null; falling back to internal playtime provider.");
                }
            } catch (ClassNotFoundException e) {
                if ("ZIB_PLAYTIME".equals(mode)) {
                    LOGGER.at(Level.WARNING)
                            .log("[MysticNameTags] playtimeProvider=ZIB_PLAYTIME but Zid Playtime mod not found; falling back to internal provider.");
                }
                // if AUTO, we silently fall back
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING)
                        .withCause(t)
                        .log("[MysticNameTags] Failed to initialize Zid Playtime integration; falling back to internal provider.");
            }
        }

        // INTERNAL or AUTO (fallback): keep the default internal provider
        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] Using internal stat-based playtime provider (mode=" + mode + ").");
    }

    // ----------------------------------------------------------------
    // Backend detection
    // ----------------------------------------------------------------

    private void setupPermissionBackends() {
        // 1) LuckPerms
        try {
            this.luckPermsSupport = new LuckPermsSupport();
            if (luckPermsSupport.isAvailable()) {
                this.permissionsBackend = luckPermsSupport;
                this.activePermissionBackend = PermissionBackendType.LUCKPERMS;
                LOGGER.at(Level.INFO).log("[MysticNameTags] Using LuckPerms for permissions.");
                return;
            } else {
                this.luckPermsSupport = null;
            }
        } catch (NoClassDefFoundError e) {
            this.luckPermsSupport = null;
            LOGGER.at(Level.INFO).log("[MysticNameTags] LuckPerms API not found – skipping LuckPerms integration.");
        } catch (Throwable t) {
            this.luckPermsSupport = null;
            LOGGER.at(Level.WARNING).withCause(t).log("[MysticNameTags] Error probing LuckPerms – skipping LuckPerms integration.");
        }

        // 2) HyperPerms
        try {
            this.hyperPermsSupport = new HyperPermsSupport();
            if (hyperPermsSupport.isAvailable()) {
                this.permissionsBackend = hyperPermsSupport;
                this.activePermissionBackend = PermissionBackendType.HYPERPERMS;
                LOGGER.at(Level.INFO).log("[MysticNameTags] Using HyperPerms for permissions.");
                return;
            } else {
                this.hyperPermsSupport = null;
            }
        } catch (NoClassDefFoundError e) {
            this.hyperPermsSupport = null;
            LOGGER.at(Level.INFO).log("[MysticNameTags] HyperPerms API not found – skipping HyperPerms integration.");
        } catch (Throwable t) {
            this.hyperPermsSupport = null;
            LOGGER.at(Level.WARNING).withCause(t).log("[MysticNameTags] Error probing HyperPerms – skipping HyperPerms integration.");
        }

        // 3) PermissionsPlus
        try {
            PermissionsPlusSupport permsPlus = new PermissionsPlusSupport();
            if (permsPlus.isAvailable()) {
                this.permissionsBackend = permsPlus;
                this.activePermissionBackend = PermissionBackendType.PERMISSIONS_PLUS;
                LOGGER.at(Level.INFO).log("[MysticNameTags] Using PermissionsPlus / PermissionsModule for permissions.");
                return;
            }
        } catch (NoClassDefFoundError e) {
            LOGGER.at(Level.INFO).log("[MysticNameTags] PermissionsPlus API not found – skipping PermissionsPlus integration.");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[MysticNameTags] Error probing PermissionsPlus – skipping PermissionsPlus integration.");
        }

        // 4) Native
        this.permissionsBackend = new NativePermissionsSupport();
        this.activePermissionBackend = PermissionBackendType.NATIVE;
        LOGGER.at(Level.INFO).log("[MysticNameTags] No external permission plugin found – using native Hytale permissions.");
    }

    private void setupPrefixBackends() {
        // PrefixesPlus first
        try {
            this.prefixesPlusSupport = new PrefixesPlusSupport();
            if (prefixesPlusSupport.isAvailable()) {
                LOGGER.at(Level.INFO).log("[MysticNameTags] Detected PrefixesPlus – using it for rank prefixes.");
                return;
            } else {
                this.prefixesPlusSupport = null;
            }
        } catch (NoClassDefFoundError e) {
            this.prefixesPlusSupport = null;
            LOGGER.at(Level.INFO).log("[MysticNameTags] PrefixesPlus API not found – skipping PrefixesPlus prefix provider.");
        } catch (Throwable t) {
            this.prefixesPlusSupport = null;
            LOGGER.at(Level.WARNING).withCause(t).log("[MysticNameTags] Error probing PrefixesPlus – skipping PrefixesPlus prefix provider.");
        }

        // HyperPerms (prefix via ChatAPI)
        try {
            if (hyperPermsSupport != null && hyperPermsSupport.isAvailable()) {
                LOGGER.at(Level.INFO).log("[MysticNameTags] Using HyperPerms ChatAPI for rank prefixes.");
                return;
            }
        } catch (Throwable ignored) {}

        // LuckPerms meta fallback (existing behavior)
        if (luckPermsSupport != null && luckPermsSupport.isAvailable()) {
            LOGGER.at(Level.INFO).log("[MysticNameTags] Using LuckPerms meta data for rank prefixes.");
        } else {
            LOGGER.at(Level.INFO).log("[MysticNameTags] No prefix provider detected – nameplates will only show tags + player names.");
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
            if (p != null && !p.isEmpty()) return p;
        }

        if (hyperPermsSupport != null && hyperPermsSupport.isAvailable()) {
            String p = hyperPermsSupport.getPrefix(uuid);
            if (p != null && !p.isEmpty()) return p;
        }

        if (luckPermsSupport != null && luckPermsSupport.isAvailable()) {
            String p = luckPermsSupport.getPrefix(uuid);
            if (p != null && !p.isEmpty()) return p;
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

    public boolean isHyperPermsAvailable() {
        return hyperPermsSupport != null && hyperPermsSupport.isAvailable();
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
        try {
            long minutes = playtimeProvider.getPlaytimeMinutes(uuid);
            if (minutes <= 0L) {
                return 0;
            }
            if (minutes > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) minutes;
        } catch (Throwable t) {
            return null;
        }
    }

    public void setPlaytimeProvider(@Nullable PlaytimeProvider playtimeProvider) {
        this.playtimeProvider = playtimeProvider;
    }

    // ----------------------------------------------------------------
    // Stats / challenge integration
    // ----------------------------------------------------------------

    /**
     * Generic stat lookup for tag requirements and UI.
     *
     * Supports:
     *  - Internal PlayerStatManager-backed keys (e.g. "custom.damage_dealt")
     *  - EndlessLeveling-backed keys (keys starting with "endlessleveling.")
     */
    @Nullable
    public Integer getStatValue(@Nonnull UUID uuid, @Nonnull String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        String trimmed = key.trim();

        // 1) EndlessLeveling bridge (prefix-based)
        if (trimmed.startsWith("endlessleveling.")) {
            try {
                Integer val = EndlessLevelingStatBridge.getStatValue(uuid, trimmed);
                if (val != null) {
                    return val;
                }
            } catch (Throwable t) {
                LOGGER.at(Level.FINE)
                        .withCause(t)
                        .log("[MysticNameTags] EndlessLeveling stat bridge error for %s (key=%s)", uuid, trimmed);
                // Fall through to internal stats if EndlessLeveling is missing or fails
            }
        }

        // 2) Primary StatProvider (internal PlayerStatManager)
        StatProvider provider = this.statProvider;
        if (provider == null) {
            return null;
        }

        try {
            if (key.contains("*")) {
                return provider.getStatValuePattern(uuid, key);
            }
            return provider.getStatValue(uuid, key);
        } catch (Throwable ignored) {
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

    @Nullable
    public String resolvePlaceholder(@Nonnull PlayerRef playerRef,
                                     @Nonnull String placeholder) {
        String input = placeholder.trim();
        if (input.isEmpty()) return null;

        String value = null;

        try {
            // 1) WiFlow first
            value = WiFlowPlaceholderSupport.applySingle(playerRef, input);
        } catch (Throwable ignored) {}

        try {
            if (value == null || value.equals(input)) {
                // 2) HelpChat
                value = HelpchPlaceholderHook.resolve(playerRef, input);
            }
        } catch (Throwable ignored) {}

        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    @Nullable
    public String resolvePlaceholderRequirement(@Nonnull PlayerRef playerRef,
                                                @Nonnull String placeholder,
                                                @Nonnull String operator,
                                                @Nonnull String value) {
        String input = placeholder.trim();
        if (input.isEmpty()) {
            return null;
        }

        // 1) Try resolving exactly as given first.
        String direct = resolvePlaceholder(playerRef, input);
        if (direct != null && !direct.equals(input)) {
            return direct;
        }

        String arg = value.trim();
        if (arg.isEmpty()) {
            return direct;
        }

        // 2) If the placeholder looks like %something%, try expanding it
        // into a parameterized placeholder form using the value.
        //
        // Example:
        //   %luckperms_check_permission%
        // + mysticnametags.tag.thief
        // -> %luckperms_check_permission_mysticnametags.tag.thief%
        //
        // This allows generic "template-style" placeholders.
        if (input.startsWith("%") && input.endsWith("%") && input.length() > 2) {
            String inner = input.substring(1, input.length() - 1);

            // underscore expansion
            String expandedUnderscore = "%" + inner + "_" + arg + "%";
            String resolvedUnderscore = resolvePlaceholder(playerRef, expandedUnderscore);
            if (resolvedUnderscore != null && !resolvedUnderscore.equals(expandedUnderscore)) {
                return resolvedUnderscore;
            }

            // colon expansion
            String expandedColon = "%" + inner + ":" + arg + "%";
            String resolvedColon = resolvePlaceholder(playerRef, expandedColon);
            if (resolvedColon != null && !resolvedColon.equals(expandedColon)) {
                return resolvedColon;
            }

            // pipe expansion
            String expandedPipe = "%" + inner + "|" + arg + "%";
            String resolvedPipe = resolvePlaceholder(playerRef, expandedPipe);
            if (resolvedPipe != null && !resolvedPipe.equals(expandedPipe)) {
                return resolvedPipe;
            }
        }

        // 3) Fall back to whatever direct resolution gave us.
        return direct;
    }

    // Dashboard UI Getters

    public boolean isPrefixesPlusAvailable() {
        return prefixesPlusSupport != null && prefixesPlusSupport.isAvailable();
    }

    public boolean isAnyPrefixProviderAvailable() {
        return isPrefixesPlusAvailable()
                || isHyperPermsAvailable()
                || isLuckPermsAvailable();
    }

    @Nonnull
    public String getActivePermissionBackendName() {
        return switch (activePermissionBackend) {
            case LUCKPERMS -> "LuckPerms";
            case HYPERPERMS -> "HyperPerms";
            case PERMISSIONS_PLUS -> "PermissionsPlus";
            case NATIVE -> "Native Hytale Permissions";
        };
    }

    @Nonnull
    public String getPlaytimeProviderName() {
        PlaytimeProvider provider = this.playtimeProvider;
        if (provider == null) {
            return "None";
        }
        return provider.getClass().getSimpleName();
    }

    // TODO: REMOVE METHODS - Was moved to a better integration call
    public boolean isItemRequirementHandlerAvailable() {
        return itemRequirementHandler != null;
    }

    public boolean isStatProviderAvailable() {
        return statProvider != null;
    }

    public boolean isEndlessLevelingNameplateAttached() {
        return endlessNameplateSystem != null;
    }

    public boolean isCoinsAndMarketsAvailable() {
        return coinsAndMarketsBackend != null && coinsAndMarketsBackend.isAvailable();
    }

    @Nonnull
    public java.util.List<String> getAvailableLedgerBackendNames() {
        if (ledgerBackends == null || ledgerBackends.isEmpty()) {
            return java.util.List.of();
        }

        java.util.List<String> names = new java.util.ArrayList<>();
        for (EconomyBackend backend : ledgerBackends) {
            try {
                names.add(backend.getClass().getSimpleName());
            } catch (Throwable ignored) {
                names.add("UnknownBackend");
            }
        }
        return java.util.Collections.unmodifiableList(names);
    }

    @Nullable
    private PlayerData getEndlessPlayerData(@Nonnull UUID uuid) {
        try {
            Class.forName("com.airijko.endlessleveling.EndlessLeveling");

            com.airijko.endlessleveling.EndlessLeveling plugin =
                    com.airijko.endlessleveling.EndlessLeveling.getInstance();
            if (plugin == null) {
                return null;
            }

            PlayerDataManager pdm = plugin.getPlayerDataManager();
            if (pdm == null) {
                return null;
            }

            return pdm.get(uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nonnull
    private String prettifyId(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = input.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isEmpty()) {
            return "";
        }

        String[] parts = normalized.split("\\s+");
        StringBuilder out = new StringBuilder(normalized.length());

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }

            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase());
            }

            if (i + 1 < parts.length) {
                out.append(' ');
            }
        }

        return out.toString();
    }

    @Nonnull
    public String getEndlessLevel(@Nonnull UUID uuid) {
        if (!Settings.get().isEndlessLevelingNameplatesEnabled()) {
            return "";
        }

        PlayerData data = getEndlessPlayerData(uuid);
        if (data == null) {
            return "";
        }

        int level = Math.max(1, data.getLevel());
        return String.valueOf(level);
    }

    @Nonnull
    public String getEndlessPrestige(@Nonnull UUID uuid) {
        Settings s = Settings.get();
        if (!s.isEndlessLevelingNameplatesEnabled() || !s.isEndlessPrestigeDisplayEnabled()) {
            return "";
        }

        PlayerData data = getEndlessPlayerData(uuid);
        if (data == null) {
            return "";
        }

        int prestige = Math.max(0, data.getPrestigeLevel());
        if (prestige <= 0) {
            return "";
        }

        return s.getEndlessPrestigePrefix() + prestige;
    }

    @Nonnull
    public String getEndlessRace(@Nonnull UUID uuid) {
        Settings s = Settings.get();
        if (!s.isEndlessLevelingNameplatesEnabled() || !s.isEndlessRaceDisplayEnabled()) {
            return "";
        }

        PlayerData data = getEndlessPlayerData(uuid);
        if (data == null) {
            return "";
        }

        String race = data.getRaceId();
        if (race == null || race.isBlank()) {
            race = PlayerData.DEFAULT_RACE_ID;
        }

        return race == null ? "" : race.trim();
    }

    @Nonnull
    public String getEndlessPrimaryClass(@Nonnull UUID uuid) {
        Settings s = Settings.get();
        if (!s.isEndlessLevelingNameplatesEnabled() || !s.isEndlessPrimaryClassDisplayEnabled()) {
            return "";
        }

        PlayerData data = getEndlessPlayerData(uuid);
        if (data == null) {
            return "";
        }

        return prettifyId(data.getPrimaryClassId());
    }

    @Nonnull
    public String getEndlessSecondaryClass(@Nonnull UUID uuid) {
        Settings s = Settings.get();
        if (!s.isEndlessLevelingNameplatesEnabled() || !s.isEndlessSecondaryClassDisplayEnabled()) {
            return "";
        }

        PlayerData data = getEndlessPlayerData(uuid);
        if (data == null) {
            return "";
        }

        return prettifyId(data.getSecondaryClassId());
    }

    @Nonnull
    public String getRpgLevel(@Nonnull PlayerRef playerRef) {
        if (!Settings.get().isRpgLevelingNameplatesEnabled()) {
            return "";
        }

        try {
            RPGLevelingAPI api = RPGLevelingAPI.get();
            if (api == null) return "";

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return "";

            Store<EntityStore> store = ref.getStore();
            RPGLevelingAPI.PlayerLevelInfo info = api.getPlayerLevelInfo(playerRef, store);
            if (info == null || info.getLevel() <= 0) {
                return "";
            }

            return String.valueOf(info.getLevel());
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Nonnull
    public String getEcoQuestsRank(@Nonnull UUID uuid) {
        try {
            if (!EcoQuestsCompat.isAvailable()) {
                return "";
            }

            String rank = EcoQuestsCompat.getRankId(uuid);
            if (rank == null || rank.isBlank()) {
                return "E";
            }
            return rank.trim();
        } catch (Throwable ignored) {
            return "E";
        }
    }
}
