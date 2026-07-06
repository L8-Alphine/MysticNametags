package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.tags.TagAuditLogger;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public class TagsAdminAuditSubCommand extends AbstractTagsAdminSubCommand {

    @Nonnull
    private final OptionalArg<Integer> linesArg =
            this.withOptionalArg("lines", "Number of recent audit lines to show", ArgTypes.INTEGER);

    public TagsAdminAuditSubCommand() {
        super("audit", "Show recent MysticNameTags audit log entries");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin audit"
            ))));
            return;
        }

        Integer requested = linesArg.get(context);
        int lines = requested == null ? 10 : requested;
        List<String> entries = TagAuditLogger.tail(lines);

        if (entries.isEmpty()) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.audit.empty")));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("&bMysticNameTags Audit&r\n");
        sb.append("&7Showing ").append(entries.size()).append(" recent entries&r\n");
        sb.append("&8------------------------------&r\n");

        for (String entry : entries) {
            sb.append("&7").append(entry).append("&r\n");
        }

        context.sender().sendMessage(colored(sb.toString()));
    }
}
