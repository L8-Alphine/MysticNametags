package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.tags.TagDefinition;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Handles item checks & consumption for tag requirements.
 */
public interface ItemRequirementHandler {

    /**
     * Check if the player has ALL required items.
     */
    boolean hasItems(@Nonnull PlayerRef playerRef,
                     @Nonnull List<TagDefinition.ItemRequirement> requirements);

    /**
     * Consume ALL required items from the player.
     * Called only after hasItems(...) was true.
     *
     * @return true if all items were successfully consumed.
     */
    boolean consumeItems(@Nonnull PlayerRef playerRef,
                         @Nonnull List<TagDefinition.ItemRequirement> requirements);
}