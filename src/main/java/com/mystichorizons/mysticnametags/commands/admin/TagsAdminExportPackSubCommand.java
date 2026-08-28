package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import java.util.Map;

public class TagsAdminExportPackSubCommand extends AbstractTagsAdminSubCommand {

    @Nonnull
    private final RequiredArg<String> packArg =
            this.withRequiredArg("packFile", "JSON file to write inside the tagpacks folder", ArgTypes.STRING);

    @Nonnull
    private final OptionalArg<String> categoryArg =
            this.withOptionalArg("category", "Only export tags of this category", ArgTypes.STRING);

    public TagsAdminExportPackSubCommand() {
        super("exportpack", "Export the loaded tags to a pack in the tagpacks folder");
        this.addAliases("exporttags", "packexport");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin exportpack"
            ))));
            return;
        }

        String pack = packArg.get(context);
        if (pack == null || pack.isBlank()) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.exportpack.usage")));
            return;
        }

        TagManager.TagPackExportResult result =
                TagManager.get().exportTagPack(pack, categoryArg.get(context), actorName(context));

        if (!result.isSuccess()) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.exportpack.failed", Map.of(
                    "error", result.getError() == null ? "Unknown" : result.getError()
            ))));
            return;
        }

        context.sender().sendMessage(colored(lang.tr("cmd.admin.exportpack.success", Map.of(
                "pack", result.getFile() == null ? pack : result.getFile().getName(),
                "count", String.valueOf(result.getExported())
        ))));
    }
}
