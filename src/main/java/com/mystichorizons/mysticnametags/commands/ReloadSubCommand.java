package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;

/**
 * /tags reload - Reload plugin configuration + tags.json
 */
public class ReloadSubCommand extends CommandBase {

    private static final String PERMISSION = "mysticnametags.reload";

    public ReloadSubCommand() {
        super("reload", "Reload MysticNameTags configuration and tags");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    private boolean hasReloadPermission(@Nonnull CommandContext context) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) return false;

        IntegrationManager integrations = plugin.getIntegrations();
        CommandSender sender = context.sender();

        return integrations.hasPermission(sender, PERMISSION);
    }

    private Message colored(String text) {
        return ColorFormatter.toMessage(text);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasReloadPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.reload.no_permission")));
            return;
        }

        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            context.sender().sendMessage(colored(lang.tr("cmd.reload.not_loaded")));
            return;
        }

        context.sender().sendMessage(colored(lang.tr("cmd.reload.start")));

        try {
            plugin.reloadAll();
            context.sender().sendMessage(colored(lang.tr("cmd.reload.success")));
        } catch (Throwable t) {
            t.printStackTrace();
            context.sender().sendMessage(colored(lang.tr("cmd.reload.failed")));
        }
    }
}
