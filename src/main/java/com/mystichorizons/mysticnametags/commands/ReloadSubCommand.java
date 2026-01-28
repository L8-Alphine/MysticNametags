package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import javax.annotation.Nonnull;

/**
 * /tags reload - Reload plugin configuration
 */
public class ReloadSubCommand extends CommandBase {

    public ReloadSubCommand() {
        super("reload", "Reload plugin configuration");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();

        if (plugin == null) {
            context.sendMessage(Message.raw("Error: Plugin not loaded"));
            return;
        }

        context.sendMessage(Message.raw("Reloading MysticNameTags..."));

        // TODO: Add your reload logic here
        // Example: Reload config files, refresh caches, etc.

        context.sendMessage(Message.raw("MysticNameTags reloaded successfully!"));
    }
}