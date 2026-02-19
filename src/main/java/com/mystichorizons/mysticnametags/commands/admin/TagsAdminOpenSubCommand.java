package com.mystichorizons.mysticnametags.commands.admin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.commands.AbstractTagsAdminSubCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.ui.MysticNameTagsTagsUI;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public class TagsAdminOpenSubCommand extends AbstractTagsAdminSubCommand {

    @Nonnull
    private final RequiredArg<PlayerRef> targetArg =
            this.withRequiredArg("player", "Player to open the tag UI for", ArgTypes.PLAYER_REF);

    public TagsAdminOpenSubCommand() {
        super("open", "Open the tag UI for another player");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.no_permission", Map.of(
                    "usage", "/tagsadmin open"
            ))));
            return;
        }

        PlayerRef targetRef = targetArg.get(context);
        if (targetRef == null) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.target_not_found")));
            return;
        }

        Ref<EntityStore> targetEntityRef = targetRef.getReference();
        if (targetEntityRef == null || !targetEntityRef.isValid()) {
            context.sender().sendMessage(colored(lang.tr("cmd.admin.open.not_in_world")));
            return;
        }

        Store<EntityStore> targetStore = targetEntityRef.getStore();
        World targetWorld = ((EntityStore) targetStore.getExternalData()).getWorld();
        UUID targetUuid = targetRef.getUuid();

        context.sender().sendMessage(colored(lang.tr("cmd.admin.open.opening", Map.of(
                "player", targetRef.getUsername()
        ))));

        targetWorld.execute(() -> {
            try {
                Player targetPlayer = targetStore.getComponent(targetEntityRef, Player.getComponentType());
                if (targetPlayer == null) {
                    context.sender().sendMessage(colored(lang.tr("cmd.admin.open.no_player_component", Map.of(
                            "player", targetRef.getUsername()
                    ))));
                    return;
                }

                MysticNameTagsTagsUI page = new MysticNameTagsTagsUI(targetRef, targetUuid);
                targetPlayer.getPageManager().openCustomPage(targetEntityRef, targetStore, page);

            } catch (Exception e) {
                context.sender().sendMessage(colored(lang.tr("cmd.admin.open.error", Map.of(
                        "player", targetRef.getUsername(),
                        "error", e.getMessage() == null ? "Unknown" : e.getMessage()
                ))));
            }
        });
    }
}
