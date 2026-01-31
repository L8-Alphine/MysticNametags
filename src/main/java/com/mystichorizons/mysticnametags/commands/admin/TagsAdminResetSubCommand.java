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

public class TagsAdminResetSubCommand extends AbstractTagsAdminSubCommand {

    // /tagsadmin reset <player>

    @Nonnull
    private final RequiredArg<PlayerRef> targetArg =
            this.withRequiredArg(
                    "player",
                    "Player whose tags should be reset",
                    ArgTypes.PLAYER_REF
            );

    public TagsAdminResetSubCommand() {
        super("reset", "Reset all tags for a player");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef senderRef,
                           @Nonnull World world) {

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(
                    colored("&cYou do not have permission to use &f/tagsadmin reset&c.")
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

        UUID targetUuid = targetRef.getUuid();
        boolean changed = TagManager.get().adminResetTags(targetUuid);

        if (!changed) {
            context.sender().sendMessage(
                    colored("&ePlayer &f" + targetRef.getUsername() +
                            "&e has no stored tags to reset.")
            );
            return;
        }

        context.sender().sendMessage(
                colored("&aReset all tags for &b" + targetRef.getUsername() + "&a.")
        );
    }
}
