package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TagsAdminGiveTagSubCommand extends AbstractTagsAdminSubCommand {

    // /tagsadmin givetag <player> <tagId>

    @Nonnull
    private final RequiredArg<PlayerRef> targetArg =
            this.withRequiredArg(
                    "player",
                    "Target player",
                    ArgTypes.PLAYER_REF
            );

    @Nonnull
    private final RequiredArg<String> tagIdArg =
            this.withRequiredArg(
                    "tagId",
                    "Tag id from tags.json",
                    ArgTypes.STRING
            );

    public TagsAdminGiveTagSubCommand() {
        super("givetag", "Grant a tag to a player");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(
                    colored("&cYou do not have permission to use &f/tagsadmin givetag&c.")
            );
            return;
        }

        PlayerRef targetRef = targetArg.get(context);
        if (targetRef == null) {
            context.sender().sendMessage(
                    colored("&cCould not resolve target player.")
            );
            return;
        }

        String rawTagId = tagIdArg.get(context);
        String tagId = (rawTagId != null) ? rawTagId.trim() : "";
        if (tagId.isEmpty()) {
            context.sender().sendMessage(
                    colored("&cUsage: &f/tagsadmin givetag <player> <tagId>")
            );
            return;
        }

        UUID targetUuid = targetRef.getUuid();

        // Admin helper: force-grant + equip tag, bypassing economy/perm gate.
        boolean success = TagManager.get().adminGiveTag(targetUuid, tagId, true);
        if (!success) {
            context.sender().sendMessage(
                    colored("&cUnknown tag id '&f" + tagId + "&c'.")
            );
            return;
        }

        context.sender().sendMessage(
                colored("&aGave tag '&f" + tagId + "&a' to &b" +
                        targetRef.getUsername() + "&a (equipped).")
        );
    }
}
