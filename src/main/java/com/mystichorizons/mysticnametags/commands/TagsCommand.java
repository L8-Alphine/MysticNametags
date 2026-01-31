package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.ui.MysticNameTagsTagsUI;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TagsCommand extends AbstractPlayerCommand {

    public TagsCommand() {
        super("tags", "Open the tag selection UI");
        this.addAliases("tag");         // /tag works too
        this.setPermissionGroup(null);  // public
    }

    @Override
    protected boolean canGeneratePermission() {
        // Keep this public; don't let the framework auto-generate a node.
        return false;
    }

    private Message colored(String text) {
        // Let ColorFormatter interpret & and hex codes into a styled Message.
        return ColorFormatter.toMessage(text);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        CommandSender sender = context.sender();

        // Get the Player component on the world thread
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sender.sendMessage(colored("&cError: &7Could not get Player component."));
            return;
        }

        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            sender.sendMessage(colored("&cError: &7Could not determine your account id."));
            return;
        }

        sender.sendMessage(colored("&7[&bMysticNameTags&7] &fOpening &bTag Selector&f..."));

        try {
            MysticNameTagsTagsUI page = new MysticNameTagsTagsUI(playerRef, uuid);
            player.getPageManager().openCustomPage(ref, store, page);
        } catch (Exception e) {
            sender.sendMessage(
                    colored("&cError opening tag selector: &7" + e.getMessage())
            );
        }
    }
}
