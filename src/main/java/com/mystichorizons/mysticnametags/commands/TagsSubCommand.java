package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.ui.MysticNameTagsTagsUI;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * /tags tags - Open the tag selection UI directly.
 */
public class TagsSubCommand extends AbstractPlayerCommand {

    public TagsSubCommand() {
        super("tags", "Open the tag selection UI");
        this.addAliases(new String[]{"select", "tagmenu"});
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        // Get player entity (we're already on the world thread)
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Error: Could not get Player component."));
            return;
        }

        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            context.sendMessage(Message.raw("Error: Could not determine your account id."));
            return;
        }

        context.sendMessage(Message.raw("Opening MysticNameTags Tag Selector..."));

        try {
            MysticNameTagsTagsUI page = new MysticNameTagsTagsUI(playerRef, uuid);
            player.getPageManager().openCustomPage(ref, store, page);
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error opening tag selector: " + e.getMessage()));
        }
    }
}
