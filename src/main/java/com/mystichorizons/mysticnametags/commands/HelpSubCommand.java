package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * /tags help - Show available commands
 */
public class HelpSubCommand extends CommandBase {

    public HelpSubCommand() {
        super("help", "Show available commands");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== MysticNameTags Commands ==="));
        context.sendMessage(Message.raw("/tags help - Show this help message"));
        context.sendMessage(Message.raw("/tags info - Show plugin information"));
        context.sendMessage(Message.raw("/tags reload - Reload configuration"));
        context.sendMessage(Message.raw("/tags ui - Open the dashboard UI"));
        context.sendMessage(Message.raw("/tags tags  - Open the tag selection UI"));
        context.sendMessage(Message.raw("========================"));
    }
}