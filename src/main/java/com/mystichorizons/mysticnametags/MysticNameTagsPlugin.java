package com.mystichorizons.mysticnametags;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.mystichorizons.mysticnametags.commands.MysticNameTagsPluginCommand;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.listeners.PlayerListener;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;
import com.mystichorizons.mysticnametags.placeholders.PlaceholderHook;
import com.mystichorizons.mysticnametags.tags.TagManager;

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

    /**
     * Expose integrations to commands / other systems.
     */
    public IntegrationManager getIntegrations() {
        return integrations;
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Setting up...");

        this.integrations = new IntegrationManager();

        // Init core config + tags
        Settings.init();
        TagManager.init(integrations);

        // Soft-economy detection
        boolean vault = integrations.isVaultAvailable();
        boolean elite = integrations.isEliteEconomyAvailable();

        if (vault) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] VaultUnlocked detected – tag purchasing enabled (primary backend).");

            if (elite) {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] EliteEssentials economy also detected – using VaultUnlocked over EliteEssentials.");
            }
        } else if (elite) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] EliteEssentials economy detected – tag purchasing enabled.");
        } else {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] No compatible economy found – tags can only be unlocked via permissions.");
        }

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

        // WiFlowPlaceholderAPI ({mystictags_...}) – new expansion
        try {
            new com.mystichorizons.mysticnametags.placeholders.WiFlowPlaceholderHook().register();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to register WiFlowPlaceholderAPI expansion. Maybe not installed? Disabled Placeholder Support.");
        }
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Shutting down...");
        NameplateManager.get().clearAll();
        instance = null;
    }
}
