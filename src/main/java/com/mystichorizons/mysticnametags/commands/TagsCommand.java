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
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.ui.MysticNameTagsTagsUI;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TagsCommand extends AbstractPlayerCommand {

    public TagsCommand() {
        super("tags", "Open the tag selection UI");
        this.addAliases("tag");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    private Message colored(String text) {
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
        LanguageManager lang = LanguageManager.get();
        CommandSender sender = context.sender();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sender.sendMessage(colored(lang.tr("cmd.tags.no_player_component")));
            return;
        }

        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            sender.sendMessage(colored(lang.tr("cmd.tags.no_account_id")));
            return;
        }

        sender.sendMessage(colored(lang.tr("cmd.tags.opening")));

        try {
            MysticNameTagsTagsUI page = new MysticNameTagsTagsUI(playerRef, uuid);
            player.getPageManager().openCustomPage(ref, store, page);
        } catch (Exception e) {
            sender.sendMessage(colored(lang.tr("cmd.tags.open_error",
                    java.util.Map.of("error", e.getMessage() == null ? "Unknown" : e.getMessage())
            )));
        }
    }
}
