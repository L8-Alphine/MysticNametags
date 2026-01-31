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
import com.mystichorizons.mysticnametags.ui.MysticNameTagsTagsUI;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TagsAdminOpenSubCommand extends AbstractTagsAdminSubCommand {

    @Nonnull
    private final RequiredArg<PlayerRef> targetArg =
            this.withRequiredArg(
                    "player",
                    "Player to open the tag UI for",
                    ArgTypes.PLAYER_REF
            );

    public TagsAdminOpenSubCommand() {
        super("open", "Open the tag UI for another player");
    }

    @Override
    protected void executeAdmin(@Nonnull CommandContext context) {

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(
                    colored("&cYou do not have permission to use &f/tagsadmin open&c.")
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

        Ref<EntityStore> targetEntityRef = targetRef.getReference();
        if (targetEntityRef == null || !targetEntityRef.isValid()) {
            context.sender().sendMessage(
                    colored("&cThat player is not currently in a world.")
            );
            return;
        }

        // Safe to grab store + world ref on this thread
        Store<EntityStore> targetStore = targetEntityRef.getStore();
        World targetWorld = ((EntityStore) targetStore.getExternalData()).getWorld();
        UUID targetUuid = targetRef.getUuid();

        context.sender().sendMessage(
                colored("&7[&bMysticNameTags&7] &fOpening &bTag Selector&f for &b" +
                        targetRef.getUsername() + "&f...")
        );

        // Hop onto the world thread before touching the Store / Player
        targetWorld.execute(() -> {
            try {
                Player targetPlayer = targetStore.getComponent(targetEntityRef, Player.getComponentType());
                if (targetPlayer == null) {
                    context.sender().sendMessage(
                            colored("&cError: &7Could not get Player component for '&f" +
                                    targetRef.getUsername() + "&7'.")
                    );
                    return;
                }

                MysticNameTagsTagsUI page = new MysticNameTagsTagsUI(targetRef, targetUuid);
                targetPlayer.getPageManager().openCustomPage(targetEntityRef, targetStore, page);

            } catch (Exception e) {
                context.sender().sendMessage(
                        colored("&cError opening tag selector for '&f" +
                                targetRef.getUsername() + "&c': &7" + e.getMessage())
                );
            }
        });
    }
}
