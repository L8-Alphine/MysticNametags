package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TagsAdminRemoveTagSubCommand extends AbstractTagsAdminSubCommand {

    // /tagsadmin removetag <player> <tagId>

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

    public TagsAdminRemoveTagSubCommand() {
        super("removetag", "Remove a tag from a player");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef senderRef,
                           @Nonnull World world) {

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(
                    colored("&cYou do not have permission to use &f/tagsadmin removetag&c.")
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
                    colored("&cUsage: &f/tagsadmin removetag <player> <tagId>")
            );
            return;
        }

        UUID targetUuid = targetRef.getUuid();
        boolean removed = TagManager.get().adminRemoveTag(targetUuid, tagId);

        if (!removed) {
            context.sender().sendMessage(
                    colored("&cPlayer &f" + targetRef.getUsername() +
                            "&c does not have tag '&f" + tagId + "&c'.")
            );
            return;
        }

        context.sender().sendMessage(
                colored("&aRemoved tag '&f" + tagId + "&a' from &b" +
                        targetRef.getUsername() + "&a.")
        );
    }
}
