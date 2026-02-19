package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.tags.StorageBackend;

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

            case MYSQL: {
                String host = settings.getMysqlHost();
                int port    = settings.getMysqlPort();
                String db   = settings.getMysqlDatabase();
                String user = settings.getMysqlUser();

                sb.append("&7MySQL Host: &f").append(host).append("&r\n");
                sb.append("&7MySQL Port: &f").append(port).append("&r\n");
                sb.append("&7MySQL Database: &f").append(db).append("&r\n");
                sb.append("&7MySQL User: &f").append(user).append("&r\n");
                break;
            }
        }

        context.sender().sendMessage(colored(sb.toString()));
    }
}
