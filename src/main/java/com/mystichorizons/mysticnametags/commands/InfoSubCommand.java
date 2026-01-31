package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.UpdateChecker;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

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

    private void sendColored(CommandContext context, String text) {
        context.sendMessage(ColorFormatter.toMessage(text));
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();

        if (plugin == null) {
            sendColored(context, "&cMysticNameTags plugin instance not available.");
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

        boolean lp          = integrations.isLuckPermsAvailable();
        boolean permsPlus   = integrations.isPermissionsPlusActive();
        boolean econPrimary = integrations.isPrimaryEconomyAvailable();
        boolean econVault   = integrations.isVaultAvailable();
        boolean econElite   = integrations.isEliteEconomyAvailable();

        StringBuilder integrationLine = new StringBuilder("&7Integrations: ");

        // Permissions stack
        if (lp) {
            integrationLine.append("&aLuckPerms");
        } else {
            integrationLine.append("&cLuckPerms (none)");
        }

        if (permsPlus) {
            integrationLine.append("&7 + &bPermissionsPlus");
        }

        integrationLine.append("&7 | Economy: ");

        // Economy backends
        if (econPrimary) {
            integrationLine.append("&aEconomySystem");
            if (econVault || econElite) {
                integrationLine.append("&7 (fallback: ");
                if (econVault) integrationLine.append("&bVaultUnlocked&7 ");
                if (econElite) integrationLine.append("&dEliteEssentials&7 ");
                integrationLine.append("&7)");
            }
        } else if (econVault || econElite) {
            if (econVault) integrationLine.append("&bVaultUnlocked ");
            if (econElite) integrationLine.append("&dEliteEssentials ");
        } else {
            integrationLine.append("&cnone");
        }

        // Update status
        UpdateChecker checker = plugin.getUpdateChecker();
        String updateLine = "&7Update status: &8Unknown";

        if (checker != null && checker.hasVersionInfo()) {
            String latest = checker.getLatestVersion();
            if (checker.isCurrentAheadOfLatest()) {
                updateLine = "&7Update status: &aRunning " + version +
                        " &7(ahead of CurseForge: &b" + latest + "&7)";
            } else if (checker.isUpdateAvailable()) {
                updateLine = "&7Update status: &eUpdate available &7→ &b" + latest;
            } else {
                updateLine = "&7Update status: &aUp to date (&b" + version + "&a)";
            }
        }

        // Pretty output
        sendColored(context, ""); // blank line
        sendColored(context, "&7&m------------------------------");
        sendColored(context, "&b" + name + " &7Plugin Info");
        sendColored(context, "&7Name: &f" + name);
        sendColored(context, "&7Version: &f" + version);
        sendColored(context, "&7Author: &f" + author);
        sendColored(context, "&7Status: &aRunning");
        sendColored(context, integrationLine.toString());
        sendColored(context, updateLine);
        sendColored(context, "&7&m------------------------------");
    }
}
