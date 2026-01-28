package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import javax.annotation.Nonnull;

/**
 * /tags info - Show plugin information
 */
public class InfoSubCommand extends CommandBase {

    public InfoSubCommand() {
        super("info", "Show plugin information");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();

        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== MysticNameTags Info ==="));
        context.sendMessage(Message.raw("Name: MysticNameTags"));
        context.sendMessage(Message.raw("Version: 1.0.0"));
        context.sendMessage(Message.raw("Author: Alphine"));
        context.sendMessage(Message.raw("Status: " + (plugin != null ? "Running" : "Not loaded")));
        context.sendMessage(Message.raw("===================="));
    }
}