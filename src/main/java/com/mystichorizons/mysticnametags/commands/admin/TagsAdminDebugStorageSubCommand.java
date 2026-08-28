package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.network.NetworkSyncService;
import com.mystichorizons.mysticnametags.network.RedisManager;
import com.mystichorizons.mysticnametags.tags.StorageBackend;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Map;

public class TagsAdminDebugStorageSubCommand extends AbstractTagsAdminSubCommand {

    public TagsAdminDebugStorageSubCommand() {
        super("debugstorage", "Debug MysticNameTags storage backend");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin debugstorage"
            ))));
            return;
        }

        Settings settings = Settings.get();
        StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());
        File dataFolder = MysticNameTagsPlugin.getInstance()
                .getDataDirectory().toFile();

        StringBuilder sb = new StringBuilder();
        sb.append("&bMysticNameTags Storage Debug&r\n");
        sb.append("&7Backend: &e").append(backend.name()).append("&r\n");

        switch (backend) {
            case FILE: {
                File playerDataFolder = new File(dataFolder, "playerdata");
                File legacyFolder = new File(dataFolder, "playerdata_legacy");

                sb.append("&7Playerdata folder: &f")
                        .append(playerDataFolder.getAbsolutePath())
                        .append("&r\n");
                sb.append("&7Exists: ")
                        .append(playerDataFolder.exists() ? "&aYES" : "&cNO")
                        .append("&r\n");

                if (playerDataFolder.exists() && playerDataFolder.isDirectory()) {
                    File[] jsonFiles = playerDataFolder.listFiles((dir, name) -> name.endsWith(".json"));
                    int count = (jsonFiles != null) ? jsonFiles.length : 0;
                    sb.append("&7JSON files: &f").append(count).append("&r\n");
                }

                // Also show if a migrated folder exists
                sb.append("&7Legacy folder: &f")
                        .append(legacyFolder.getAbsolutePath())
                        .append("&r\n");
                sb.append("&7Legacy exists: ")
                        .append(legacyFolder.exists() ? "&eYES" : "&7NO")
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

                if (sqliteFile.exists()) {
                    long size = sqliteFile.length();
                    sb.append("&7Size: &f").append(size).append(" bytes&r\n");
                }

                // Optional: mention that connection checks are done at startup
                sb.append("&7Note: &fConnection/schema errors are logged on server startup.&r\n");
                break;
            }

            case MYSQL: {
                String host = settings.getMysqlHost();
                int port    = settings.getMysqlPort();
                String db   = settings.getMysqlDatabase();
                String user = settings.getMysqlUser();

                sb.append("&7MySQL Host: &f").append(host).append("&r\n");
                sb.append("&7MySQL Port: &f").append(port).append("&r\n");
                sb.append("&7MySQL Database: &f").append(db).append("&r\n");
                sb.append("&7MySQL User: &f").append(user).append("&r\n");

                sb.append("&7Note: &fIf credentials/host are wrong, check startup logs for SQL errors.&r\n");
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
                sb.append("&7Player key pattern: &f")
                        .append(settings.getRedisKeyPrefix())
                        .append("tags:<uuid>&r\n");
                sb.append("&7Connected: ")
                        .append(redis != null && redis.isHealthy() ? "&aYES" : "&cNO")
                        .append("&r\n");

                sb.append("&7Note: &fRedis needs RDB or AOF persistence enabled, "
                        + "or tag ownership is lost when Redis restarts.&r\n");
                break;
            }
        }

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

        context.sender().sendMessage(colored(sb.toString()));
    }
}
