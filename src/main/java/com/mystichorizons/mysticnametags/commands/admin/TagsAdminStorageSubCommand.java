package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.network.NetworkSyncService;
import com.mystichorizons.mysticnametags.network.RedisManager;
import com.mystichorizons.mysticnametags.tags.StorageBackend;
import com.mystichorizons.mysticnametags.util.JdbcDrivers;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Map;

public class TagsAdminStorageSubCommand extends AbstractTagsAdminSubCommand {

    public TagsAdminStorageSubCommand() {
        super("storage", "Show MysticNameTags storage backend info");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            // Reuse your existing localized no-permission message
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin storage"
            ))));
            return;
        }

        Settings settings = Settings.get();
        StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());

        File dataFolder = MysticNameTagsPlugin.getInstance()
                .getDataDirectory().toFile();

        StringBuilder sb = new StringBuilder();
        sb.append("&bMysticNameTags Storage Info&r\n");
        sb.append("&7Active backend: &e").append(backend.name()).append("&r\n");

        switch (backend) {
            case FILE: {
                File playerDataFolder = new File(dataFolder, "playerdata");
                sb.append("&7Player data folder: &f")
                        .append(playerDataFolder.getAbsolutePath())
                        .append("&r\n");
                sb.append("&7Exists: ")
                        .append(playerDataFolder.exists() ? "&aYES" : "&cNO")
                        .append("&r\n");
                break;
            }

            case SQLITE: {
                String sqliteFileName = settings.getSqliteFile();
                File sqliteFile = new File(dataFolder, sqliteFileName);

                sb.append("&7SQLite file: &f")
                        .append(sqliteFile.getAbsolutePath())
                        .append("&r\n");
                sb.append("&7Exists: ")
                        .append(sqliteFile.exists() ? "&aYES" : "&cNO")
                        .append("&r\n");
                break;
            }

            case MYSQL:
            case MARIADB: {
                String host = settings.getMysqlHost();
                int port    = settings.getMysqlPort();
                String db   = settings.getMysqlDatabase();
                String user = settings.getMysqlUser();
                String jdbcUrl = backend.mySqlJdbcUrl(host, port, db);

                sb.append("&7SQL Host: &f").append(host).append("&r\n");
                sb.append("&7SQL Port: &f").append(port).append("&r\n");
                sb.append("&7SQL Database: &f").append(db).append("&r\n");
                sb.append("&7SQL User: &f").append(user).append("&r\n");
                sb.append("&7JDBC driver: ")
                        .append(JdbcDrivers.isAvailable(jdbcUrl) ? "&a" : "&c")
                        .append(JdbcDrivers.describeDriver(jdbcUrl))
                        .append("&r\n");
                break;
            }

            case REDIS: {
                RedisManager redis = RedisManager.get();

                sb.append("&7Redis Host: &f").append(settings.getRedisHost()).append("&r\n");
                sb.append("&7Redis Port: &f").append(settings.getRedisPort()).append("&r\n");
                sb.append("&7Redis Database: &f").append(settings.getRedisDatabase()).append("&r\n");
                sb.append("&7Redis User: &f")
                        .append(settings.getRedisUser().isEmpty() ? "(default)" : settings.getRedisUser())
                        .append("&r\n");
                sb.append("&7TLS: &f").append(settings.isRedisSsl() ? "on" : "off").append("&r\n");
                sb.append("&7Key Prefix: &f").append(settings.getRedisKeyPrefix()).append("&r\n");
                sb.append("&7Connected: ")
                        .append(redis != null && redis.isHealthy() ? "&aYES" : "&cNO")
                        .append("&r\n");
                break;
            }
        }

        appendSyncInfo(sb, settings);

        context.sender().sendMessage(colored(sb.toString()));
    }

    /** Sync runs independently of the backend, so it is always worth showing. */
    private static void appendSyncInfo(@Nonnull StringBuilder sb, @Nonnull Settings settings) {
        NetworkSyncService sync = NetworkSyncService.get();

        sb.append("&7Cross-server sync: ");
        if (!settings.isRedisSyncEnabled()) {
            sb.append("&7DISABLED");
        } else if (sync != null) {
            sb.append("&aACTIVE");
        } else {
            sb.append("&cENABLED BUT NOT RUNNING");
        }
        sb.append("&r\n");

        if (sync != null) {
            sb.append("&7Server id: &f").append(sync.getServerId()).append("&r\n");
            sb.append("&7Sync channel: &f").append(sync.getChannel()).append("&r\n");
        }
    }
}
