package com.mystichorizons.mysticnametags.network;

import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.tags.TagManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetAddress;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Cross-server tag synchronisation over Redis pub/sub.
 *
 * Shared storage (MYSQL or REDIS) already makes a player's tags follow them
 * between servers on join, because each server re-reads their row when they
 * connect. Pub/sub covers the rest: a change made on one server reaches every
 * other server immediately, so an admin granting a tag on the lobby is
 * reflected on the survival server the player is standing on.
 *
 * Message wire format (pipe delimited, so it stays greppable in redis-cli):
 *   v1|&lt;serverId&gt;|&lt;type&gt;|&lt;argument&gt;
 */
public final class NetworkSyncService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String PROTOCOL_VERSION = "v1";
    private static final String CHANNEL_SUFFIX = "sync";
    private static final String DELIMITER = "|";

    /** Player tag data changed elsewhere; argument is the player UUID. */
    private static final String TYPE_PLAYER = "PLAYER";

    private static volatile NetworkSyncService instance;

    private final String serverId;
    private final String channel;
    private final ExecutorService publisher;

    private NetworkSyncService(@Nonnull String serverId, @Nonnull String channel) {
        this.serverId = serverId;
        this.channel = channel;
        this.publisher = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "MysticNameTags-RedisPublisher");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts the subscriber if sync is enabled and Redis is connected.
     * Must be called after TagManager.init so incoming messages have
     * somewhere to land.
     */
    public static synchronized void init() {
        shutdown();

        Settings settings = Settings.get();
        if (!settings.isRedisSyncEnabled()) {
            return;
        }

        RedisManager redis = RedisManager.get();
        if (redis == null) {
            LOGGER.at(Level.WARNING).log(
                    "[MysticNameTags] redisSyncEnabled is true but Redis is not connected; "
                            + "cross-server tag sync is off.");
            return;
        }

        String serverId = resolveServerId(settings);
        NetworkSyncService service = new NetworkSyncService(serverId, redis.key(CHANNEL_SUFFIX));
        instance = service;

        redis.startSubscriber(service.channel, (channel, message) -> service.handle(message));

        LOGGER.at(Level.INFO).log("[MysticNameTags] Cross-server tag sync enabled as server id "
                + serverId);
    }

    @Nullable
    public static NetworkSyncService get() {
        return instance;
    }

    public static synchronized void shutdown() {
        NetworkSyncService service = instance;
        instance = null;
        if (service == null) {
            return;
        }

        service.publisher.shutdown();
        try {
            if (!service.publisher.awaitTermination(2, TimeUnit.SECONDS)) {
                service.publisher.shutdownNow();
            }
        } catch (InterruptedException e) {
            service.publisher.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Nonnull
    public String getServerId() {
        return serverId;
    }

    @Nonnull
    public String getChannel() {
        return channel;
    }

    /**
     * Tells every other server that this player's tag data changed, so they
     * drop their cached copy and re-read it from shared storage.
     *
     * Published off the calling thread; the write to storage has already
     * happened by the time this is called, so ordering is safe.
     */
    public void publishPlayerDataChanged(@Nonnull UUID uuid) {
        String payload = String.join(DELIMITER,
                PROTOCOL_VERSION, serverId, TYPE_PLAYER, uuid.toString());

        try {
            publisher.execute(() -> {
                RedisManager redis = RedisManager.get();
                if (redis != null) {
                    redis.publish(channel, payload);
                }
            });
        } catch (Throwable t) {
            // Rejected because we are shutting down; nothing worth logging loudly.
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Could not queue tag sync message for " + uuid);
        }
    }

    /** Called on the Redis subscriber thread for every message on our channel. */
    private void handle(@Nonnull String message) {
        String[] parts = message.split("\\" + DELIMITER, 4);
        if (parts.length < 4 || !PROTOCOL_VERSION.equals(parts[0])) {
            return;
        }

        // Our own broadcast came back to us; this server is already up to date.
        if (serverId.equals(parts[1])) {
            return;
        }

        if (!TYPE_PLAYER.equals(parts[2])) {
            return;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(parts[3]);
        } catch (IllegalArgumentException e) {
            return;
        }

        TagManager tagManager = TagManager.get();
        if (tagManager == null) {
            return;
        }
        tagManager.handleRemotePlayerDataChanged(uuid);
    }

    /**
     * Identity used to filter out our own broadcasts. Falls back to
     * hostname plus a random suffix so two servers sharing a machine never
     * collide and silently ignore each other.
     */
    @Nonnull
    private static String resolveServerId(@Nonnull Settings settings) {
        String configured = settings.getNetworkServerId();
        if (!configured.isEmpty()) {
            return configured;
        }

        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Throwable t) {
            host = "server";
        }

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return sanitize(host.toLowerCase(Locale.ROOT)) + "-" + suffix;
    }

    /** Keeps ids free of the delimiter and of anything awkward in logs. */
    @Nonnull
    public static String sanitize(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim().replaceAll("[^A-Za-z0-9._-]", "-");
        return cleaned.length() > 48 ? cleaned.substring(0, 48) : cleaned;
    }
}
