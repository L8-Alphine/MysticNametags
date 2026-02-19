package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

import javax.annotation.Nonnull;
import java.util.Map;

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
        LanguageManager lang = LanguageManager.get();
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();

        if (plugin == null) {
            sendColored(context, lang.tr("cmd.info.not_loaded"));
            return;
        }

        var manifest = plugin.getManifest();

        String name    = plugin.getName();
        String version = plugin.getResolvedVersion();

        String author = "Unknown";
        if (manifest != null && manifest.getAuthors() != null && !manifest.getAuthors().isEmpty()) {
            author = manifest.getAuthors().get(0).getName();
        }

        TagManager tagManager = TagManager.get();
        IntegrationManager integrations = tagManager.getIntegrations();

        boolean lp          = integrations.isLuckPermsAvailable();
        boolean permsPlus   = integrations.isPermissionsPlusActive();
        boolean econPrimary = integrations.isPrimaryEconomyAvailable();
        boolean econVault   = integrations.isVaultAvailable();
        boolean econElite   = integrations.isEliteEconomyAvailable();

        StringBuilder integrationLine = new StringBuilder(lang.tr("cmd.info.integrations_prefix"));

        if (lp) integrationLine.append(lang.tr("cmd.info.luckperms_yes"));
        else    integrationLine.append(lang.tr("cmd.info.luckperms_no"));

        if (permsPlus) integrationLine.append(lang.tr("cmd.info.permissionsplus_yes"));

        integrationLine.append(lang.tr("cmd.info.economy_prefix"));

        if (econPrimary) {
            integrationLine.append(lang.tr("cmd.info.economy_primary"));
            if (econVault || econElite) {
                integrationLine.append(lang.tr("cmd.info.economy_fallback_prefix"));
                boolean first = true;
                if (econVault) { integrationLine.append(lang.tr("cmd.info.economy_vault")); first = false; }
                if (econElite) {
                    if (!first) integrationLine.append(lang.tr("cmd.info.economy_fallback_sep"));
                    integrationLine.append(lang.tr("cmd.info.economy_elite"));
                }
                integrationLine.append(lang.tr("cmd.info.economy_fallback_suffix"));
            }
        } else if (econVault || econElite) {
            boolean first = true;
            if (econVault) { integrationLine.append(lang.tr("cmd.info.economy_vault")); first = false; }
            if (econElite) {
                if (!first) integrationLine.append(lang.tr("cmd.info.economy_plus_sep"));
                integrationLine.append(lang.tr("cmd.info.economy_elite"));
            }
        } else {
            integrationLine.append(lang.tr("cmd.info.economy_none"));
        }

        UpdateChecker checker = plugin.getUpdateChecker();
        String updateLine = lang.tr("cmd.info.update_unknown");

        if (checker != null && checker.hasVersionInfo()) {
            String latest = checker.getLatestVersion();
            if (checker.isCurrentAheadOfLatest()) {
                updateLine = lang.tr("cmd.info.update_ahead", Map.of(
                        "current", version,
                        "latest", latest
                ));
            } else if (checker.isUpdateAvailable()) {
                updateLine = lang.tr("cmd.info.update_available", Map.of("latest", latest));
            } else {
                updateLine = lang.tr("cmd.info.update_ok", Map.of("current", version));
            }
        }

        sendColored(context, "");
        sendColored(context, lang.tr("cmd.info.separator"));
        sendColored(context, lang.tr("cmd.info.title", Map.of("name", name)));
        sendColored(context, lang.tr("cmd.info.name", Map.of("name", name)));
        sendColored(context, lang.tr("cmd.info.version", Map.of("version", version)));
        sendColored(context, lang.tr("cmd.info.author", Map.of("author", author)));
        sendColored(context, lang.tr("cmd.info.status_running"));
        sendColored(context, integrationLine.toString());
        sendColored(context, updateLine);
        sendColored(context, lang.tr("cmd.info.separator"));
    }
}
