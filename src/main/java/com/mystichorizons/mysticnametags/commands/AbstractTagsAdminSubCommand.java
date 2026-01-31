package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Base for /tagsadmin subcommands.
 *
 * NOTE: extends AbstractPlayerCommand, so these are player-only for now.
 * If you want console support later, we can swap this to AbstractCommand.
 */
public abstract class AbstractTagsAdminSubCommand extends AbstractPlayerCommand {

    /**
     * Root admin permission. Having this grants access to all /tagsadmin subcommands.
     *
     * Per-subcommand permissions are derived automatically as:
     *   mysticnametags.admin.<subcommand-name>
     * e.g. mysticnametags.admin.open, mysticnametags.admin.givetag, ...
     */
    public static final String PERM_TAGS_ADMIN_ROOT = "mysticnametags.admin";

    protected AbstractTagsAdminSubCommand(@Nonnull String name, @Nonnull String description) {
        super(name, description);
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    protected Message colored(String text) {
        return ColorFormatter.toMessage(text);
    }

    protected IntegrationManager getIntegrations() {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        return plugin != null ? plugin.getIntegrations() : null;
    }

    /**
     * Returns the per-subcommand permission, e.g. "mysticnametags.admin.givetag".
     */
    @Nonnull
    protected String getSubcommandPermission() {
        // getName() comes from AbstractAsyncCommand / AbstractCommandBase
        String subName = this.getName();
        if (subName == null) {
            subName = "";
        }
        subName = subName.toLowerCase(Locale.ROOT);
        return PERM_TAGS_ADMIN_ROOT + "." + subName;
    }

    /**
     * Checks if the sender has either:
     *  - the root permission: mysticnametags.admin
     *  - the subcommand-specific permission: mysticnametags.admin.<subcommand>
     */
    protected boolean hasAdminPermission(@Nonnull CommandContext context) {
        IntegrationManager integrations = getIntegrations();
        if (integrations == null) {
            return false;
        }

        CommandSender sender = context.sender();

        // Root + sub-node
        String rootPerm = PERM_TAGS_ADMIN_ROOT;
        String subPerm  = getSubcommandPermission();

        return integrations.hasPermission(sender, rootPerm)
                || integrations.hasPermission(sender, subPerm);
    }

    /**
     * Resolve an online player by username using the Universe registry.
     * Uses PARTIAL matching (like vanilla tab-complete).
     */
    @Nullable
    protected PlayerRef findOnlinePlayer(String inputName) {
        if (inputName == null || inputName.isEmpty()) {
            return null;
        }
        return Universe.get().getPlayerByUsername(inputName, NameMatching.STARTS_WITH_IGNORE_CASE);
    }
}
