package com.mystichorizons.mysticnametags.stats;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Bridge helpers to connect Hytale's block break/place logic
 * to MysticNameTags stats.
 *
 * Call these from the actual block break/place systems
 * once you've located them in the server code.
 */
public final class BlockStatHooks {

    private BlockStatHooks() {
    }

    public static void recordBlockBroken(@Nonnull PlayerRef playerRef,
                                         @Nonnull String blockId) {
        PlayerStatManager mgr = PlayerStatManager.get();
        if (mgr == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        mgr.incrementBlockBroken(uuid, blockId);
    }

    public static void recordBlockPlaced(@Nonnull PlayerRef playerRef,
                                         @Nonnull String blockId) {
        PlayerStatManager mgr = PlayerStatManager.get();
        if (mgr == null) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        mgr.incrementBlockPlaced(uuid, blockId);
    }
}