package com.mystichorizons.mysticnametags.stats;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
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

    private static final int CURRENT_DATA_VERSION = 1;

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
        if (categoryStats.get(stat) != null && categoryStats.get(stat) == 0L) {
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
        return Collections.unmodifiableMap(new HashMap<>(categoryStats));
    }

    @Nonnull
    public Map<String, Map<String, Long>> getAll() {
        Map<String, Map<String, Long>> copy = new HashMap<>();
        stats.forEach((category, categoryStats) ->
                copy.put(category, Collections.unmodifiableMap(new HashMap<>(categoryStats))));
        return Collections.unmodifiableMap(copy);
    }

    public boolean hasCategory(@Nonnull String category) {
        Map<String, Long> categoryStats = stats.get(category);
        return categoryStats != null && !categoryStats.isEmpty();
    }

    // --------------------------------------------------
    // Serialization helpers (used by Gson adapter)
    // --------------------------------------------------

    Map<String, Map<String, Long>> getStatsInternal() {
        return stats;
    }

    void setStats(@Nonnull Map<String, Map<String, Long>> loadedStats) {
        stats.clear();
        loadedStats.forEach((category, categoryStats) -> {
            Map<String, Long> newCategoryStats = new ConcurrentHashMap<>(categoryStats);
            stats.put(category, newCategoryStats);
        });
    }
}