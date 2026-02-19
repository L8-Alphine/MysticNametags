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
import com.mystichorizons.mysticnametags.config.LanguageManager;
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

    private boolean hasUiPermission(@Nonnull CommandContext context) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) return false;

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
        LanguageManager lang = LanguageManager.get();

        if (!hasUiPermission(context)) {
            context.sender().sendMessage(
                    colored(lang.tr("cmd.ui.no_permission"))
            );
            return;
        }

        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            context.sender().sendMessage(
                    colored(lang.tr("cmd.ui.not_loaded"))
            );
            return;
        }

        context.sender().sendMessage(
                colored(lang.tr("cmd.ui.opening"))
        );

        try {
            // Get the player component (safe - we're on world thread)
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                context.sender().sendMessage(
                        colored(lang.tr("cmd.ui.no_player_component"))
                );
                return;
            }

            // Create and open the custom page
            MysticNameTagsDashboardUI dashboardPage = new MysticNameTagsDashboardUI(playerRef);
            player.getPageManager().openCustomPage(ref, store, dashboardPage);

            context.sender().sendMessage(
                    colored(lang.tr("cmd.ui.opened"))
            );
        } catch (Exception e) {
            context.sender().sendMessage(
                    colored(lang.tr("cmd.ui.open_error", java.util.Map.of(
                            "error", e.getMessage() == null ? "Unknown" : e.getMessage()
                    )))
            );
        }
    }
}
