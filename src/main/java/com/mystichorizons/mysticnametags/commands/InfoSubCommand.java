package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import javax.annotation.Nonnull;

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

        if (plugin == null) {
            context.sendMessage(Message.raw("MysticNameTags plugin instance not available."));
            return;
        }

        var manifest = plugin.getManifest();

        String name    = plugin.getName();
        String version = (manifest != null && manifest.getVersion() != null)
                ? manifest.getVersion().toString()
                : "unknown";

        String author = "Unknown";
        if (manifest != null && manifest.getAuthors() != null && !manifest.getAuthors().isEmpty()) {
            // Depending on the exact API, adjust `.getName()` if needed
            author = manifest.getAuthors().get(0).getName();
        }

        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== " + name + " Info ==="));
        context.sendMessage(Message.raw("Name: " + name));
        context.sendMessage(Message.raw("Version: " + version));
        context.sendMessage(Message.raw("Author: " + author));
        context.sendMessage(Message.raw("Status: Running"));
        context.sendMessage(Message.raw("===================="));
    }
}
