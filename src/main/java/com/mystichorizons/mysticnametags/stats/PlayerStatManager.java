package com.mystichorizons.mysticnametags.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.integrations.StatProvider;
import com.mystichorizons.mysticnametags.tags.StorageBackend;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central stat system for MysticNameTags.
 *
 * - Persists per-player stats using PlayerStatStore (FILE / SQLITE / MYSQL).
 * - Implements StatProvider for tag requirements.
 * - Exposes helpers for kills/blocks/distance/damage/etc.
 * - Tracks lightweight session-only stats in memory.
 *
 * Keys:
 *   Public API exposed to TagManager uses "category.statKey":
 *     "custom.damage_dealt"
 *     "custom.damage_taken"
 *     "custom.player_kills"
 *     "mined.hytale:stone"
 *     "killed.Player"
 */
public final class PlayerStatManager implements StatProvider {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(PlayerStatsData.class, new SparseStatsSerializer())
            .create();

    private static volatile PlayerStatManager INSTANCE;

    /**
     * Initialize and register as StatProvider with the integration manager.
     * Call this ONCE from your plugin bootstrap, after IntegrationManager exists.
     */
    public static void init(@Nonnull IntegrationManager integrations) {
        if (INSTANCE == null) {
            synchronized (PlayerStatManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PlayerStatManager();
                    integrations.setStatProvider(INSTANCE);
                    LOGGER.at(Level.INFO)
                            .log("[MysticNameTags] PlayerStatManager wired into IntegrationManager as StatProvider.");
                }
            }
        }
    }

    /**
     * @return the global PlayerStatManager instance, or null if not initialized.
     */
    @Nullable
    public static PlayerStatManager get() {
        return INSTANCE;
    }

    /**
     * Optional: called from plugin shutdown to flush and drop references.
     */
    public static void shutdownGlobal() {
        PlayerStatManager mgr = INSTANCE;
        if (mgr != null) {
            try {
                mgr.shutdown();
            } catch (Throwable ignored) {
            } finally {
                INSTANCE = null;
            }
        }
    }

    // --------------------------------------------------

    private final PlayerStatStore store;

    // Persistent stats cache (loaded from backend)
    private final Map<UUID, PlayerStatsData> cache = new ConcurrentHashMap<>();

    // Session-only, in-memory stats (for live placeholders)
    private final Map<UUID, PlayerStatsData> sessionStats = new ConcurrentHashMap<>();

    private PlayerStatManager() {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("MysticNameTagsPlugin instance is null during PlayerStatManager init");
        }

        File dataFolder = plugin.getDataDirectory().toFile();
        File statsFolder = new File(dataFolder, "stats");
        //noinspection ResultOfMethodCallIgnored
        statsFolder.mkdirs();

        Settings settings = Settings.get();
        StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());

        PlayerStatStore chosen;

        switch (backend) {
            case SQLITE: {
                File sqliteFile = new File(dataFolder, settings.getSqliteFile());
                String jdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
                chosen = new SqlPlayerStatStore(jdbcUrl, "", "", GSON);
                break;
            }

            case MYSQL: {
                String host = settings.getMysqlHost();
                int    port = settings.getMysqlPort();
                String db   = settings.getMysqlDatabase();
                String user = settings.getMysqlUser();
                String pass = settings.getMysqlPassword();

                String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db +
                        "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8";

                chosen = new SqlPlayerStatStore(jdbcUrl, user, pass, GSON);
                break;
            }

            case FILE:
            default: {
                chosen = new FilePlayerStatStore(statsFolder, GSON);
                break;
            }
        }

        this.store = chosen;
        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] PlayerStatManager initialized using backend: " + backend);
    }

    /**
     * Flush all cached stats to the underlying store.
     */
    private void shutdown() {
        LOGGER.at(Level.INFO).log("[MysticNameTags] Flushing PlayerStatManager cache on shutdown...");
        for (UUID uuid : cache.keySet()) {
            try {
                save(uuid);
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Failed to save stats for " + uuid + " during shutdown.");
            }
        }
        cache.clear();
        sessionStats.clear();
    }

    // --------------------------------------------------
    // Internal helpers
    // --------------------------------------------------

    @Nonnull
    private PlayerStatsData getOrLoad(@Nonnull UUID uuid) {
        return cache.computeIfAbsent(uuid, store::load);
    }

    private void save(@Nonnull UUID uuid) {
        PlayerStatsData data = cache.get(uuid);
        if (data == null) return;
        store.save(uuid, data);
    }

    @Nonnull
    private PlayerStatsData getSession(@Nonnull UUID uuid) {
        return sessionStats.computeIfAbsent(uuid, u -> new PlayerStatsData());
    }

    // --------------------------------------------------
    // Lifecycle hooks (call from your player listener)
    // --------------------------------------------------

    /**
     * Player joined: reset session-only stats.
     */
    public void onPlayerJoin(@Nonnull UUID uuid) {
        sessionStats.put(uuid, new PlayerStatsData());
        // Lazy load: actual persistent data will be loaded on first get/add.
    }

    /**
     * Player quit: flush stats and clear session cache.
     */
    public void onPlayerQuit(@Nonnull UUID uuid) {
        sessionStats.remove(uuid);
        save(uuid);
    }

    // --------------------------------------------------
    // StatProvider implementation (for TagManager via IntegrationManager)
    // --------------------------------------------------

    /**
     * Tag requirements use a single string key – we interpret it as:
     *   "category.statKey"
     *
     * Examples:
     *   "custom.damage_dealt"
     *   "custom.damage_taken"
     *   "custom.player_kills"
     *
     * If no '.' is present, category "custom" is assumed.
     */
    @Override
    public @Nullable Integer getStatValue(@Nonnull UUID uuid, @Nonnull String key) {
        long value = getStatLong(uuid, key);
        if (value <= 0L) {
            return null;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    // --------------------------------------------------
    // Public API – generic
    // --------------------------------------------------

    /**
     * Raw long value for a "category.statKey" string.
     */
    public long getStatLong(@Nonnull UUID uuid, @Nonnull String key) {
        ParsedKey parsed = parseKey(key);
        return getOrLoad(uuid).get(parsed.category, parsed.stat);
    }

    /**
     * Add delta (can be negative) to "category.statKey".
     */
    public long addToStat(@Nonnull UUID uuid, @Nonnull String key, long delta) {
        if (delta == 0L) {
            return getStatLong(uuid, key);
        }
        ParsedKey parsed = parseKey(key);

        PlayerStatsData data = getOrLoad(uuid);
        data.increment(parsed.category, parsed.stat, delta);
        save(uuid);

        return data.get(parsed.category, parsed.stat);
    }

    // --------------------------------------------------
    // Convenience API – category-aware helpers
    // --------------------------------------------------

    public long incrementEntityKill(@Nonnull UUID uuid, @Nonnull String entityId) {
        // total kills
        addToStat(uuid, "custom.kills_total", 1L);
        // per-entity category
        addToStat(uuid, "killed." + entityId, 1L);
        return getStatLong(uuid, "killed." + entityId);
    }

    public long incrementDeath(@Nonnull UUID uuid) {
        addToStat(uuid, "custom.deaths_total", 1L);
        return getStatLong(uuid, "custom.deaths_total");
    }

    public long incrementBlockBroken(@Nonnull UUID uuid, @Nonnull String blockId) {
        addToStat(uuid, "custom.blocks_broken_total", 1L);
        addToStat(uuid, "mined." + blockId, 1L);
        getSession(uuid).increment("session", "blocks_broken_total", 1L);
        return getStatLong(uuid, "mined." + blockId);
    }

    public long incrementBlockPlaced(@Nonnull UUID uuid, @Nonnull String blockId) {
        addToStat(uuid, "custom.blocks_placed_total", 1L);
        addToStat(uuid, "placed." + blockId, 1L);
        getSession(uuid).increment("session", "blocks_placed_total", 1L);
        return getStatLong(uuid, "placed." + blockId);
    }

    public void addDamageDealt(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) return;
        long delta = Math.round(amount);
        addToStat(uuid, "custom.damage_dealt", delta);
        getSession(uuid).increment("session", "damage_dealt", delta);
    }

    public void addDamageTaken(@Nonnull UUID uuid, double amount) {
        if (amount <= 0.0D) return;
        long delta = Math.round(amount);
        addToStat(uuid, "custom.damage_taken", delta);
        getSession(uuid).increment("session", "damage_taken", delta);
    }

    // Session getters – for placeholders / live displays
    public long getSessionStatLong(@Nonnull UUID uuid, @Nonnull String key) {
        ParsedKey parsed = parseKey(key);
        return getSession(uuid).get(parsed.category, parsed.stat);
    }

    // --------------------------------------------------
    // Admin helpers
    // --------------------------------------------------

    public void adminSetStat(@Nonnull UUID uuid,
                             @Nonnull String key,
                             long value) {
        ParsedKey parsed = parseKey(key);
        PlayerStatsData data = getOrLoad(uuid);
        data.increment(parsed.category, parsed.stat, value - data.get(parsed.category, parsed.stat));
        save(uuid);
    }

    public long adminAddStat(@Nonnull UUID uuid,
                             @Nonnull String key,
                             long delta) {
        return addToStat(uuid, key, delta);
    }

    public void adminResetAll(@Nonnull UUID uuid) {
        cache.remove(uuid);
        sessionStats.remove(uuid);
        try {
            store.delete(uuid);
        } catch (Throwable ignored) {
        }
    }

    // --------------------------------------------------
    // Key parsing helper
    // --------------------------------------------------

    private record ParsedKey(String category, String stat) {}

    private ParsedKey parseKey(@Nonnull String key) {
        String trimmed = key.trim();
        int dot = trimmed.indexOf('.');
        if (dot <= 0 || dot == trimmed.length() - 1) {
            // No dot or malformed -> treat entire key as stat in "custom" category
            return new ParsedKey("custom", trimmed);
        }
        String cat = trimmed.substring(0, dot);
        String stat = trimmed.substring(dot + 1);
        return new ParsedKey(cat, stat);
    }
}