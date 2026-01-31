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

    /**
     * /tagsadmin open <player>
     * Required player argument parsed by the command framework.
     */
    @Nonnull
    private final RequiredArg<PlayerRef> targetArg =
            this.withRequiredArg(
                    "player",
                    "Player to open the tag UI for", // plain description is fine
                    ArgTypes.PLAYER_REF
            );

    public TagsAdminOpenSubCommand() {
        super("open", "Open the tag UI for another player");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef senderRef,
                           @Nonnull World world) {

        if (!hasAdminPermission(context)) {
            context.sender().sendMessage(
                    colored("&cYou do not have permission to use &f/tagsadmin open&c.")
            );
            return;
        }

        // Will already have been validated by the framework
        PlayerRef targetRef = targetArg.get(context);
        if (targetRef == null) {
            // Extremely defensive; normally RequiredArg guarantees non-null
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

        Store<EntityStore> targetStore = targetEntityRef.getStore();
        Player targetPlayer = targetStore.getComponent(targetEntityRef, Player.getComponentType());
        if (targetPlayer == null) {
            context.sender().sendMessage(
                    colored("&cError: &7Could not get Player component for '&f" +
                            targetRef.getUsername() + "&7'.")
            );
            return;
        }

        UUID targetUuid = targetRef.getUuid();

        context.sender().sendMessage(
                colored("&7[&bMysticNameTags&7] &fOpening &bTag Selector&f for &b" +
                        targetRef.getUsername() + "&f...")
        );

        // Ensure page open runs on the target's world thread
        World targetWorld = ((EntityStore) targetStore.getExternalData()).getWorld();
        targetWorld.execute(() -> {
            try {
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
