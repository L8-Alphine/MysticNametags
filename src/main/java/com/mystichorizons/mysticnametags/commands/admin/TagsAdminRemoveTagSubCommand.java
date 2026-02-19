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

public class TagsAdminRemoveTagSubCommand extends AbstractTagsAdminSubCommand {

    @Nonnull
    private final RequiredArg<PlayerRef> targetArg =
            this.withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);

    @Nonnull
    private final RequiredArg<String> tagIdArg =
            this.withRequiredArg("tagId", "Tag id from tags.json", ArgTypes.STRING);

    public TagsAdminRemoveTagSubCommand() {
        super("removetag", "Remove a tag from a player");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin removetag"
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
            context.sender().sendMessage(colored(lang.tr("cmd.admin.removetag.usage")));
            return;
        }

        UUID targetUuid = targetRef.getUuid();
        boolean removed = TagManager.get().adminRemoveTag(targetUuid, tagId);

        if (!removed) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.removetag.not_owned", Map.of(
                    "player", targetRef.getUsername(),
                    "tagId", tagId
            ))));
            return;
        }

        context.sender().sendMessage(colored(lang.tr("cmd.admin.removetag.success", Map.of(
                "player", targetRef.getUsername(),
                "tagId", tagId
        ))));
    }
}
