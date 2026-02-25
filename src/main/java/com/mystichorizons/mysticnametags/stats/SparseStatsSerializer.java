package com.mystichorizons.mysticnametags.stats;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.HashMap;
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

        allStats.forEach((category, categoryStats) -> {
            JsonObject categoryObject = new JsonObject();
            boolean hasNonZero = false;

            for (Map.Entry<String, Long> entry : categoryStats.entrySet()) {
                long value = entry.getValue();
                if (value != 0L) {
                    categoryObject.addProperty(entry.getKey(), value);
                    hasNonZero = true;
                }
            }

            if (hasNonZero) {
                statsObject.add(category, categoryObject);
            }
        });

        root.add("stats", statsObject);
        return root;
    }

    @Override
    public PlayerStatsData deserialize(JsonElement json,
                                       Type typeOfT,
                                       JsonDeserializationContext context)
            throws JsonParseException {

        PlayerStatsData stats = new PlayerStatsData();
        if (!json.isJsonObject()) {
            return stats;
        }

        JsonObject root = json.getAsJsonObject();

        if (root.has("DataVersion")) {
            stats.setDataVersion(root.get("DataVersion").getAsInt());
        }

        if (root.has("stats") && root.get("stats").isJsonObject()) {
            JsonObject statsObject = root.getAsJsonObject("stats");
            Map<String, Map<String, Long>> loadedStats = new HashMap<>();

            for (Map.Entry<String, JsonElement> categoryEntry : statsObject.entrySet()) {
                String category = categoryEntry.getKey();
                JsonElement categoryElement = categoryEntry.getValue();
                if (!categoryElement.isJsonObject()) {
                    continue;
                }

                JsonObject categoryObject = categoryElement.getAsJsonObject();
                Map<String, Long> categoryStats = new HashMap<>();

                for (Map.Entry<String, JsonElement> statEntry : categoryObject.entrySet()) {
                    String statKey = statEntry.getKey();
                    JsonElement statElement = statEntry.getValue();
                    if (!statElement.isJsonPrimitive()) {
                        continue;
                    }
                    long value = statElement.getAsLong();
                    categoryStats.put(statKey, value);
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