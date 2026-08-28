package com.mystichorizons.mysticnametags.tags;

import com.google.gson.Gson;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.network.RedisManager;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Redis-backed storage for player tags, for networks that already run Redis
 * as their shared datastore.
 *
 * One string key per player:
 *   &lt;prefix&gt;tags:&lt;uuid&gt; -&gt; PlayerTagData JSON
 *
 * Note for operators: Redis must be configured with persistence (RDB or AOF)
 * when it is the storage backend, otherwise tag ownership is lost if the
 * Redis instance restarts. Use MYSQL storage with redisSyncEnabled if you
 * would rather keep durability in SQL and use Redis only as the sync bus.
 */
public final class RedisPlayerTagStore implements PlayerTagStore {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String KEY_SUFFIX = "tags:";

    private final Gson gson;

    public RedisPlayerTagStore(@Nonnull Gson gson) {
        this.gson = gson;
    }

    @Nonnull
    private String keyFor(@Nonnull UUID uuid, @Nonnull RedisManager redis) {
        return redis.key(KEY_SUFFIX + uuid);
    }

    @Nonnull
    @Override
    public PlayerTagData load(@Nonnull UUID uuid) {
        RedisManager redis = RedisManager.get();
        if (redis == null) {
            return unavailable(uuid, null);
        }

        try {
            String json = redis.execute(jedis -> jedis.get(keyFor(uuid, redis)));
            if (json == null || json.isBlank()) {
                // Genuinely a new player: an empty record is the right answer.
                return new PlayerTagData();
            }

            PlayerTagData data = gson.fromJson(json, PlayerTagData.class);
            return data != null ? data : new PlayerTagData();

        } catch (RedisManager.RedisUnavailableException e) {
            return unavailable(uuid, e);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Corrupt Redis tag data for " + uuid);
            return unavailable(uuid, e);
        }
    }

    /**
     * Marks the record as "we could not read it" rather than "this player owns
     * nothing", so TagManager refuses to write over it and a Redis blip cannot
     * erase somebody's tags.
     */
    @Nonnull
    private PlayerTagData unavailable(@Nonnull UUID uuid, Throwable cause) {
        LOGGER.at(Level.WARNING).withCause(cause)
                .log("[MysticNameTags] Could not read Redis tag data for " + uuid
                        + "; treating it as unknown until Redis responds again.");
        PlayerTagData data = new PlayerTagData();
        data.setLoadFailed(true);
        return data;
    }

    @Override
    public void save(@Nonnull UUID uuid, @Nonnull PlayerTagData data) {
        RedisManager redis = RedisManager.get();
        if (redis == null) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Redis is not connected; dropped tag save for " + uuid);
            return;
        }

        String json = gson.toJson(data);

        try {
            redis.execute(jedis -> jedis.set(keyFor(uuid, redis), json));
        } catch (RedisManager.RedisUnavailableException e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save Redis tag data for " + uuid);
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
        } catch (RedisManager.RedisUnavailableException e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to delete Redis tag data for " + uuid);
        }
    }

    /**
     * Writes only when no record exists yet.
     *
     * Every server on a network runs this migration against the same Redis
     * instance, so a plain SET would let the second server to boot overwrite
     * the data the first one just imported. SET NX makes the import
     * first-writer-wins and idempotent.
     *
     * @return true when this call created the record.
     */
    private boolean saveIfAbsent(@Nonnull UUID uuid, @Nonnull PlayerTagData data) {
        RedisManager redis = RedisManager.get();
        if (redis == null) {
            return false;
        }

        String json = gson.toJson(data);

        try {
            Long created = redis.execute(jedis -> jedis.setnx(keyFor(uuid, redis), json));
            return created != null && created == 1L;
        } catch (RedisManager.RedisUnavailableException e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to migrate Redis tag data for " + uuid);
            return false;
        }
    }

    @Override
    public void migrateFromFolder(@Nonnull File playerDataFolder, @Nonnull Gson gson) {
        if (RedisManager.get() == null) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Skipping playerdata migration: Redis is not connected.");
            return;
        }

        if (!playerDataFolder.exists() || !playerDataFolder.isDirectory()) {
            return;
        }

        File[] files = playerDataFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return;
        }

        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] Migrating " + files.length
                        + " playerdata JSON files into the Redis backend...");

        int migrated = 0;
        int skipped = 0;
        for (File file : files) {
            try {
                String filename = file.getName();
                String uuidPart = filename.substring(0, filename.length() - ".json".length());
                UUID uuid = UUID.fromString(uuidPart);

                try (InputStreamReader reader = new InputStreamReader(
                        new FileInputStream(file), StandardCharsets.UTF_8)) {

                    PlayerTagData data = gson.fromJson(reader, PlayerTagData.class);
                    if (data == null) data = new PlayerTagData();
                    if (saveIfAbsent(uuid, data)) {
                        migrated++;
                    } else {
                        skipped++;
                    }
                }
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e)
                        .log("[MysticNameTags] Failed to migrate " + file.getName()
                                + " to the Redis backend.");
            }
        }

        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] Migration complete. Migrated " + migrated
                        + " players, left " + skipped + " already present in Redis untouched.");

        File renamed = new File(playerDataFolder.getParentFile(), "playerdata_legacy");
        if (!playerDataFolder.renameTo(renamed)) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] Could not rename playerdata folder after migration; "
                            + "it may attempt again on next boot.");
        }
    }
}
