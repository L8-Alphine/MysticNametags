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

public class TagsAdminImportPackSubCommand extends AbstractTagsAdminSubCommand {

    @Nonnull
    private final RequiredArg<String> packArg =
            this.withRequiredArg("packFile", "JSON file inside the tagpacks folder", ArgTypes.STRING);

    @Nonnull
    private final OptionalArg<String> modeArg =
            this.withOptionalArg("mode", "append/upsert/replace", ArgTypes.STRING);

    public TagsAdminImportPackSubCommand() {
        super("importpack", "Import a tag pack from the tagpacks folder");
        this.addAliases("importtags", "packimport");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin importpack"
            ))));
            return;
        }

        String pack = packArg.get(context);
        if (pack == null || pack.isBlank()) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.importpack.usage")));
            return;
        }

        TagManager.TagPackImportMode mode = TagManager.TagPackImportMode.from(modeArg.get(context));
        TagManager.TagPackImportResult result =
                TagManager.get().importTagPack(pack, mode, actorName(context));

        if (!result.isSuccess()) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.importpack.failed", Map.of(
                    "error", result.getError() == null ? "Unknown" : result.getError()
            ))));
            return;
        }

        context.sender().sendMessage(colored(lang.tr("cmd.admin.importpack.success", Map.of(
                "pack", result.getFile() == null ? pack : result.getFile().getName(),
                "added", String.valueOf(result.getAdded()),
                "replaced", String.valueOf(result.getReplaced()),
                "skipped", String.valueOf(result.getSkipped())
        ))));
    }
}
