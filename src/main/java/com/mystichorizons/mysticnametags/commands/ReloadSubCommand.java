package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;

/**
 * /tags reload - Reload plugin configuration + tags.json
 */
public class ReloadSubCommand extends CommandBase {

    private static final String PERMISSION = "mysticnametags.reload";

    public ReloadSubCommand() {
        super("reload", "Reload MysticNameTags configuration and tags");
        // We handle permission manually via IntegrationManager
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        // No auto-generated permission; we explicitly check PERMISSION
        return false;
    }

    private boolean hasReloadPermission(@Nonnull CommandContext context) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            return false;
        }

        IntegrationManager integrations = plugin.getIntegrations();
        CommandSender sender = context.sender();

        // Use IntegrationManager, which prefers LuckPerms but falls back to Hytale perms
        return integrations.hasPermission(sender, PERMISSION);
    }

    private Message colored(String text) {
        // Let ColorFormatter interpret & and hex codes into a styled Message.
        return ColorFormatter.toMessage(text);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!hasReloadPermission(context)) {
            context.sender().sendMessage(
                    colored("&cYou do not have permission to use this command.")
            );
            return;
        }

        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            context.sender().sendMessage(
                    colored("&cMysticNameTags is not loaded.")
            );
            return;
        }

        context.sender().sendMessage(
                colored("&7[&bMysticNameTags&7] &fReloading configuration and tags...")
        );

        try {
            // Single entrypoint: settings + tags + schedulers + integrations
            plugin.reloadAll();

            context.sender().sendMessage(
                    colored("&7[&bMysticNameTags&7] &aReload complete!")
            );
        } catch (Throwable t) {
            t.printStackTrace();
            context.sender().sendMessage(
                    colored("&7[&bMysticNameTags&7] &cReload failed. Check console.")
            );
        }
    }
}
