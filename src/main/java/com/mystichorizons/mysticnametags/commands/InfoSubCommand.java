package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

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
        String version = plugin.getResolvedVersion();

        String author = "Unknown";
        if (manifest != null && manifest.getAuthors() != null && !manifest.getAuthors().isEmpty()) {
            author = manifest.getAuthors().get(0).getName();
        }

        // Integrations
        TagManager tagManager = TagManager.get();
        IntegrationManager integrations = tagManager.getIntegrations();

        boolean lp = integrations.isLuckPermsAvailable();
        boolean econPrimary = integrations.isPrimaryEconomyAvailable();
        boolean econVault = integrations.isVaultAvailable();
        boolean econElite = integrations.isEliteEconomyAvailable();

        StringBuilder integrationLine = new StringBuilder("Integrations: ");
        integrationLine.append(lp ? "LuckPerms" : "LuckPerms (none)");
        integrationLine.append(" | Economy: ");

        if (econPrimary) {
            integrationLine.append("EconomySystem");
            if (econVault || econElite) {
                integrationLine.append(" (fallback: ");
                if (econVault) integrationLine.append("VaultUnlocked ");
                if (econElite) integrationLine.append("EliteEssentials ");
                integrationLine.append(')');
            }
        } else if (econVault || econElite) {
            if (econVault) integrationLine.append("VaultUnlocked ");
            if (econElite) integrationLine.append("EliteEssentials ");
        } else {
            integrationLine.append("none");
        }

        // Update status
        UpdateChecker checker = plugin.getUpdateChecker();
        String updateLine = "Update status: Unknown";
        if (checker != null && checker.hasVersionInfo()) {
            String latest = checker.getLatestVersion();
            if (checker.isCurrentAheadOfLatest()) {
                updateLine = "Update status: Running " + version +
                        " (ahead of CurseForge: " + latest + ")";
            } else if (checker.isUpdateAvailable()) {
                updateLine = "Update status: Update available -> " + latest;
            } else {
                updateLine = "Update status: Up to date (" + version + ")";
            }
        }

        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== " + name + " Info ==="));
        context.sendMessage(Message.raw("Name: " + name));
        context.sendMessage(Message.raw("Version: " + version));
        context.sendMessage(Message.raw("Author: " + author));
        context.sendMessage(Message.raw("Status: Running"));
        context.sendMessage(Message.raw(integrationLine.toString()));
        context.sendMessage(Message.raw(updateLine));
        context.sendMessage(Message.raw("===================="));
    }
}
