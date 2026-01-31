package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Base for /tagsadmin subcommands.
 *
 * Extends AbstractCommand so subcommands can be executed
 * by BOTH players and console.
 */
public abstract class AbstractTagsAdminSubCommand extends AbstractCommand {

    public static final String PERM_TAGS_ADMIN_ROOT = "mysticnametags.admin";

    protected AbstractTagsAdminSubCommand(@Nonnull String name, @Nonnull String description) {
        super(name, description);
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        // We handle permissions manually via IntegrationManager.
        return false;
    }

    protected Message colored(String text) {
        return ColorFormatter.toMessage(text);
    }

    @Nullable
    protected IntegrationManager getIntegrations() {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        return plugin != null ? plugin.getIntegrations() : null;
    }

    @Nonnull
    protected String getSubcommandPermission() {
        String subName = this.getName();
        if (subName == null) {
            subName = "";
        }
        subName = subName.toLowerCase(Locale.ROOT);
        return PERM_TAGS_ADMIN_ROOT + "." + subName;
    }

    protected boolean hasAdminPermission(@Nonnull CommandContext context) {
        IntegrationManager integrations = getIntegrations();
        if (integrations == null) {
            return false;
        }

        CommandSender sender = context.sender();

        String rootPerm = PERM_TAGS_ADMIN_ROOT;
        String subPerm  = getSubcommandPermission();

        return integrations.hasPermission(sender, rootPerm)
                || integrations.hasPermission(sender, subPerm);
    }

    @Nullable
    protected PlayerRef findOnlinePlayer(String inputName) {
        if (inputName == null || inputName.isEmpty()) {
            return null;
        }
        return Universe.get().getPlayerByUsername(inputName, NameMatching.STARTS_WITH_IGNORE_CASE);
    }

    /**
     * This is the method that AbstractCommand expects.
     * We delegate to our own void method and then immediately
     * complete the future.
     */
    @Override
    public CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        executeAdmin(context);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Subclasses implement this with their actual logic.
     */
    protected abstract void executeAdmin(@Nonnull CommandContext context);
}
