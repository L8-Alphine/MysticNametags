package com.mystichorizons.mysticnametags.network;

import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.tags.StorageBackend;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Owns the shared Redis connection used by the network-aware pieces of
 * MysticNameTags:
 *
 *  - RedisPlayerTagStore / RedisPlayerStatStore when storageBackend is REDIS.
 *  - {@link NetworkSyncService} pub/sub, which keeps every server's cached
 *    player data in step no matter which backend is in use.
 *
 * A pooled connection serves normal commands; the subscriber runs on its own
 * dedicated connection because SUBSCRIBE blocks for the life of the
 * connection.
 */
public final class RedisManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static volatile RedisManager instance;

    private final HostAndPort address;
    private final JedisClientConfig subscriberConfig;
    private final JedisPool pool;
    private final String keyPrefix;

    private volatile boolean healthy;
    private volatile boolean running = true;
    private volatile JedisPubSub activeSubscription;
    private volatile Thread subscriberThread;

    private RedisManager(@Nonnull HostAndPort address,
                         @Nonnull JedisClientConfig subscriberConfig,
                         @Nonnull JedisPool pool,
                         @Nonnull String keyPrefix) {
        this.address = address;
        this.subscriberConfig = subscriberConfig;
        this.pool = pool;
        this.keyPrefix = keyPrefix;
    }

    /**
     * True when this server needs Redis at all: either it is the storage
     * backend, or cross-server sync has been switched on next to SQL/file
     * storage.
     */
    public static boolean isRequired(@Nonnull Settings settings) {
        StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());
        return backend == StorageBackend.REDIS || settings.isRedisSyncEnabled();
    }

    /**
     * Connects (or reconnects) using the current settings. Safe to call when
     * Redis is not configured at all; it simply leaves the manager unset.
     *
     * @return true when a live connection was established.
     */
    public static synchronized boolean init() {
        shutdown();

        Settings settings = Settings.get();
        if (!isRequired(settings)) {
            return false;
        }

        String host = settings.getRedisHost();
        int port = settings.getRedisPort();

        try {
            HostAndPort address = new HostAndPort(host, port);
            int timeout = settings.getRedisTimeoutMs();

            DefaultJedisClientConfig.Builder base = DefaultJedisClientConfig.builder()
                    .connectionTimeoutMillis(timeout)
                    .socketTimeoutMillis(timeout)
                    .database(settings.getRedisDatabase())
                    .ssl(settings.isRedisSsl())
                    .clientName("MysticNameTags");

            String user = settings.getRedisUser();
            if (!user.isEmpty()) {
                base.user(user);
            }
            String password = settings.getRedisPassword();
            if (!password.isEmpty()) {
                base.password(password);
            }

            JedisClientConfig pooledConfig = base.build();

            // The subscriber connection must never time out while idle:
            // SUBSCRIBE simply parks on the socket waiting for messages.
            // pooledConfig is already built, so reusing the builder is safe.
            JedisClientConfig subscriberConfig = base.socketTimeoutMillis(0).build();

            GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(settings.getRedisPoolSize());
            poolConfig.setMaxIdle(settings.getRedisPoolSize());
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setBlockWhenExhausted(true);
            poolConfig.setMaxWait(Duration.ofMillis(Math.max(1000, timeout)));

            JedisPool pool = new JedisPool(poolConfig, address, pooledConfig);

            RedisManager manager = new RedisManager(
                    address, subscriberConfig, pool, settings.getRedisKeyPrefix());

            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }
            manager.healthy = true;
            instance = manager;

            LOGGER.at(Level.INFO).log("[MysticNameTags] Connected to Redis at "
                    + host + ":" + port + " (db " + settings.getRedisDatabase()
                    + ", prefix " + manager.keyPrefix + ")");
            return true;

        } catch (Throwable t) {
            instance = null;
            LOGGER.at(Level.SEVERE).withCause(t)
                    .log("[MysticNameTags] Failed to connect to Redis at " + host + ":" + port
                            + ". Redis-backed storage and cross-server sync are disabled.");
            return false;
        }
    }

    @Nullable
    public static RedisManager get() {
        return instance;
    }

    public static synchronized void shutdown() {
        RedisManager manager = instance;
        instance = null;
        if (manager != null) {
            manager.close();
        }
    }

    @Nonnull
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /** Fully-qualified key, e.g. mysticnametags:tags:&lt;uuid&gt;. */
    @Nonnull
    public String key(@Nonnull String suffix) {
        return keyPrefix + suffix;
    }

    /** Last known connection state; flipped by command failures. */
    public boolean isHealthy() {
        return healthy;
    }

    @Nonnull
    public String describeAddress() {
        return address.getHost() + ":" + address.getPort();
    }

    /**
     * Runs a command against a pooled connection.
     *
     * @throws RedisUnavailableException when the command could not be
     *         completed; callers must treat this as "unknown", never as
     *         "no data", so a blip cannot wipe a player's tags.
     */
    public <T> T execute(@Nonnull Function<Jedis, T> action) {
        try (Jedis jedis = pool.getResource()) {
            T result = action.apply(jedis);
            healthy = true;
            return result;
        } catch (Exception e) {
            healthy = false;
            throw new RedisUnavailableException(e);
        }
    }

    /** Fire-and-forget publish; failures are logged, never thrown. */
    public void publish(@Nonnull String channel, @Nonnull String message) {
        try {
            execute(jedis -> jedis.publish(channel, message));
        } catch (RedisUnavailableException e) {
            LOGGER.at(Level.FINE).withCause(e)
                    .log("[MysticNameTags] Failed to publish on Redis channel " + channel);
        }
    }

    /**
     * Subscribes to the given channel on a dedicated daemon thread,
     * reconnecting with backoff for as long as this manager is alive.
     */
    public void startSubscriber(@Nonnull String channel,
                                @Nonnull BiConsumer<String, String> handler) {
        if (subscriberThread != null) {
            return;
        }

        Thread thread = new Thread(() -> runSubscriber(channel, handler),
                "MysticNameTags-RedisSubscriber");
        thread.setDaemon(true);
        this.subscriberThread = thread;
        thread.start();
    }

    private void runSubscriber(@Nonnull String channel,
                               @Nonnull BiConsumer<String, String> handler) {
        long backoffMs = 1000L;

        while (running) {
            try (Jedis jedis = new Jedis(address, subscriberConfig)) {
                JedisPubSub subscription = new JedisPubSub() {
                    @Override
                    public void onMessage(String receivedChannel, String message) {
                        try {
                            handler.accept(receivedChannel, message);
                        } catch (Throwable t) {
                            LOGGER.at(Level.WARNING).withCause(t)
                                    .log("[MysticNameTags] Failed to handle Redis sync message: " + message);
                        }
                    }

                    @Override
                    public void onSubscribe(String receivedChannel, int subscribedChannels) {
                        LOGGER.at(Level.INFO).log(
                                "[MysticNameTags] Listening for cross-server tag updates on "
                                        + receivedChannel);
                    }
                };

                activeSubscription = subscription;
                backoffMs = 1000L;
                jedis.subscribe(subscription, channel);
            } catch (Throwable t) {
                if (!running) {
                    return;
                }
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Redis subscriber disconnected; retrying in "
                                + backoffMs + "ms");
            } finally {
                activeSubscription = null;
            }

            if (!running) {
                return;
            }

            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs = Math.min(backoffMs * 2L, 30_000L);
        }
    }

    private void close() {
        running = false;

        JedisPubSub subscription = activeSubscription;
        if (subscription != null) {
            try {
                subscription.unsubscribe();
            } catch (Throwable ignored) {
                // connection is going away anyway
            }
        }

        Thread thread = subscriberThread;
        subscriberThread = null;
        if (thread != null) {
            thread.interrupt();
        }

        try {
            pool.close();
        } catch (Throwable ignored) {
            // nothing useful to do during shutdown
        }
    }

    /** Raised when a Redis command could not be completed. */
    public static final class RedisUnavailableException extends RuntimeException {
        public RedisUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}
