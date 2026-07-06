package com.mystichorizons.mysticnametags.api.events;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class MysticNameTagsEvent {

    private final MysticNameTagsEventType type;
    private final Instant timestamp;
    private final String actor;
    private final UUID playerUuid;
    private final String playerName;
    private final String tagId;
    private final String result;
    private final Map<String, String> details;

    public MysticNameTagsEvent(@Nonnull MysticNameTagsEventType type,
                               @Nullable String actor,
                               @Nullable UUID playerUuid,
                               @Nullable String playerName,
                               @Nullable String tagId,
                               @Nullable String result,
                               @Nullable Map<String, String> details) {
        this.type = type;
        this.timestamp = Instant.now();
        this.actor = actor;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.tagId = tagId;
        this.result = result;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    @Nonnull
    public MysticNameTagsEventType getType() {
        return type;
    }

    @Nonnull
    public Instant getTimestamp() {
        return timestamp;
    }

    @Nullable
    public String getActor() {
        return actor;
    }

    @Nullable
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Nullable
    public String getPlayerName() {
        return playerName;
    }

    @Nullable
    public String getTagId() {
        return tagId;
    }

    @Nullable
    public String getResult() {
        return result;
    }

    @Nonnull
    public Map<String, String> getDetails() {
        return details;
    }
}
