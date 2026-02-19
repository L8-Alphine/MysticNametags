package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.mystichorizons.mysticnametags.commands.admin.*;

/**
 * Admin command root for MysticNameTags.
 *
 * Usage (intended):
 *   /tagsadmin open <player>
 *   /tagsadmin givetag <player> <tagId>
 *   /tagsadmin removetag <player> <tagId>
 *   /tagsadmin reset <player>
 */
public class TagsAdminCommand extends AbstractCommandCollection {

    public TagsAdminCommand() {
        super("tagsadmin", "Admin tools for MysticNameTags tags");
        this.addAliases("tagadmin", "mntagadmin");

        this.addSubCommand(new TagsAdminOpenSubCommand());
        this.addSubCommand(new TagsAdminGiveTagSubCommand());
        this.addSubCommand(new TagsAdminRemoveTagSubCommand());
        this.addSubCommand(new TagsAdminResetSubCommand());
        this.addSubCommand(new TagsAdminDebugStorageSubCommand());
        this.addSubCommand(new TagsAdminStorageSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        // We’ll handle permissions manually via IntegrationManager in each subcommand
        return false;
    }
}
