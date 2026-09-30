package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.network.NetworkSyncService;
import com.mystichorizons.mysticnametags.network.RedisManager;
import com.mystichorizons.mysticnametags.integrations.mmoskilltree.MMOSkillTreeCompat;
import com.mystichorizons.mysticnametags.integrations.rpgleveling.RPGLevelingCompat;
import com.mystichorizons.mysticnametags.tags.StorageBackend;
import com.mystichorizons.mysticnametags.tags.TagConfigValidator;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.JdbcDrivers;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TagsAdminDoctorSubCommand extends AbstractTagsAdminSubCommand {

    private static final int MAX_FINDINGS = 8;

    public TagsAdminDoctorSubCommand() {
        super("doctor", "Run MysticNameTags health checks");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin doctor"
            ))));
            return;
        }

        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            context.sender().sendMessage(colored("&cMysticNameTags plugin instance is not available.&r"));
            return;
        }

        Settings settings = Settings.get();
        IntegrationManager integrations = plugin.getIntegrations();
        TagManager tags = TagManager.get();
        File dataFolder = plugin.getDataDirectory().toFile();

        TagConfigValidator.Report tagReport =
                TagConfigValidator.validateDefault(settings, integrations);

        int storageErrors = inspectStorage(settings, dataFolder);
        List<String> integrationWarnings = inspectIntegrationWarnings(settings, integrations);
        int totalErrors = tagReport.count(TagConfigValidator.Severity.ERROR) + storageErrors;
        int totalWarnings = tagReport.count(TagConfigValidator.Severity.WARNING) + integrationWarnings.size();

        String statusColor = totalErrors > 0 ? "&c" : (totalWarnings > 0 ? "&e" : "&a");
        String statusText = totalErrors > 0 ? "ISSUES FOUND" : (totalWarnings > 0 ? "WARNINGS" : "HEALTHY");

        StringBuilder sb = new StringBuilder();
        sb.append("&bMysticNameTags Doctor&r\n");
        sb.append("&7Status: ").append(statusColor).append(statusText).append("&r\n");
        sb.append("&7Version: &f").append(plugin.getResolvedVersion()).append("&r\n");
        sb.append("&7Data folder: &f").append(dataFolder.getAbsolutePath()).append("&r\n");
        sb.append("&8------------------------------&r\n");

        appendStorageSummary(sb, settings, dataFolder);
        appendIntegrationSummary(sb, settings, integrations, integrationWarnings);
        appendTagSummary(sb, tags, tagReport);

        if (!tagReport.getFindings().isEmpty()) {
            sb.append("&8------------------------------&r\n");
            sb.append("&eTag config findings")
                    .append(" &7(showing ")
                    .append(Math.min(MAX_FINDINGS, tagReport.getFindings().size()))
                    .append("/")
                    .append(tagReport.getFindings().size())
                    .append(")&r\n");

            for (TagConfigValidator.Finding finding : tagReport.getFindingsUpTo(MAX_FINDINGS)) {
                sb.append(colorFor(finding.getSeverity()))
                        .append(symbolFor(finding.getSeverity()))
                        .append(" &7")
                        .append(finding.getLocation())
                        .append("&f: ")
                        .append(finding.getMessage())
                        .append("&r\n");
            }

            int remaining = tagReport.getFindings().size() - MAX_FINDINGS;
            if (remaining > 0) {
                sb.append("&7...and &f").append(remaining).append("&7 more. Check server logs or fix the first issues and rerun doctor.&r\n");
            }
        }

        sb.append("&8------------------------------&r\n");
        sb.append("&7Summary: &f")
                .append(totalErrors)
                .append("&7 errors, &f")
                .append(totalWarnings)
                .append("&7 warnings.&r");

        context.sender().sendMessage(colored(sb.toString()));
    }

    private static void appendStorageSummary(@Nonnull StringBuilder sb,
                                             @Nonnull Settings settings,
                                             @Nonnull File dataFolder) {
        StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());

        sb.append("&eStorage&r\n");
        sb.append("&7Backend: &f").append(backend.name()).append("&r\n");

        switch (backend) {
            case FILE -> {
                File playerDataFolder = new File(dataFolder, "playerdata");
                sb.append("&7Playerdata folder: ")
                        .append(playerDataFolder.isDirectory() ? "&aOK" : "&cMISSING")
                        .append("&r\n");
                sb.append("&7Writable: ")
                        .append(playerDataFolder.canWrite() ? "&aYES" : "&cNO")
                        .append("&r\n");
            }
            case SQLITE -> {
                File sqliteFile = new File(dataFolder, settings.getSqliteFile());
                File sqliteParent = sqliteFile.getAbsoluteFile().getParentFile();
                sb.append("&7SQLite file: &f").append(sqliteFile.getAbsolutePath()).append("&r\n");
                sb.append("&7Parent writable: ")
                        .append(sqliteParent != null && sqliteParent.canWrite() ? "&aYES" : "&cNO")
                        .append("&r\n");
            }
            case MYSQL, MARIADB -> {
                String jdbcUrl = backend.mySqlJdbcUrl(
                        settings.getMysqlHost(), settings.getMysqlPort(), settings.getMysqlDatabase());
                sb.append("&7")
                        .append(backend == StorageBackend.MARIADB ? "MariaDB" : "MySQL")
                        .append(": &f")
                        .append(settings.getMysqlHost())
                        .append(":")
                        .append(settings.getMysqlPort())
                        .append("/")
                        .append(settings.getMysqlDatabase())
                        .append("&r\n");
                sb.append("&7User: &f").append(settings.getMysqlUser()).append("&r\n");
                sb.append("&7JDBC driver: ")
                        .append(JdbcDrivers.isAvailable(jdbcUrl) ? "&a" : "&c")
                        .append(JdbcDrivers.describeDriver(jdbcUrl))
                        .append("&r\n");
            }
            case REDIS -> {
                sb.append("&7Redis: &f")
                        .append(settings.getRedisHost())
                        .append(":")
                        .append(settings.getRedisPort())
                        .append("/")
                        .append(settings.getRedisDatabase())
                        .append("&r\n");
                sb.append("&7Key prefix: &f").append(settings.getRedisKeyPrefix()).append("&r\n");

                RedisManager redis = RedisManager.get();
                sb.append("&7Connected: ")
                        .append(redis != null && redis.isHealthy() ? "&aYES" : "&cNO")
                        .append("&r\n");
            }
        }

        appendNetworkSyncSummary(sb, settings);
    }

    private static void appendNetworkSyncSummary(@Nonnull StringBuilder sb,
                                                 @Nonnull Settings settings) {
        if (!settings.isRedisSyncEnabled()) {
            return;
        }

        NetworkSyncService sync = NetworkSyncService.get();
        sb.append("&7Cross-server sync: ")
                .append(sync != null ? "&aACTIVE" : "&cENABLED BUT NOT RUNNING")
                .append("&r\n");

        if (sync != null) {
            sb.append("&7Server id: &f").append(sync.getServerId()).append("&r\n");
            sb.append("&7Sync channel: &f").append(sync.getChannel()).append("&r\n");
        }
    }

    private static void appendIntegrationSummary(@Nonnull StringBuilder sb,
                                                 @Nonnull Settings settings,
                                                 IntegrationManager integrations,
                                                 @Nonnull List<String> warnings) {
        sb.append("&eIntegrations&r\n");

        if (integrations == null) {
            sb.append("&cIntegrationManager is unavailable.&r\n");
            return;
        }

        sb.append("&7Permissions: &f")
                .append(integrations.getActivePermissionBackendName())
                .append("&r\n");
        sb.append("&7Economy: &f")
                .append(integrations.getEconomyMode().name())
                .append(integrations.hasAnyEconomy() ? " &aavailable" : " &7none")
                .append("&r\n");
        List<String> ledgerBackendNames = integrations.getAvailableLedgerBackendNames();
        sb.append("&7Ledger backends: &f")
                .append(ledgerBackendNames.isEmpty()
                        ? "none"
                        : String.join(", ", ledgerBackendNames))
                .append("&r\n");
        sb.append("&7Playtime: &f")
                .append(integrations.getPlaytimeProviderName())
                .append("&r\n");
        sb.append("&7Placeholders: &f")
                .append(formatPlaceholderBackends(settings))
                .append("&r\n");
        sb.append("&7Endless nameplate bridge: ")
                .append(integrations.isEndlessLevelingNameplateAttached() ? "&aattached" : "&7not attached")
                .append("&r\n");
        sb.append("&7MMOSkillTree stat bridge: ")
                .append(MMOSkillTreeCompat.isAvailable() ? "&aavailable" : "&7not detected")
                .append("&r\n");
        sb.append("&7MysticVanish vanish hook: ")
                .append(integrations.isMysticVanishAvailable() ? "&aactive" : "&7not detected")
                .append("&r\n");

        for (String warning : warnings) {
            sb.append("&eWARN &7integration&f: ")
                    .append(warning)
                    .append("&r\n");
        }
    }

    private static void appendTagSummary(@Nonnull StringBuilder sb,
                                         TagManager tags,
                                         @Nonnull TagConfigValidator.Report report) {
        sb.append("&eTags&r\n");
        sb.append("&7tags.json: &f")
                .append(report.getFile().getAbsolutePath())
                .append("&r\n");
        sb.append("&7Parsed entries: &f")
                .append(report.getRawTagCount())
                .append(" &7raw, &f")
                .append(report.getUniqueTagCount())
                .append(" &7unique, &f")
                .append(report.getCategoryCount())
                .append(" &7categories&r\n");

        if (tags != null) {
            sb.append("&7Loaded manager tags: &f")
                    .append(tags.getTagCount())
                    .append("&r\n");
        } else {
            sb.append("&cTagManager is unavailable.&r\n");
        }

        sb.append("&7Validation: &f")
                .append(report.count(TagConfigValidator.Severity.ERROR))
                .append("&7 errors, &f")
                .append(report.count(TagConfigValidator.Severity.WARNING))
                .append("&7 warnings, &f")
                .append(report.count(TagConfigValidator.Severity.INFO))
                .append("&7 info&r\n");
    }

    private static int inspectStorage(@Nonnull Settings settings,
                                      @Nonnull File dataFolder) {
        StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());
        return switch (backend) {
            case FILE -> {
                File playerDataFolder = new File(dataFolder, "playerdata");
                yield (playerDataFolder.isDirectory() && playerDataFolder.canWrite()) ? 0 : 1;
            }
            case SQLITE -> {
                File sqliteFile = new File(dataFolder, settings.getSqliteFile());
                File parent = sqliteFile.getAbsoluteFile().getParentFile();
                yield (parent != null && parent.canWrite()) ? 0 : 1;
            }
            // No connection is attempted here: /doctor must not block the main
            // thread on a remote server. A missing driver is the failure that
            // silently killed every SQL backend, so that is what gets checked.
            case MYSQL, MARIADB -> JdbcDrivers.isAvailable(backend.mySqlJdbcUrl(
                    settings.getMysqlHost(), settings.getMysqlPort(), settings.getMysqlDatabase())) ? 0 : 1;
            case REDIS -> {
                RedisManager redis = RedisManager.get();
                yield (redis != null && redis.isHealthy()) ? 0 : 1;
            }
        };
    }

    @Nonnull
    private static List<String> inspectIntegrationWarnings(@Nonnull Settings settings,
                                                           IntegrationManager integrations) {
        List<String> warnings = new ArrayList<>();
        if (integrations == null) {
            warnings.add("IntegrationManager is unavailable.");
            return warnings;
        }
        if (settings.isEconomySystemEnabled() && !integrations.hasAnyEconomy()) {
            warnings.add("Economy is enabled, but no economy backend is available.");
        }
        if (settings.isRpgLevelingNameplatesEnabled() && !RPGLevelingCompat.isAvailable()) {
            warnings.add("RPGLeveling nameplates are enabled, but RPGLeveling is not available.");
        }
        if (settings.isEndlessLevelingNameplatesEnabled() && !integrations.isEndlessLevelingNameplateAttached()) {
            warnings.add("Endless Leveling nameplates are enabled, but the bridge is not attached.");
        }
        return warnings;
    }

    @Nonnull
    private static String formatPlaceholderBackends(@Nonnull Settings settings) {
        List<String> names = new ArrayList<>();
        if (settings.isWiFlowPlaceholdersEnabled()) {
            names.add("WiFlow");
        }
        if (settings.isHelpchPlaceholderApiEnabled()) {
            names.add("HelpChat");
        }
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    @Nonnull
    private static String colorFor(@Nonnull TagConfigValidator.Severity severity) {
        return switch (severity) {
            case ERROR -> "&c";
            case WARNING -> "&e";
            case INFO -> "&b";
        };
    }

    @Nonnull
    private static String symbolFor(@Nonnull TagConfigValidator.Severity severity) {
        return switch (severity) {
            case ERROR -> "ERROR";
            case WARNING -> "WARN";
            case INFO -> "INFO";
        };
    }
}
