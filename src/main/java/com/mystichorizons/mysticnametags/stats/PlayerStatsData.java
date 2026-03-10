package com.mystichorizons.mysticnametags.stats;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player statistics container.
 *
 * Structure:
 *   category -> (statKey -> value)
 *
 * Examples:
 *   category = "custom",  stat = "damage_dealt"
 *   category = "mined",   stat = "hytale:stone"
 *   category = "killed",  stat = "Player"
 *
 * Keys are free-form strings; Tag requirements should use "category.stat"
 * notation, e.g. "custom.damage_dealt".
 */
public final class PlayerStatsData {

    private static final int CURRENT_DATA_VERSION = 2;

    private final Map<String, Map<String, Long>> stats = new ConcurrentHashMap<>();
    private int dataVersion = CURRENT_DATA_VERSION;

    public PlayerStatsData() {
    }

    // --------------------------------------------------
    // Version
    // --------------------------------------------------

    public int getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(int dataVersion) {
        this.dataVersion = dataVersion;
    }

    // --------------------------------------------------
    // Core API
    // --------------------------------------------------

    public synchronized void increment(@Nonnull String category,
                                       @Nonnull String stat,
                                       long amount) {
        if (amount == 0L) {
            return;
        }

        Map<String, Long> categoryStats =
                stats.computeIfAbsent(category, k -> new ConcurrentHashMap<>());

        categoryStats.merge(stat, amount, Long::sum);

        // Remove zeros to keep data sparse
        Long newValue = categoryStats.get(stat);
        if (newValue != null && newValue == 0L) {
            categoryStats.remove(stat);
        }
        if (categoryStats.isEmpty()) {
            stats.remove(category);
        }
    }

    public long get(@Nonnull String category, @Nonnull String stat) {
        Map<String, Long> categoryStats = stats.get(category);
        if (categoryStats == null) {
            return 0L;
        }
        return categoryStats.getOrDefault(stat, 0L);
    }

    @Nonnull
    public Map<String, Long> getCategory(@Nonnull String category) {
        Map<String, Long> categoryStats = stats.get(category);
        if (categoryStats == null || categoryStats.isEmpty()) {
            return Collections.emptyMap();
        }
        return Map.copyOf(categoryStats);
    }

    /**
     * Read-only view of all stats.
     * Useful for serializers and general inspection.
     */
    @Nonnull
    public Map<String, Map<String, Long>> getAll() {
        Map<String, Map<String, Long>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Long>> entry : stats.entrySet()) {
            Map<String, Long> inner = entry.getValue();
            if (inner == null || inner.isEmpty()) {
                continue;
            }
            copy.put(entry.getKey(), Map.copyOf(inner));
        }
        return Map.copyOf(copy);
    }

    /**
     * Read-only snapshot used by wildcard/stat-pattern matching.
     */
    @Nonnull
    public Map<String, Map<String, Long>> viewAll() {
        return getAll();
    }

    public boolean hasCategory(@Nonnull String category) {
        Map<String, Long> categoryStats = stats.get(category);
        return categoryStats != null && !categoryStats.isEmpty();
    }

    // --------------------------------------------------
    // Serialization helpers (used by Gson adapter)
    // --------------------------------------------------

    void setStats(@Nonnull Map<String, Map<String, Long>> loadedStats) {
        stats.clear();

        for (Map.Entry<String, Map<String, Long>> entry : loadedStats.entrySet()) {
            String category = entry.getKey();
            Map<String, Long> categoryStats = entry.getValue();

            if (category == null || category.isBlank() || categoryStats == null || categoryStats.isEmpty()) {
                continue;
            }

            Map<String, Long> cleaned = new ConcurrentHashMap<>();
            for (Map.Entry<String, Long> statEntry : categoryStats.entrySet()) {
                String statKey = statEntry.getKey();
                Long value = statEntry.getValue();

                if (statKey == null || statKey.isBlank() || value == null || value == 0L) {
                    continue;
                }

                cleaned.put(statKey, value);
            }

            if (!cleaned.isEmpty()) {
                stats.put(category, cleaned);
            }
        }
    }
}