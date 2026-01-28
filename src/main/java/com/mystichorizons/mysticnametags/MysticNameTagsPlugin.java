package com.mystichorizons.mysticnametags;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.commands.MysticNameTagsPluginCommand;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.listeners.PlayerListener;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;
import com.mystichorizons.mysticnametags.placeholders.PlaceholderHook;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.ui.MysticNameTagsTagsUI;
import cz.creeperface.hytale.placeholderapi.api.PlaceholderAPI;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * MysticNameTags - A Hytale server plugin.
 */
public class MysticNameTagsPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static MysticNameTagsPlugin instance;

    private IntegrationManager integrations;

    public MysticNameTagsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    /**
     * Get the plugin instance.
     *
     * @return The plugin instance
     */
    public static MysticNameTagsPlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Setting up...");

        this.integrations = new IntegrationManager();
//        this.integrations.init();

        // Init core config + tags
        Settings.init();
        TagManager.init(integrations);

        // Register commands
        registerCommands();

        // Register event listeners
        registerListeners();

        LOGGER.at(Level.INFO).log("[MysticNameTags] Setup complete!");
    }

    private void registerCommands() {
        try {
            getCommandRegistry().registerCommand(new MysticNameTagsPluginCommand());
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered /tags command");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[MysticNameTags] Failed to register commands");
        }
    }

    private void registerListeners() {
        EventRegistry eventBus = getEventRegistry();

        try {
            new PlayerListener().register(eventBus);
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered player event listeners");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[MysticNameTags] Failed to register listeners");
        }
    }

    @Override
    protected void start() {
        this.integrations.init();

        LOGGER.at(Level.INFO).log("[MysticNameTags] Started!");
        LOGGER.at(Level.INFO).log("[MysticNameTags] Use /tags help for commands");

        // Register PlaceholderAPI placeholders
        try {
            new PlaceholderHook().register();
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] PlaceholderAPI hooked successfully.");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] PlaceholderAPI not present, placeholders disabled.");
        }
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Shutting down...");
        NameplateManager.get().clearAll();
        instance = null;
    }
}
