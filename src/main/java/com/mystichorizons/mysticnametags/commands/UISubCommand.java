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
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.ui.MysticNameTagsDashboardUI;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;

/**
 * /tags ui - Open the plugin dashboard UI
 *
 * Extends AbstractPlayerCommand to ensure proper thread handling
 * when opening custom UI pages.
 */
public class UISubCommand extends AbstractPlayerCommand {

    // Permission node to open the dashboard
    public static final String PERMISSION_NODE = "mysticnametags.ui.open";

    public UISubCommand() {
        super("ui", "Open the plugin dashboard");
        this.addAliases(new String[]{"dashboard", "gui"});
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    private boolean hasReloadPermission(@Nonnull CommandContext context) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            return false;
        }

        IntegrationManager integrations = plugin.getIntegrations();
        CommandSender sender = context.sender();

        // Use IntegrationManager, which prefers LuckPerms but falls back to Hytale perms
        return integrations.hasPermission(sender, PERMISSION_NODE);
    }

    private Message colored(String text) {
        // Let ColorFormatter interpret & and hex codes into a styled Message.
        return ColorFormatter.toMessage(text);
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
        if (!hasReloadPermission(context)) {
            context.sender().sendMessage(
                    colored("&cYou do not have permission to use this command.")
            );
            return;
        }

        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            context.sender().sendMessage(
                    colored("&cMysticNameTags is not loaded.")
            );
            return;
        }

        context.sender().sendMessage(
                colored("&7[&bMysticNameTags&7] &fOpening &bDashboard&f...")
        );

        try {
            // Get the player component (safe - we're on world thread)
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sender().sendMessage(
                        colored("&cError: Could not get &7Player component."));
                return;
            }

            // Create and open the custom page
            MysticNameTagsDashboardUI dashboardPage = new MysticNameTagsDashboardUI(playerRef);
            player.getPageManager().openCustomPage(ref, store, dashboardPage);
            context.sender().sendMessage(
                    colored("&aDashboard opened. &fPress &7ESC&f to close."));
        } catch (Exception e) {
            context.sender().sendMessage(
                    colored("&cError opening dashboard: &7" + e.getMessage()));
        }
    }
}
