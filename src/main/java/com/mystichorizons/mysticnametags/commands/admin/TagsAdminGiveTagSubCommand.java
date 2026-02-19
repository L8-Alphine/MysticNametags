package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public class TagsAdminGiveTagSubCommand extends AbstractTagsAdminSubCommand {

    @Nonnull
    private final RequiredArg<PlayerRef> targetArg =
            this.withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);

    @Nonnull
    private final RequiredArg<String> tagIdArg =
            this.withRequiredArg("tagId", "Tag id from tags.json", ArgTypes.STRING);

    public TagsAdminGiveTagSubCommand() {
        super("givetag", "Grant a tag to a player");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin givetag"
            ))));
            return;
        }

        PlayerRef targetRef = targetArg.get(context);
        if (targetRef == null) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.target_not_found")));
            return;
        }

        String rawTagId = tagIdArg.get(context);
        String tagId = (rawTagId != null) ? rawTagId.trim() : "";
        if (tagId.isEmpty()) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.givetag.usage")));
            return;
        }

        UUID targetUuid = targetRef.getUuid();

        boolean success = TagManager.get().adminGiveTag(targetUuid, tagId, true);
        if (!success) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.givetag.unknown_tag", Map.of(
                    "tagId", tagId
            ))));
            return;
        }

        context.sender().sendMessage(colored(lang.tr("cmd.admin.givetag.success", Map.of(
                "player", targetRef.getUsername(),
                "tagId", tagId
        ))));
    }
}
