package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Main command for MysticNameTags plugin.
 *
 * Usage:
 * - /tags help - Show available commands
 * - /tags info - Show plugin information
 * - /tags reload - Reload plugin configuration
 * - /tags ui - Open the plugin dashboard
 */
public class MysticNameTagsPluginCommand extends AbstractCommandCollection {

    public MysticNameTagsPluginCommand() {
        super("tags", "MysticNameTags plugin commands");

        // Add subcommands
        this.addSubCommand(new HelpSubCommand());
        this.addSubCommand(new InfoSubCommand());
        this.addSubCommand(new ReloadSubCommand());
        this.addSubCommand(new UISubCommand());
        this.addSubCommand(new TagsSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false; // No permission required for base command
    }
}