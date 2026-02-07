package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;


public class IntegrationManager {

    private static final String ECON_PLUGIN_NAME = "MysticNameTags";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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

    private PermissionBackendType activePermissionBackend = PermissionBackendType.NATIVE;

    // Economy flags
    private boolean loggedEconomyStatus = false;

    public void init() {
        setupPermissionBackends();
        setupPrefixBackends();
        // economy is unchanged
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

        if (uuid != null && permissionsBackend != null) {
            if (permissionsBackend.hasPermission(uuid, permissionNode)) {
                return true;
            }
        }

        // If LP is missing entirely fail-open for tag usage
        // (other systems may unlock tags without perms)
        if (!isLuckPermsAvailable()) {
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

    public boolean hasAnyEconomy() {
        return isPrimaryEconomyAvailable() || isVaultAvailable() || isEliteEconomyAvailable() || isEcoTaleAvailable();
    }

    private void logEconomyStatusIfNeeded() {
        if (loggedEconomyStatus) {
            return;
        }

        boolean primary = isPrimaryEconomyAvailable();
        boolean ecoTale = isEcoTaleAvailable();
        boolean vault   = isVaultAvailable();
        boolean elite   = isEliteEconomyAvailable();

        if (primary) {
            if (ecoTale || vault || elite) {
                LOGGER.at(Level.INFO).log(
                        "[MysticNameTags] EconomySystem (com.economy) detected as primary economy. " +
                                "EcoTale: " + ecoTale +
                                ", VaultUnlocked: " + vault +
                                ", EliteEssentials: " + elite + " (fallbacks)."
                );
            } else {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] EconomySystem (com.economy) detected – tag purchasing enabled.");
            }
            loggedEconomyStatus = true;
        } else if (ecoTale) {
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
        } else if (vault) {
            if (elite) {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] VaultUnlocked + EliteEssentials detected – using VaultUnlocked as primary economy backend.");
            } else {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] VaultUnlocked detected – tag purchasing enabled.");
            }
            loggedEconomyStatus = true;
        } else if (elite) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] EliteEssentials EconomyAPI detected – tag purchasing enabled.");
            loggedEconomyStatus = true;
        }
    }

    public boolean withdraw(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) {
            return true;
        }

        logEconomyStatusIfNeeded();

        boolean useCoins = Settings.get().isEconomySystemEnabled()
                && Settings.get().isUseCoinSystem();

        if (isPrimaryEconomyAvailable()) {
            if (useCoins) {
                int coinAmount = (int) Math.round(amount);
                if (EconomySystemSupport.withdrawCoins(uuid, coinAmount)) {
                    return true;
                }
            } else {
                if (EconomySystemSupport.withdraw(uuid, amount)) {
                    return true;
                }
            }
        }

        // EcoTale
        if (isEcoTaleAvailable()) {
            return EcoTaleSupport.withdraw(uuid, amount);
        }

        if (isVaultAvailable()) {
            return VaultUnlockedSupport.withdraw(ECON_PLUGIN_NAME, uuid, amount);
        }

        if (isEliteEconomyAvailable()) {
            return EliteEconomySupport.withdraw(uuid, amount);
        }

        return false;
    }

    public boolean hasBalance(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) {
            return true;
        }

        logEconomyStatusIfNeeded();

        boolean useCoins = Settings.get().isEconomySystemEnabled()
                && Settings.get().isUseCoinSystem();

        if (isPrimaryEconomyAvailable()) {
            if (useCoins) {
                int coinAmount = (int) Math.round(amount);
                if (EconomySystemSupport.hasCoins(uuid, coinAmount)) {
                    return true;
                }
            } else {
                if (EconomySystemSupport.has(uuid, amount)) {
                    return true;
                }
            }
        }

        // EcoTale
        if (isEcoTaleAvailable()) {
            return EcoTaleSupport.getBalance(uuid) >= amount;
        }

        if (isVaultAvailable()) {
            return VaultUnlockedSupport.getBalance(ECON_PLUGIN_NAME, uuid) >= amount;
        }

        if (isEliteEconomyAvailable()) {
            return EliteEconomySupport.has(uuid, amount);
        }

        return false;
    }

    public double getBalance(@Nonnull UUID uuid) {
        logEconomyStatusIfNeeded();

        boolean useCoins = Settings.get().isEconomySystemEnabled()
                && Settings.get().isUseCoinSystem();

        if (isPrimaryEconomyAvailable()) {
            if (useCoins) {
                int coins = EconomySystemSupport.getCoins(uuid);
                return coins;
            } else {
                return EconomySystemSupport.getBalance(uuid);
            }
        }

        // EcoTale
        if (isEcoTaleAvailable()) {
            return EcoTaleSupport.getBalance(uuid);
        }

        if (isVaultAvailable()) {
            return VaultUnlockedSupport.getBalance(ECON_PLUGIN_NAME, uuid);
        }

        if (isEliteEconomyAvailable()) {
            return EliteEconomySupport.getBalance(uuid);
        }

        return 0.0D;
    }

    @Nonnull
    private UUID getUuidFromPlayerRef(@Nonnull PlayerRef ref) {
        return ref.getUuid();
    }
}
