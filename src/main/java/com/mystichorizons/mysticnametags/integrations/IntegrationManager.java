package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.tags.TagManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.logging.Level;

public class IntegrationManager {

    private static final String ECON_PLUGIN_NAME = "MysticNameTags";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ---- LuckPerms ----
    private LuckPerms luckPerms;
    private boolean luckPermsAvailable;

    // ---- Economy status logging ----
    private boolean loggedEconomyStatus = false;

    public void init() {
        setupLuckPerms();
    }

    // ----------------------------------------------------------------
    // LuckPerms
    // ----------------------------------------------------------------

    private void setupLuckPerms() {
        try {
            this.luckPerms = LuckPermsProvider.get();
            this.luckPermsAvailable = true;

            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Hooked into LuckPerms " + luckPerms.getPlatform());

            registerLuckPermsListeners();
        } catch (Throwable t) {
            this.luckPermsAvailable = false;
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] LuckPerms not detected! Permissions and rank prefixes disabled.");
        }
    }

    public boolean isLuckPermsAvailable() {
        return luckPermsAvailable;
    }

    @Nullable
    public String getLuckPermsPrefix(@Nonnull UUID uuid) {
        if (!luckPermsAvailable) return null;

        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return null;

        QueryOptions options = luckPerms.getContextManager()
                .getQueryOptions(user)
                .orElseGet(() -> luckPerms.getContextManager().getStaticQueryOptions());

        String prefix = user.getCachedData().getMetaData(options).getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return prefix;
        }

        Group group = luckPerms.getGroupManager().getGroup(user.getPrimaryGroup());
        if (group != null) {
            return group.getCachedData().getMetaData(options).getPrefix();
        }

        return null;
    }

    public boolean hasPermissionWithLuckPerms(@Nonnull UUID uuid, @Nonnull String node) {
        if (!luckPermsAvailable) return false;

        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return false;

        return user.getCachedData()
                .getPermissionData()
                .checkPermission(node)
                .asBoolean();
    }

    private void registerLuckPermsListeners() {
        luckPerms.getEventBus().subscribe(
                MysticNameTagsPlugin.getInstance(),
                UserDataRecalculateEvent.class,
                event -> {
                    UUID uuid = event.getUser().getUniqueId();
                    try {
                        handleRankChange(uuid);
                    } catch (Throwable t) {
                        LOGGER.at(Level.WARNING).withCause(t)
                                .log("[MysticNameTags] Failed to refresh nameplate for " + uuid);
                    }
                }
        );
    }

    private void handleRankChange(@Nonnull UUID uuid) {
        TagManager manager = TagManager.get();

        PlayerRef ref = manager.getOnlinePlayer(uuid);
        World world   = manager.getOnlineWorld(uuid);

        if (ref != null && world != null) {
            manager.refreshNameplate(ref, world);
        }
    }

    /**
     * Grant a permission node to a player using LuckPerms.
     * Returns true if it appears to succeed, false otherwise.
     */
    public boolean grantPermission(@Nonnull UUID uuid, @Nonnull String node) {
        if (!luckPermsAvailable) {
            return false;
        }

        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            if (user == null) {
                // Try to load if not already loaded
                user = luckPerms.getUserManager().loadUser(uuid).join();
            }
            if (user == null) {
                return false;
            }

            user.data().add(Node.builder(node).build());
            luckPerms.getUserManager().saveUser(user);
            return true;
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to grant permission '" + node + "' to " + uuid);
            return false;
        }
    }

    // ----------------------------------------------------------------
    // Economy
    // ----------------------------------------------------------------

    /**
     * Primary backend: EconomySystem (com.economy.api.EconomyAPI) if present
     * and enabled in settings.
     * Secondary backend: VaultUnlocked (if present).
     * Tertiary backend: EliteEssentials EconomyAPI (if present and enabled).
     */
    public boolean isPrimaryEconomyAvailable() {
        // Allow server owners to hard-disable EconomySystem usage
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

    /**
     * Is there ANY economy available at all?
     */
    public boolean hasAnyEconomy() {
        return isPrimaryEconomyAvailable() || isVaultAvailable() || isEliteEconomyAvailable();
    }

    private void logEconomyStatusIfNeeded() {
        if (loggedEconomyStatus) {
            return;
        }

        boolean primary = isPrimaryEconomyAvailable();
        boolean vault   = isVaultAvailable();
        boolean elite   = isEliteEconomyAvailable();

        if (primary) {
            if (vault || elite) {
                LOGGER.at(Level.INFO).log(
                        "[MysticNameTags] EconomySystem (com.economy) detected as primary economy. " +
                                "VaultUnlocked: " + vault + ", EliteEssentials: " + elite + " (fallbacks)."
                );
            } else {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] EconomySystem (com.economy) detected – tag purchasing enabled.");
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
        // If none are detected yet, stay quiet and re-check on the next call.
    }

    public boolean withdraw(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) {
            return true;
        }

        logEconomyStatusIfNeeded();

        boolean useCoins = Settings.get().isEconomySystemEnabled()
                && Settings.get().isUseCoinSystem();

        // 1) EconomySystem (com.economy.api.EconomyAPI)
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
            // fall through to other backends if the call fails for some reason
        }

        // 2) VaultUnlocked
        if (isVaultAvailable()) {
            return VaultUnlockedSupport.withdraw(ECON_PLUGIN_NAME, uuid, amount);
        }

        // 3) EliteEssentials EconomyAPI
        if (isEliteEconomyAvailable()) {
            return EliteEconomySupport.withdraw(uuid, amount);
        }

        // 4) No economy
        return false;
    }

    public boolean hasBalance(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) {
            return true;
        }

        logEconomyStatusIfNeeded();

        boolean useCoins = Settings.get().isEconomySystemEnabled()
                && Settings.get().isUseCoinSystem();

        // 1) EconomySystem
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
            // if it errors we still allow fallback checks
        }

        // 2) VaultUnlocked
        if (isVaultAvailable()) {
            return VaultUnlockedSupport.getBalance(ECON_PLUGIN_NAME, uuid) >= amount;
        }

        // 3) EliteEssentials
        if (isEliteEconomyAvailable()) {
            return EliteEconomySupport.has(uuid, amount);
        }

        // 4) No economy
        return false;
    }

    public double getBalance(@Nonnull UUID uuid) {
        logEconomyStatusIfNeeded();

        boolean useCoins = Settings.get().isEconomySystemEnabled()
                && Settings.get().isUseCoinSystem();

        // 1) EconomySystem
        if (isPrimaryEconomyAvailable()) {
            if (useCoins) {
                int coins = EconomySystemSupport.getCoins(uuid);
                return coins; // represent coin stack as a double
            } else {
                double bal = EconomySystemSupport.getBalance(uuid);
                return bal;
            }
        }

        // 2) VaultUnlocked
        if (isVaultAvailable()) {
            return VaultUnlockedSupport.getBalance(ECON_PLUGIN_NAME, uuid);
        }

        // 3) EliteEssentials
        if (isEliteEconomyAvailable()) {
            return EliteEconomySupport.getBalance(uuid);
        }

        // 4) No economy
        return 0.0D;
    }

    // ----------------------------------------------------------------
    // Unified permission entry
    // ----------------------------------------------------------------

    /**
     * Permission check for *players* (used in tag logic).
     * Uses LuckPerms when available; if LP missing, falls back to
     * "fail-open" so tags remain usable via other means (crates, etc.).
     */
    public boolean hasPermission(@Nonnull PlayerRef playerRef,
                                 @Nonnull String permissionNode) {

        UUID uuid = getUuidFromPlayerRef(playerRef);

        if (uuid != null && hasPermissionWithLuckPerms(uuid, permissionNode)) {
            return true;
        }

        // If LP is missing, fail-open for tag usage
        return !luckPermsAvailable;
    }

    /**
     * Permission check for *command senders*.
     * - If LuckPerms is present, prefers LP via UUID
     * - Always falls back to sender.hasPermission(...) so Hytale's permission
     *   system (including console) continues to work.
     */
    public boolean hasPermission(@Nonnull CommandSender sender,
                                 @Nonnull String permissionNode) {

        if (sender == null) {
            return false;
        }

        // If LP isn't present, delegate entirely to native perms
        if (!luckPermsAvailable) {
            return sender.hasPermission(permissionNode);
        }

        try {
            UUID uuid = sender.getUuid();
            if (uuid != null && hasPermissionWithLuckPerms(uuid, permissionNode)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall through to native perms
        }

        // Fallback to Hytale's PermissionHolder – covers OP, console, etc.
        return sender.hasPermission(permissionNode);
    }

    // ----------------------------------------------------------------

    @NonNullDecl
    private UUID getUuidFromPlayerRef(@Nonnull PlayerRef ref) {
        return ref.getUuid();
    }
}
