package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.tags.TagManager;
import net.cfh.vault.VaultUnlockedServicesManager;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.milkbowl.vault2.chat.ChatUnlocked;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import net.milkbowl.vault2.permission.PermissionUnlocked;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Level;

public class IntegrationManager {

    private static final String ECON_PLUGIN_NAME = "MysticNameTags";
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private LuckPerms luckPerms;
    private Economy economy;
    private PermissionUnlocked permission;
    private ChatUnlocked chat;

    private boolean luckPermsAvailable;
    private boolean vaultAvailable;

    public void init() {
        setupLuckPerms();
        setupVaultUnlocked();
    }

    private void setupLuckPerms() {
        try {
            this.luckPerms = LuckPermsProvider.get();
            this.luckPermsAvailable = true;

            LOGGER.at(Level.INFO).log("[MysticNameTags] Hooked into LuckPerms " +
                    luckPerms.getPlatform());

            // Register LP event listeners once we know it's present
            registerLuckPermsListeners();
        } catch (Throwable t) {
            this.luckPermsAvailable = false;
            LOGGER.at(Level.INFO).withCause(t)
                    .log("[MysticNameTags] LuckPerms not detected - rank formatting + extra permissions limited.");
        }
    }

    private void setupVaultUnlocked() {
        try {
            VaultUnlockedServicesManager services = VaultUnlockedServicesManager.get();
            this.economy = services.economyObj();
            this.permission = services.permissionObj();
            this.chat = services.chatObj();
            this.vaultAvailable = (economy != null || permission != null || chat != null);

            if (vaultAvailable) {
                LOGGER.at(Level.INFO).log("[MysticNameTags] Hooked into VaultUnlocked (Hytale)");
            } else {
                LOGGER.at(Level.INFO).log("[MysticNameTags] VaultUnlocked detected but no services registered.");
            }
        } catch (Throwable t) {
            this.vaultAvailable = false;
            LOGGER.at(Level.INFO).withCause(t)
                    .log("[MysticNameTags] VaultUnlocked not detected - economic tag purchasing disabled.");
        }
    }

    public boolean isLuckPermsAvailable() {
        return luckPermsAvailable;
    }

    public boolean isVaultAvailable() {
        return vaultAvailable && economy != null;
    }

    // ---------------- LuckPerms helpers ----------------

    @Nullable
    public String getLuckPermsPrefix(@Nonnull UUID uuid) {
        if (!luckPermsAvailable) {
            return null;
        }

        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return null;
        }

        QueryOptions options = luckPerms.getContextManager()
                .getQueryOptions(user)
                .orElseGet(() -> luckPerms.getContextManager().getStaticQueryOptions());

        String prefix = user.getCachedData().getMetaData(options).getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return prefix;
        }

        // Fall back to primary group tag/prefix if present
        String primaryGroup = user.getPrimaryGroup();
        Group group = luckPerms.getGroupManager().getGroup(primaryGroup);
        if (group != null) {
            String groupPrefix = group.getCachedData().getMetaData(options).getPrefix();
            if (groupPrefix != null && !groupPrefix.isEmpty()) {
                return groupPrefix;
            }
        }

        return null;
    }

    public boolean hasPermissionWithLuckPerms(@Nonnull UUID uuid, @Nonnull String node) {
        if (!luckPermsAvailable) {
            return false;
        }

        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) {
            return false;
        }

        return user.getCachedData().getPermissionData().checkPermission(node).asBoolean();
    }

    private void registerLuckPermsListeners() {
        if (!luckPermsAvailable || luckPerms == null) {
            return;
        }

        // Fires whenever a user's cached data (including prefixes / groups) is recalculated
        luckPerms.getEventBus().subscribe(
                MysticNameTagsPlugin.getInstance(), // your plugin instance
                UserDataRecalculateEvent.class,
                event -> {
                    UUID uuid = event.getUser().getUniqueId();
                    try {
                        handleRankChange(uuid);
                    } catch (Throwable t) {
                        LOGGER.at(Level.WARNING).withCause(t)
                                .log("[MysticNameTags] Failed to handle LuckPerms rank change for " + uuid);
                    }
                }
        );
    }

    /**
     * Called when LuckPerms notifies us that a user's data (groups/prefix/meta) changed.
     * We rebuild their nameplate if they're online.
     */
    private void handleRankChange(@Nonnull UUID uuid) {
        TagManager tagManager = TagManager.get();

        PlayerRef ref = tagManager.getOnlinePlayer(uuid);
        World world   = tagManager.getOnlineWorld(uuid);

        if (ref == null || world == null) {
            return; // offline or not tracked
        }

        // This is deduplicated + world-thread safe inside TagManager
        tagManager.refreshNameplate(ref, world);
    }

    // ---------------- VaultUnlocked helpers ----------------

    public boolean withdraw(@Nonnull UUID accountId, double amount) {
        if (!isVaultAvailable() || amount <= 0.0D) {
            return false;
        }

        try {
            BigDecimal value = BigDecimal.valueOf(amount);
            EconomyResponse response = economy.withdraw(ECON_PLUGIN_NAME, accountId, value);
            return response != null && response.transactionSuccess();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Economy withdraw failed for " + accountId);
            return false;
        }
    }

    public boolean hasBalance(@Nonnull UUID accountId, double amount) {
        if (!isVaultAvailable() || amount <= 0.0D) {
            return false;
        }

        try {
            BigDecimal bal = economy.getBalance(ECON_PLUGIN_NAME, accountId);
            if (bal == null) return false;
            return bal.compareTo(BigDecimal.valueOf(amount)) >= 0;
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Economy getBalance failed for " + accountId);
            return false;
        }
    }

    public double getBalance(@Nonnull UUID accountId) {
        if (!isVaultAvailable()) {
            return 0.0D;
        }

        try {
            BigDecimal bal = economy.getBalance(ECON_PLUGIN_NAME, accountId);
            return bal == null ? 0.0D : bal.doubleValue();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Economy getBalance failed for " + accountId);
            return 0.0D;
        }
    }

    public boolean hasPermission(@Nonnull PlayerRef playerRef, @Nonnull String permissionNode) {
        UUID uuid = getUuidFromPlayerRef(playerRef);
        if (uuid != null && hasPermissionWithLuckPerms(uuid, permissionNode)) {
            return true;
        }

        // If LuckPerms isn't present, we can't enforce plugin-level perms here.
        if (!luckPermsAvailable) {
            return true;
        }

        return false;
    }

    // ---------------- Utility ----------------

    @NonNullDecl
    private UUID getUuidFromPlayerRef(@Nonnull PlayerRef ref) {
        return ref.getUuid();
    }
}
