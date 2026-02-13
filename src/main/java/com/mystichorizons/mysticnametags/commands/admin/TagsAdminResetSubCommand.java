package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TagsAdminResetSubCommand extends AbstractTagsAdminSubCommand {

    // /tagsadmin reset <player> [resetPerms]

    @Nonnull
    private final RequiredArg<PlayerRef> targetArg =
            this.withRequiredArg(
                    "player",
                    "Player whose tags should be reset",
                    ArgTypes.PLAYER_REF
            );

    @Nonnull
    private final OptionalArg<Boolean> resetPermsArg =
            this.withOptionalArg(
                    "resetPerms",
                    "Whether to also revoke tag permissions (true/false)",
                    ArgTypes.BOOLEAN
            );

    public TagsAdminResetSubCommand() {
        super("reset", "Reset all tags for a player");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {

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

        // Default: data-only reset.
        boolean resetPerms = false;
        Boolean flag = resetPermsArg.get(context);
        if (flag != null) {
            resetPerms = flag;
        }

        boolean changed = resetPerms
                ? TagManager.get().adminResetTagsAndPermissions(targetUuid)
                : TagManager.get().adminResetTags(targetUuid);

        if (!changed) {
            context.sender().sendMessage(
                    colored("&ePlayer &f" + targetRef.getUsername() +
                            "&e has no stored tags to reset.")
            );
            return;
        }

        if (resetPerms) {
            context.sender().sendMessage(
                    colored("&aReset all tags & revoked tag permissions for &b"
                            + targetRef.getUsername() + "&a.")
            );
        } else {
            context.sender().sendMessage(
                    colored("&aReset all tags for &b" + targetRef.getUsername() + "&a.")
            );
        }
    }
}
