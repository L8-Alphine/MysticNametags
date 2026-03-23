package com.mystichorizons.mysticnametags;

import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.mystichorizons.mysticnametags.commands.MysticNameTagsPluginCommand;
import com.mystichorizons.mysticnametags.commands.TagsAdminCommand;
import com.mystichorizons.mysticnametags.commands.TagsCommand;
import com.mystichorizons.mysticnametags.commands.TagsOwnedCommand;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.hstats.HStats;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.integrations.endlessleveling.EndlessLevelingCompat;
import com.mystichorizons.mysticnametags.integrations.endlessleveling.EndlessLevelingNameplateSystem;
import com.mystichorizons.mysticnametags.listeners.PlayerListener;
import com.mystichorizons.mysticnametags.nameplate.*;
import com.mystichorizons.mysticnametags.placeholders.HelpchPlaceholderHook;
import com.mystichorizons.mysticnametags.placeholders.WiFlowPlaceholderHook;
import com.mystichorizons.mysticnametags.playtime.PlaytimeService;
import com.mystichorizons.mysticnametags.stats.PlayerStatManager;
import com.mystichorizons.mysticnametags.stats.systems.BlockBreakStatSystem;
import com.mystichorizons.mysticnametags.stats.systems.BlockPlaceStatSystem;
import com.mystichorizons.mysticnametags.stats.systems.DamageStatSystem;
import com.mystichorizons.mysticnametags.stats.systems.DeathStatSystem;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.MysticLog;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

import javax.annotation.Nonnull;
import java.util.Objects;
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
    private ScheduledExecutorService glyphScheduler;

    private IntegrationManager integrations;
    private UpdateChecker updateChecker;
    private PluginManifest manifest;

    private PlaytimeService playtimeService;

    private volatile boolean endlessLevelingSystemRegistered = false;

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

        // Start HStats
        new HStats("b2740b4b-b730-4693-9ec4-e39a1ac5b661", version);

        // ------------------------------------------------------
        // Playtime service (60s interval; adjust if you add config)
        // ------------------------------------------------------
        this.playtimeService = new PlaytimeService(60L);

        // ------------------------------------------------------
        // Integrations (backed by playtimeService)
        // ------------------------------------------------------
        this.integrations = new IntegrationManager(this.playtimeService);

        // ------------------------------------------------------
        // Core config + language
        // ------------------------------------------------------
        Settings.init();
        LanguageManager.init();

        // ------------------------------------------------------
        // Tags + ECS systems + commands + listeners
        // ------------------------------------------------------
        TagManager.init(integrations);

        // Register commands
        registerCommands();

        // Register event listeners
        registerListeners();

        // Register ECS systems (playtime + block/damage/death)
        registerEcsSystems();

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

            getCommandRegistry().registerCommand(new TagsOwnedCommand());
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered /tagsowned command");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING)
                    .withCause(e)
                    .log("[MysticNameTags] Failed to register commands");
        }
    }

    private void registerListeners() {
        EventRegistry eventBus = getEventRegistry();

        try {
            new PlayerListener(playtimeService).register(eventBus);
            LOGGER.at(Level.INFO).log("[MysticNameTags] Registered player event listeners");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING)
                    .withCause(e)
                    .log("[MysticNameTags] Failed to register listeners");
        }
    }

    private void registerEcsSystems() {
        try {
            var ecs = getEntityStoreRegistry();

            // Block / damage / death stat systems
            ecs.registerSystem(new BlockBreakStatSystem());
            ecs.registerSystem(new BlockPlaceStatSystem());
            ecs.registerSystem(new DamageStatSystem());
            ecs.registerSystem(new DeathStatSystem());

            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Registered ECS stat systems (break/place/damage/death).");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING)
                    .withCause(e)
                    .log("[MysticNameTags] Failed to register ECS stat systems");
        }
    }

    @Override
    protected void start() {
        this.integrations.init();

        MysticLog.init(this);

        if (playtimeService != null) {
            playtimeService.start();
        }

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

        // Glyph nameplate follow refresher
        startGlyphFollowSchedulerIfNeeded();

        // EcoQuests Nameplate
        tryRegisterEcoQuestsIntegration();

        // Endless Leveling Register
        tryRegisterEndlessLevelingNameplates();

        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MysticNameTags-EndlessRetry");
            t.setDaemon(true);
            return t;
        }).schedule(() -> {
            try {
                tryRegisterEndlessLevelingNameplates();
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] EndlessLeveling retry registration failed.");
            }
        }, 3, TimeUnit.SECONDS);
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Shutting down...");

        try {
            stopLevelScheduler();
        } catch (Throwable ignored) {
            LOGGER.at(Level.WARNING).log("[MysticNameTags] Failed to stop level scheduler");
        }
        try {
            stopGlyphFollowScheduler();
        } catch (Throwable ignored) {
            LOGGER.at(Level.WARNING).log("[MysticNameTags] Failed to stop glyph follow scheduler");
        }
        try {
            if (playtimeService != null) {
                playtimeService.shutdown();
            }
        } catch (Throwable ignored) {
            LOGGER.at(Level.WARNING).log("[MysticNameTags] Failed to stop PlaytimeService");
        }
        try {
            PlayerStatManager.shutdownGlobal();
        } catch (Throwable ignored) {
            LOGGER.at(Level.WARNING).log("[MysticNameTags] Failed to stop PlayerStatManager");
        }
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


    public static boolean isEcoQuestsAvailable() {
        try {
            return com.mystichorizons.mysticnametags.integrations.ecoquests.EcoQuestsCompat.isAvailable();
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
     * <p>
     * This is intended to be called from /tags reload.
     */
    public void reloadAll() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Reloading settings, integrations, and tags...");

        // 1) Reload settings.json + language
        Settings.init();
        LanguageManager.get().reload();

        // 3) Re-run integrations init (permissions, prefixes, economies, etc.)
        try {
            this.integrations.init();
            LOGGER.at(Level.INFO).log("[MysticNameTags] Integrations re-initialized after reload.");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to re-initialize integrations during reload.");
        }

        // 4) Reload tags.json and refresh all online nameplates
        TagManager.reload();

        // 5) Restart RPGLeveling scheduler based on *current* settings
        stopLevelScheduler();
        startLevelSchedulerIfNeeded();
        stopGlyphFollowScheduler();
        startGlyphFollowSchedulerIfNeeded();

        LOGGER.at(Level.INFO).log("[MysticNameTags] Reload complete.");
    }

    private void tryRegisterEndlessLevelingNameplates() {
        if (endlessLevelingSystemRegistered) {
            return;
        }

        if (!Settings.get().isEndlessLevelingNameplatesEnabled()) {
            LOGGER.at(Level.INFO).log("[MysticNameTags] EndlessLeveling nameplates disabled in settings.");
            return;
        }

        try {
            if (!EndlessLevelingCompat.isAvailable()) {
                LOGGER.at(Level.INFO).log("[MysticNameTags] EndlessLeveling API not detected.");
                return;
            }

            EndlessLevelingNameplateSystem system =
                    new EndlessLevelingNameplateSystem(TagManager.get());

            this.getEntityStoreRegistry().registerSystem(system);
            this.integrations.setEndlessNameplateSystem(system);
            this.endlessLevelingSystemRegistered = true;

            LOGGER.at(Level.INFO).log("[MysticNameTags] EndlessLeveling integration enabled via public API: overriding player nameplates.");

        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to register EndlessLeveling integration.");
        }
    }

    private void tryRegisterEcoQuestsIntegration() {
        try {
            if (!com.mystichorizons.mysticnametags.integrations.ecoquests.EcoQuestsCompat.isAvailable()) {
                LOGGER.at(Level.INFO).log("[MysticNameTags] EcoQuests not detected; skipping Adventurer Rank integration.");
                return;
            }

            LOGGER.at(Level.INFO).log("[MysticNameTags] EcoQuests integration enabled: Adventurer Rank token available.");
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to initialize EcoQuests integration.");
        }
    }

    private void startGlyphFollowSchedulerIfNeeded() {
        if (glyphScheduler != null && !glyphScheduler.isShutdown()) {
            return;
        }

        int ticks = Settings.get().getExperimentalGlyphUpdateTicks();
        long periodNanos = Math.max(1L, ticks) * 50_000_000L;

        glyphScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MysticNameTags-GlyphFollow");
            t.setDaemon(true);
            return t;
        });

        glyphScheduler.scheduleAtFixedRate(
                new com.mystichorizons.mysticnametags.nameplate.GlyphNameplateFollowTask(),
                periodNanos,
                periodNanos,
                TimeUnit.NANOSECONDS
        );

        LOGGER.at(Level.INFO).log(
                "[MysticNameTags] Started glyph follow scheduler (every " + periodNanos + "ns)."
        );
    }

    private void stopGlyphFollowScheduler() {
        if (glyphScheduler != null) {
            try { glyphScheduler.shutdownNow(); } catch (Throwable ignored) {}
            glyphScheduler = null;
            LOGGER.at(Level.INFO).log("[MysticNameTags] Stopped glyph follow scheduler.");
        }
    }
}