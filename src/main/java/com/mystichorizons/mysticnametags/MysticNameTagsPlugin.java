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
import com.mystichorizons.mysticnametags.nameplate.LevelNameplateRefreshTask;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;
import com.mystichorizons.mysticnametags.placeholders.HelpchPlaceholderHook;
import com.mystichorizons.mysticnametags.placeholders.WiFlowPlaceholderHook;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.MysticLog;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

import javax.annotation.Nonnull;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * MysticNameTags - A Hytale server plugin.
 */
public class MysticNameTagsPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static MysticNameTagsPlugin instance;

    private ScheduledExecutorService levelScheduler;

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
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered /mntags command");

            getCommandRegistry().registerCommand(new TagsCommand());
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered /tags command");

            getCommandRegistry().registerCommand(new TagsAdminCommand());
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered /tagsadmin command");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING)
                    .withCause(e)
                    .log("[MysticNameTags] Failed to register commands");
        }
    }

    private void registerListeners() {
        EventRegistry eventBus = getEventRegistry();

        try {
            new PlayerListener().register(eventBus);
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered player event listeners");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING)
                    .withCause(e)
                    .log("[MysticNameTags] Failed to register listeners");
        }
    }

    @Override
    protected void start() {
        this.integrations.init();

        tryRegisterEndlessLevelingNameplates();

        MysticLog.init(this);

        LOGGER.at(Level.INFO).log("[MysticNameTags] Started!");
        LOGGER.at(Level.INFO).log("[MysticNameTags] Use /tags help for commands");

        // at.helpch PlaceholderAPI
        try {
            new HelpchPlaceholderHook().register();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Failed to register at.helpch PlaceholderAPI expansion. "
                            + "Maybe not installed? Disabled helpch placeholder support.");
        }

        // WiFlowPlaceholderAPI
        try {
            new WiFlowPlaceholderHook().register();
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Failed to register WiFlowPlaceholderAPI expansion. "
                            + "Maybe not installed? Disabled Placeholder Support.");
        }

        // Debug: print detected economy backends
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

        // RPGLeveling nameplate refresher (lazy-guarded by config + API checks)
        startLevelSchedulerIfNeeded();
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Shutting down...");

        try {
            stopLevelScheduler();
        } catch (Throwable ignored) {}

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

    // ------------------------------------------------------
    // RPGLeveling availability helper
    // ------------------------------------------------------

    public static boolean isRpgLevelingAvailable() {
        try {
            if (!Settings.get().isRpgLevelingNameplatesEnabled()) {
                return false;
            }

            // Safe probe of the API
            org.zuxaw.plugin.api.RPGLevelingAPI api = org.zuxaw.plugin.api.RPGLevelingAPI.get();
            return api != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------
    // Level scheduler control (startup + reload + shutdown)
    // ------------------------------------------------------

    private void startLevelSchedulerIfNeeded() {
        // Only schedule if feature is enabled in config
        if (!Settings.get().isRpgLevelingNameplatesEnabled()) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] RPGLeveling nameplates disabled in settings; not starting scheduler.");
            return;
        }

        int intervalSec = Settings.get().getRpgLevelingRefreshSeconds();

        // Avoid double-scheduling if something calls this twice
        if (levelScheduler != null && !levelScheduler.isShutdown()) {
            LOGGER.at(Level.FINE)
                    .log("[MysticNameTags] Level scheduler already running; skipping restart.");
            return;
        }

        levelScheduler = Executors.newSingleThreadScheduledExecutor(
                r -> {
                    Thread t = new Thread(r, "MysticNameTags-LevelRefresher");
                    t.setDaemon(true);
                    return t;
                });

        levelScheduler.scheduleAtFixedRate(
                new LevelNameplateRefreshTask(),
                intervalSec,
                intervalSec,
                TimeUnit.SECONDS
        );

        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] Started RPGLeveling nameplate scheduler (interval=" + intervalSec + "s).");
    }

    private void stopLevelScheduler() {
        if (levelScheduler != null) {
            try {
                levelScheduler.shutdownNow();
            } catch (Throwable ignored) {
            } finally {
                levelScheduler = null;
            }
            LOGGER.at(Level.INFO).log("[MysticNameTags] Stopped RPGLeveling nameplate scheduler.");
        }
    }

    // ------------------------------------------------------
    // Reload entrypoint used by /tags reload
    // ------------------------------------------------------

    /**
     * Reloads settings, integrations, tags, and restarts the
     * RPGLeveling scheduler using the latest configuration.
     *
     * This is intended to be called from /tags reload.
     */
    /**
     * Reloads settings, tags, and restarts the RPGLeveling scheduler
     * using the latest configuration.
     *
     * Intended to be called from /tags reload.
     */
    public void reloadAll() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Reloading settings, integrations, and tags...");

        // 1) Reload settings.json
        Settings.init();

        // 2) Re-run integration detection (permissions, prefixes, economy)
        //    This allows the plugin to hook into newly-installed plugins
        //    like LuckPerms / EcoTale / PrefixesPlus without a full restart.
        try {
            this.integrations.init();
            LOGGER.at(Level.INFO).log("[MysticNameTags] Integrations re-initialized after reload.");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to re-initialize integrations during reload.");
        }

        // 3) Reload tags.json and refresh all online nameplates
        TagManager.reload();

        // 4) Restart RPGLeveling scheduler based on *current* settings
        stopLevelScheduler();
        startLevelSchedulerIfNeeded();

        LOGGER.at(Level.INFO).log("[MysticNameTags] Reload complete.");
    }

    private void tryRegisterEndlessLevelingNameplates() {
        if (!Settings.get().isEndlessLevelingNameplatesEnabled()) {
            LOGGER.at(Level.INFO).log("[MysticNameTags] EndlessLeveling nameplates disabled in settings.");
            return;
        }

        try {
            // Ensure class exists
            Class.forName("com.airijko.endlessleveling.EndlessLeveling");

            com.airijko.endlessleveling.EndlessLeveling el = com.airijko.endlessleveling.EndlessLeveling.getInstance();
            if (el == null) {
                LOGGER.at(Level.INFO).log("[MysticNameTags] EndlessLeveling detected but instance is null; skipping integration.");
                return;
            }

            var pdm = el.getPlayerDataManager();
            if (pdm == null) {
                LOGGER.at(Level.INFO).log("[MysticNameTags] EndlessLeveling detected but PlayerDataManager is null; skipping integration.");
                return;
            }

            // Register OUR system so it runs (likely) after theirs and overwrites their label
            this.getEntityStoreRegistry().registerSystem(
                    new com.mystichorizons.mysticnametags.integrations.endlessleveling.EndlessLevelingNameplateSystem(
                            pdm,
                            TagManager.get()
                    )
            );

            LOGGER.at(Level.INFO).log("[MysticNameTags] EndlessLeveling integration enabled: overriding player nameplates.");

        } catch (ClassNotFoundException ignored) {
            // Not installed
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to register EndlessLeveling integration.");
        }
    }
}
