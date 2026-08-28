package com.mystichorizons.mysticnametags.stats;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.network.RedisManager;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Redis-backed storage for player stats, used when storageBackend is REDIS so
 * stat-gated tag requirements evaluate the same way on every server.
 *
 * One string key per player:
 *   &lt;prefix&gt;stats:&lt;uuid&gt; -&gt; PlayerStatsData JSON
 */
public final class RedisPlayerStatStore implements PlayerStatStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String KEY_SUFFIX = "stats:";

    private final Gson gson;

    public RedisPlayerStatStore(@Nonnull Gson gson) {
        this.gson = gson;
    }

    @Nonnull
    private String keyFor(@Nonnull UUID uuid, @Nonnull RedisManager redis) {
        return redis.key(KEY_SUFFIX + uuid);
    }

    @Nonnull
    @Override
    public PlayerStatsData load(@Nonnull UUID uuid) {
        RedisManager redis = RedisManager.get();
        if (redis == null) {
            return new PlayerStatsData();
        }

        try {
            String json = redis.execute(jedis -> jedis.get(keyFor(uuid, redis)));
            if (json == null || json.isBlank()) {
                return new PlayerStatsData();
            }

            PlayerStatsData data = gson.fromJson(json, PlayerStatsData.class);
            return data != null ? data : new PlayerStatsData();

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load Redis stats for " + uuid);
            return new PlayerStatsData();
        }
    }

    @Override
    public void save(@Nonnull UUID uuid, @Nonnull PlayerStatsData data) {
        RedisManager redis = RedisManager.get();
        if (redis == null) {
            return;
        }

        String json = gson.toJson(data);

        try {
            redis.execute(jedis -> jedis.set(keyFor(uuid, redis), json));
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save Redis stats for " + uuid);
        }
    }

    @Override
    public void delete(@Nonnull UUID uuid) {
        RedisManager redis = RedisManager.get();
        if (redis == null) {
            return;
        }

        try {
            redis.execute(jedis -> jedis.del(keyFor(uuid, redis)));
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to delete Redis stats for " + uuid);
        }
    }
}
