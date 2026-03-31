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
import java.util.LinkedHashMap;
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
 *
 * NOTE: Playtime is now tracked separately by PlaytimeService
 *       using the stat key "custom.playtime_seconds".
 */
public final class PlayerStatManager implements StatProvider {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(PlayerStatsData.class, new SparseStatsSerializer())
            .create();

    private static volatile PlayerStatManager INSTANCE;

    // Persistent stats cache (loaded from backend)
    private final Map<UUID, PlayerStatsData> cache = new ConcurrentHashMap<>();

    // Session-only, in-memory stats (for live placeholders)
    private final Map<UUID, PlayerStatsData> sessionStats = new ConcurrentHashMap<>();

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
                int port = settings.getMysqlPort();
                String db = settings.getMysqlDatabase();
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
        return cache.computeIfAbsent(uuid, this::loadAndNormalize);
    }

    @Nonnull
    private PlayerStatsData loadAndNormalize(@Nonnull UUID uuid) {
        PlayerStatsData data = store.load(uuid);

        try {
            boolean changed = normalizeLoadedBlockCategories(data);
            if (changed) {
                store.save(uuid, data);
            }
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to normalize loaded block stats for " + uuid);
        }

        return data;
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
     *
     * NOTE: Playtime is NOT tracked here anymore; that is handled by PlaytimeService.
     */
    public void onPlayerJoin(@Nonnull UUID uuid) {
        sessionStats.put(uuid, new PlayerStatsData());
        // Lazy load: actual persistent data will be loaded on first get/add.
    }

    /**
     * Player quit: drop session-only stats and persist cached data.
     *
     * Playtime accumulation is handled externally by PlaytimeService.
     */
    public void onPlayerQuit(@Nonnull UUID uuid) {
        sessionStats.remove(uuid);
        save(uuid);
    }

    // --------------------------------------------------
    // StatProvider implementation (for TagManager via IntegrationManager)
    // --------------------------------------------------

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

    @Override
    public @Nullable Integer getStatValuePattern(@Nonnull UUID uuid, @Nonnull String keyPattern) {
        long total = getStatLong(uuid, keyPattern);

        if (total <= 0L) {
            return null;
        }
        if (total > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) total;
    }

    // --------------------------------------------------
    // Public API – generic
    // --------------------------------------------------

    /**
     * Raw long value for a "category.statKey" string.
     */
    public long getStatLong(@Nonnull UUID uuid, @Nonnull String key) {
        PlayerStatsData data = getOrLoad(uuid);

        if (key.indexOf('*') >= 0) {
            return sumMatchingStats(data, key);
        }

        ParsedKey parsed = parseKey(key);

        long direct = data.get(parsed.category, parsed.stat);
        if (direct > 0L) {
            return direct;
        }

        if (isBlockCategory(parsed.category)) {
            return getAliasedBlockStat(data, parsed.category, parsed.stat);
        }

        return 0L;
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
        addToStat(uuid, "custom.kills_total", 1L);
        addToStat(uuid, "killed." + entityId, 1L);
        return getStatLong(uuid, "killed." + entityId);
    }

    public long incrementDeath(@Nonnull UUID uuid) {
        addToStat(uuid, "custom.deaths_total", 1L);
        return getStatLong(uuid, "custom.deaths_total");
    }

    public long incrementBlockBroken(@Nonnull UUID uuid, @Nonnull String blockId) {
        String normalized = normalizeBlockId(blockId);
        String bare = stripNamespace(normalized);

        addToStat(uuid, "custom.blocks_broken_total", 1L);

        // Canonical
        addToStat(uuid, "mined." + normalized, 1L);

        // Legacy alias compatibility
        if (!bare.equals(normalized)) {
            addToStat(uuid, "mined." + bare, 1L);
        }

        getSession(uuid).increment("session", "blocks_broken_total", 1L);
        return getStatLong(uuid, "mined." + normalized);
    }

    public long incrementBlockPlaced(@Nonnull UUID uuid, @Nonnull String blockId) {
        String normalized = normalizeBlockId(blockId);
        String bare = stripNamespace(normalized);

        addToStat(uuid, "custom.blocks_placed_total", 1L);

        // Canonical
        addToStat(uuid, "placed." + normalized, 1L);

        // Legacy alias compatibility
        if (!bare.equals(normalized)) {
            addToStat(uuid, "placed." + bare, 1L);
        }

        getSession(uuid).increment("session", "blocks_placed_total", 1L);
        return getStatLong(uuid, "placed." + normalized);
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
            return new ParsedKey("custom", trimmed);
        }
        String cat = trimmed.substring(0, dot);
        String stat = trimmed.substring(dot + 1);
        return new ParsedKey(cat, stat);
    }

    private long sumMatchingStats(@Nonnull PlayerStatsData data, @Nonnull String pattern) {
        ParsedKey parsed = parseKey(pattern);

        if (isBlockCategory(parsed.category)) {
            return sumMatchingBlockStats(data, parsed.category, parsed.stat);
        }

        String regex = wildcardToRegex(pattern);
        long total = 0L;

        for (Map.Entry<String, Map<String, Long>> catEntry : data.viewAll().entrySet()) {
            String category = catEntry.getKey();
            Map<String, Long> stats = catEntry.getValue();
            if (stats == null || stats.isEmpty()) continue;

            for (Map.Entry<String, Long> statEntry : stats.entrySet()) {
                String fullKey = category + "." + statEntry.getKey();
                if (fullKey.matches(regex)) {
                    Long value = statEntry.getValue();
                    if (value != null && value > 0L) {
                        total += value;
                    }
                }
            }
        }

        return total;
    }

    private long sumMatchingBlockStats(@Nonnull PlayerStatsData data,
                                       @Nonnull String category,
                                       @Nonnull String statPattern) {
        Map<String, Long> categoryStats = data.getCategory(category);
        if (categoryStats.isEmpty()) {
            return 0L;
        }

        String normalizedPattern = normalizeBlockPattern(statPattern);
        String barePattern = stripNamespacePattern(normalizedPattern);

        String normalizedRegex = wildcardToRegex(normalizedPattern);
        String bareRegex = wildcardToRegex(barePattern);

        long total = 0L;
        Map<String, Long> deduped = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : categoryStats.entrySet()) {
            String rawKey = entry.getKey();
            Long value = entry.getValue();

            if (rawKey == null || rawKey.isBlank() || value == null || value <= 0L) {
                continue;
            }

            String normalizedKey = normalizeBlockId(rawKey);
            String bareKey = stripNamespace(normalizedKey);

            boolean matches =
                    normalizedKey.matches(normalizedRegex) ||
                            bareKey.matches(bareRegex) ||
                            rawKey.matches(normalizedRegex) ||
                            rawKey.matches(bareRegex);

            if (!matches) {
                continue;
            }

            String dedupeKey = category + "." + normalizedKey;
            deduped.merge(dedupeKey, value, Math::max);
        }

        for (Long value : deduped.values()) {
            if (value != null && value > 0L) {
                total += value;
            }
        }

        return total;
    }

    private long getAliasedBlockStat(@Nonnull PlayerStatsData data,
                                     @Nonnull String category,
                                     @Nonnull String stat) {
        String normalized = normalizeBlockId(stat);
        String bare = stripNamespace(normalized);

        long directNormalized = data.get(category, normalized);
        if (directNormalized > 0L) {
            return directNormalized;
        }

        long directBare = data.get(category, bare);
        if (directBare > 0L) {
            return directBare;
        }

        return 0L;
    }

    private boolean isBlockCategory(@Nonnull String category) {
        return "mined".equalsIgnoreCase(category) || "placed".equalsIgnoreCase(category);
    }

    /**
     * Normalizes loaded stats so canonical namespaced block keys exist.
     * Keeps legacy bare keys for compatibility.
     */
    private boolean normalizeLoadedBlockCategories(@Nonnull PlayerStatsData data) {
        boolean changed = false;

        changed |= normalizeLoadedBlockCategory(data, "mined");
        changed |= normalizeLoadedBlockCategory(data, "placed");

        return changed;
    }

    private boolean normalizeLoadedBlockCategory(@Nonnull PlayerStatsData data,
                                                 @Nonnull String category) {
        Map<String, Long> existing = data.getCategory(category);
        if (existing.isEmpty()) {
            return false;
        }

        boolean changed = false;
        Map<String, Long> canonicalAdds = new LinkedHashMap<>();

        for (Map.Entry<String, Long> entry : existing.entrySet()) {
            String rawKey = entry.getKey();
            Long value = entry.getValue();

            if (rawKey == null || rawKey.isBlank() || value == null || value <= 0L) {
                continue;
            }

            String normalized = normalizeBlockId(rawKey);
            if (!normalized.equals(rawKey)) {
                canonicalAdds.merge(normalized, value, Math::max);
            }
        }

        for (Map.Entry<String, Long> add : canonicalAdds.entrySet()) {
            String stat = add.getKey();
            long value = add.getValue() == null ? 0L : add.getValue();

            if (value <= 0L) {
                continue;
            }

            long current = data.get(category, stat);
            if (current < value) {
                data.increment(category, stat, value - current);
                changed = true;
            }
        }

        return changed;
    }

    @Nonnull
    private String normalizeBlockId(@Nonnull String blockId) {
        String key = blockId.trim().toLowerCase();

        if (key.isEmpty()) {
            return "unknown";
        }

        if (key.contains(":")) {
            return key;
        }

        return "hytale:" + key;
    }

    @Nonnull
    private String normalizeBlockPattern(@Nonnull String statPattern) {
        String key = statPattern.trim().toLowerCase();

        if (key.isEmpty()) {
            return "*";
        }

        if (key.contains(":")) {
            return key;
        }

        return "hytale:" + key;
    }

    @Nonnull
    private String stripNamespace(@Nonnull String key) {
        int idx = key.indexOf(':');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    @Nonnull
    private String stripNamespacePattern(@Nonnull String key) {
        int idx = key.indexOf(':');
        return idx >= 0 ? key.substring(idx + 1) : key;
    }

    private String wildcardToRegex(@Nonnull String pattern) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '.':
                    sb.append("\\.");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '+':
                case '?':
                case '^':
                case '$':
                case '|':
                    sb.append("\\").append(c);
                    break;
                default:
                    sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }
}