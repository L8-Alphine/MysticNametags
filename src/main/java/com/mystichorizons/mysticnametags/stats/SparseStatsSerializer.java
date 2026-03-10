package com.mystichorizons.mysticnametags.stats;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sparse JSON serializer for PlayerStatsData:
 *
 * {
 *   "DataVersion": 1,
 *   "stats": {
 *     "custom": {
 *       "damage_dealt": 12345,
 *       "damage_taken": 9876
 *     },
 *     "mined": {
 *       "hytale:stone": 1200
 *     }
 *   }
 * }
 */
public final class SparseStatsSerializer
        implements JsonSerializer<PlayerStatsData>, JsonDeserializer<PlayerStatsData> {

    @Override
    public JsonElement serialize(PlayerStatsData src,
                                 Type typeOfSrc,
                                 JsonSerializationContext context) {

        JsonObject root = new JsonObject();
        root.addProperty("DataVersion", src.getDataVersion());

        JsonObject statsObject = new JsonObject();
        Map<String, Map<String, Long>> allStats = src.getAll();

        for (Map.Entry<String, Map<String, Long>> categoryEntry : allStats.entrySet()) {
            String category = categoryEntry.getKey();
            Map<String, Long> categoryStats = categoryEntry.getValue();

            if (category == null || category.isBlank() || categoryStats == null || categoryStats.isEmpty()) {
                continue;
            }

            JsonObject categoryObject = new JsonObject();
            boolean hasNonZero = false;

            for (Map.Entry<String, Long> statEntry : categoryStats.entrySet()) {
                String statKey = statEntry.getKey();
                Long valueObj = statEntry.getValue();

                if (statKey == null || statKey.isBlank() || valueObj == null) {
                    continue;
                }

                long value = valueObj;
                if (value != 0L) {
                    categoryObject.addProperty(statKey, value);
                    hasNonZero = true;
                }
            }

            if (hasNonZero) {
                statsObject.add(category, categoryObject);
            }
        }

        root.add("stats", statsObject);
        return root;
    }

    @Override
    public PlayerStatsData deserialize(JsonElement json,
                                       Type typeOfT,
                                       JsonDeserializationContext context)
            throws JsonParseException {

        PlayerStatsData stats = new PlayerStatsData();
        if (json == null || !json.isJsonObject()) {
            return stats;
        }

        JsonObject root = json.getAsJsonObject();

        if (root.has("DataVersion") && !root.get("DataVersion").isJsonNull()) {
            try {
                stats.setDataVersion(root.get("DataVersion").getAsInt());
            } catch (Exception ignored) {
                // leave default version
            }
        }

        if (root.has("stats") && root.get("stats").isJsonObject()) {
            JsonObject statsObject = root.getAsJsonObject("stats");
            Map<String, Map<String, Long>> loadedStats = new LinkedHashMap<>();

            for (Map.Entry<String, JsonElement> categoryEntry : statsObject.entrySet()) {
                String category = categoryEntry.getKey();
                JsonElement categoryElement = categoryEntry.getValue();

                if (category == null || category.isBlank() || categoryElement == null || !categoryElement.isJsonObject()) {
                    continue;
                }

                JsonObject categoryObject = categoryElement.getAsJsonObject();
                Map<String, Long> categoryStats = new LinkedHashMap<>();

                for (Map.Entry<String, JsonElement> statEntry : categoryObject.entrySet()) {
                    String statKey = statEntry.getKey();
                    JsonElement statElement = statEntry.getValue();

                    if (statKey == null || statKey.isBlank() || statElement == null || !statElement.isJsonPrimitive()) {
                        continue;
                    }

                    JsonPrimitive primitive = statElement.getAsJsonPrimitive();
                    if (!primitive.isNumber()) {
                        continue;
                    }

                    try {
                        long value = primitive.getAsLong();
                        if (value != 0L) {
                            categoryStats.put(statKey, value);
                        }
                    } catch (Exception ignored) {
                        // skip malformed value
                    }
                }

                if (!categoryStats.isEmpty()) {
                    loadedStats.put(category, categoryStats);
                }
            }

            stats.setStats(loadedStats);
        }

        return stats;
    }
}