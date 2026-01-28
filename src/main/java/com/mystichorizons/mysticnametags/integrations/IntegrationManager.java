package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.tags.TagManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
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

    // ----------------------------------------------------------------
    // Economy (VaultUnlocked + EliteEssentials – OPTIONAL)
    // ----------------------------------------------------------------

    /**
     * Primary backend: VaultUnlocked (if present).
     * Secondary backend: EliteEssentials EconomyAPI (if present and enabled).
     */
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
        return isVaultAvailable() || isEliteEconomyAvailable();
    }

    private void logEconomyStatusIfNeeded() {
        if (loggedEconomyStatus) {
            return;
        }

        boolean vault = isVaultAvailable();
        boolean elite = isEliteEconomyAvailable();

        if (vault) {
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
        // If neither is detected yet, we stay quiet and will re-check on the next call.
    }

    public boolean withdraw(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) {
            return true;
        }

        logEconomyStatusIfNeeded();

        // 1) Prefer VaultUnlocked
        if (isVaultAvailable()) {
            return VaultUnlockedSupport.withdraw(ECON_PLUGIN_NAME, uuid, amount);
        }

        // 2) Fallback to EliteEssentials EconomyAPI
        if (isEliteEconomyAvailable()) {
            return EliteEconomySupport.withdraw(uuid, amount);
        }

        // 3) No economy
        return false;
    }

    public boolean hasBalance(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) {
            return true;
        }

        logEconomyStatusIfNeeded();

        // 1) Prefer VaultUnlocked
        if (isVaultAvailable()) {
            return VaultUnlockedSupport.getBalance(ECON_PLUGIN_NAME, uuid) >= amount;
        }

        // 2) Fallback to EliteEssentials
        if (isEliteEconomyAvailable()) {
            return EliteEconomySupport.has(uuid, amount);
        }

        // 3) No economy
        return false;
    }

    public double getBalance(@Nonnull UUID uuid) {
        logEconomyStatusIfNeeded();

        // 1) Prefer VaultUnlocked
        if (isVaultAvailable()) {
            return VaultUnlockedSupport.getBalance(ECON_PLUGIN_NAME, uuid);
        }

        // 2) Fallback to EliteEssentials
        if (isEliteEconomyAvailable()) {
            return EliteEconomySupport.getBalance(uuid);
        }

        // 3) No economy
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
