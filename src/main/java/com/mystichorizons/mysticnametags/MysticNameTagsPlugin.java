package com.mystichorizons.mysticnametags;

import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.mystichorizons.mysticnametags.commands.MysticNameTagsPluginCommand;
import com.mystichorizons.mysticnametags.commands.TagsAdminCommand;
import com.mystichorizons.mysticnametags.commands.TagsCommand;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.listeners.PlayerListener;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;
import com.mystichorizons.mysticnametags.placeholders.PlaceholderHook;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.MysticLog;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

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
    private UpdateChecker updateChecker;
    private PluginManifest manifest;

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

        this.manifest = this.getManifest();

        String version = "unknown";
        if (manifest != null && manifest.getVersion() != null) {
            version = manifest.getVersion().toString();
        }

        this.updateChecker = new UpdateChecker(version);
        // Synchronous is fine here; if you prefer async, wrap in your scheduler.
        this.updateChecker.checkForUpdates();

        this.integrations = new IntegrationManager();

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
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered /mnametags command");
            getCommandRegistry().registerCommand(new TagsCommand());
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered /tags command");
            getCommandRegistry().registerCommand(new TagsAdminCommand());
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered /tagsadmin command");
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

        MysticLog.init(this);

        LOGGER.at(Level.INFO).log("[MysticNameTags] Started!");
        LOGGER.at(Level.INFO).log("[MysticNameTags] Use /tags help for commands");

        // Register PlaceholderAPI placeholders
        try {
            new PlaceholderHook().register();
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] PlaceholderAPI hooked successfully.");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] PlaceholderAPI not present.");
        }

        // WiFlowPlaceholderAPI
        try {
            new com.mystichorizons.mysticnametags.placeholders.WiFlowPlaceholderHook().register();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Failed to register WiFlowPlaceholderAPI expansion. Maybe not installed? Disabled Placeholder Support.");
        }

        try {
            var manager = net.cfh.vault.VaultUnlockedServicesManager.get();
            LOGGER.at(Level.INFO).log("[MysticNameTags][Debug] Startup Vault econ provider names = "
                    + manager.economyProviderNames());
            LOGGER.at(Level.INFO).log("[MysticNameTags][Debug] Startup Vault economyObj() = "
                    + manager.economyObj());
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags][Debug] Error probing VaultUnlocked at startup");
        }

        try {
            boolean enabled = com.eliteessentials.api.EconomyAPI.isEnabled();
            LOGGER.at(Level.INFO).log("[MysticNameTags][Debug] Startup EconomyAPI.isEnabled() = " + enabled);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags][Debug] EconomyAPI not reachable at startup");
        }

        try {
            boolean primaryAvailable = com.economy.api.EconomyAPI.getInstance() != null;
            LOGGER.at(Level.INFO).log("[MysticNameTags][Debug] EconomySystem API available = " + primaryAvailable);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags][Debug] EconomySystem API not reachable at startup");
        }
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Shutting down...");

        try {
            NameplateManager.get().clearAll();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING)
                    .withCause(t)
                    .log("[MysticNameTags] Error while clearing nameplates during shutdown.");
        } finally {
            MysticLog.shutdown();
            instance = null;
        }
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    /** Shared "resolved version" helper for UI/commands. */
    public String getResolvedVersion() {
        if (manifest != null && manifest.getVersion() != null) {
            return manifest.getVersion().toString();
        }
        return "unknown";
    }


    public static boolean RPGLeveling(){
        // add conf support
        return true;
    }


}
