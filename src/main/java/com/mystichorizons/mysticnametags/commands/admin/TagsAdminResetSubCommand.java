package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public class TagsAdminResetSubCommand extends AbstractTagsAdminSubCommand {

    @Nonnull
    private final RequiredArg<PlayerRef> targetArg =
            this.withRequiredArg("player", "Player whose tags should be reset", ArgTypes.PLAYER_REF);

    @Nonnull
    private final OptionalArg<Boolean> resetPermsArg =
            this.withOptionalArg("resetPerms", "Whether to also revoke tag permissions (true/false)", ArgTypes.BOOLEAN);

    public TagsAdminResetSubCommand() {
        super("reset", "Reset all tags for a player");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin reset"
            ))));
            return;
        }

        PlayerRef targetRef = targetArg.get(context);
        if (targetRef == null) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.target_not_found")));
            return;
        }

        UUID targetUuid = targetRef.getUuid();

        boolean resetPerms = false;
        Boolean flag = resetPermsArg.get(context);
        if (flag != null) resetPerms = flag;

        boolean changed = resetPerms
                ? TagManager.get().adminResetTagsAndPermissions(targetUuid)
                : TagManager.get().adminResetTags(targetUuid);

        if (!changed) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.reset.none_to_reset", Map.of(
                    "player", targetRef.getUsername()
            ))));
            return;
        }

        if (resetPerms) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.reset.success_with_perms", Map.of(
                    "player", targetRef.getUsername()
            ))));
        } else {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.reset.success", Map.of(
                    "player", targetRef.getUsername()
            ))));
        }
    }
}
