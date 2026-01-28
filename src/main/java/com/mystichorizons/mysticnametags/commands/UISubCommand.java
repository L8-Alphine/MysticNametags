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

import com.mystichorizons.mysticnametags.ui.MysticNameTagsDashboardUI;
import com.mystichorizons.mysticnametags.ui.MysticNameTagsTagsUI;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * /tags ui - Open the plugin dashboard UI
 *
 * Extends AbstractPlayerCommand to ensure proper thread handling
 * when opening custom UI pages.
 */
public class UISubCommand extends AbstractPlayerCommand {

    public UISubCommand() {
        super("ui", "Open the plugin dashboard");
        this.addAliases(new String[]{"dashboard", "gui"});
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Called on the world thread with proper player context.
     */
    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        context.sendMessage(Message.raw("Opening MysticNameTags Dashboard..."));

        try {
            // Get the player component (safe - we're on world thread)
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sendMessage(Message.raw("Error: Could not get Player component."));
                return;
            }

            // Create and open the custom page
            MysticNameTagsDashboardUI dashboardPage = new MysticNameTagsDashboardUI(playerRef);
            player.getPageManager().openCustomPage(ref, store, dashboardPage);
            context.sendMessage(Message.raw("Dashboard opened. Press ESC to close."));
        } catch (Exception e) {
            context.sendMessage(Message.raw("Error opening dashboard: " + e.getMessage()));
        }
    }
}