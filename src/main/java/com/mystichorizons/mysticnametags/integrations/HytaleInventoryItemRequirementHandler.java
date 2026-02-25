package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.tags.TagDefinition;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

/**
 * Hytale implementation of ItemRequirementHandler:
 * - Resolves a player's main ItemContainer from PlayerRef
 * - Uses only core Hytale inventory APIs (ItemContainer / ItemStack / ItemStackTransaction).
 */
public final class HytaleInventoryItemRequirementHandler implements ItemRequirementHandler {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Override
    public boolean hasItems(@Nonnull PlayerRef playerRef,
                            @Nonnull List<TagDefinition.ItemRequirement> requirements) {

        ItemContainer container = resolveInventory(playerRef);
        if (container == null) {
            LOGGER.at(Level.FINE)
                    .log("[MysticNameTags] No inventory container found for player " + playerRef.getUsername());
            return false;
        }

        for (TagDefinition.ItemRequirement req : requirements) {
            if (req == null) continue;

            String itemId = req.getItemId();
            int needed = req.getAmount();

            if (itemId == null || itemId.isBlank() || needed <= 0) {
                // Treat malformed or zero-amount requirements as no-op
                continue;
            }

            long total = countItem(container, itemId);
            if (total < needed) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean consumeItems(@Nonnull PlayerRef playerRef,
                                @Nonnull List<TagDefinition.ItemRequirement> requirements) {

        ItemContainer container = resolveInventory(playerRef);
        if (container == null) {
            LOGGER.at(Level.FINE)
                    .log("[MysticNameTags] No inventory container found for player " + playerRef.getUsername());
            return false;
        }

        // First pass: verify the player actually has everything
        for (TagDefinition.ItemRequirement req : requirements) {
            if (req == null) continue;

            String itemId = req.getItemId();
            int needed = req.getAmount();

            if (itemId == null || itemId.isBlank() || needed <= 0) {
                continue;
            }

            long total = countItem(container, itemId);
            if (total < needed) {
                return false; // don't touch inventory if they can't afford it
            }
        }

        // Second pass: actually remove the items
        for (TagDefinition.ItemRequirement req : requirements) {
            if (req == null) continue;

            String itemId = req.getItemId();
            int needed = req.getAmount();

            if (itemId == null || itemId.isBlank() || needed <= 0) {
                continue;
            }

            boolean ok = tryRemove(container, itemId, needed);
            if (!ok) {
                // If this happens, something changed between check and consume or some slot
                // is protected. We fail the whole consume operation.
                LOGGER.at(Level.WARNING)
                        .log("[MysticNameTags] Failed to remove required items (" + itemId + " x" + needed +
                                ") from player " + playerRef.getUsername());
                return false;
            }
        }

        return true;
    }

    // =====================================================================
    // Lightweight inventory ops (no external dependencies)
    // =====================================================================

    /**
     * Count total quantity of a given itemId in the container.
     * Matches strictly on itemId (ignores metadata).
     */
    private static long countItem(@Nullable ItemContainer container, @Nullable String itemId) {
        if (container == null || itemId == null || itemId.isBlank()) {
            return 0L;
        }

        final long[] total = new long[1];

        container.forEach((slot, stack) -> {
            if (stack != null && !stack.isEmpty()) {
                if (itemId.equals(stack.getItemId())) {
                    // quantities are ints but we store in long to be extra safe
                    total[0] += stack.getQuantity();
                }
            }
        });

        return total[0];
    }

    /**
     * Try to remove a given quantity of an itemId from the container.
     *
     * This is intentionally similar to Coins & Markets' HytaleInventoryOps.tryRemove,
     * but implemented locally and using only ItemContainer/ItemStack APIs.
     */
    private static boolean tryRemove(@Nullable ItemContainer container,
                                     @Nullable String itemId,
                                     int quantity) {
        if (container == null || itemId == null || itemId.isBlank()) {
            return false;
        }

        if (quantity <= 0) {
            return true;
        }

        // Quick sanity check: do a count before we start mutating anything
        long total = countItem(container, itemId);
        if (total < quantity) {
            return false;
        }

        // Resolve max stack size for this item
        int maxStack;
        try {
            // Constructs a temporary ItemStack solely to get the Item and its max stack size.
            ItemStack probe = new ItemStack(itemId, 1);
            maxStack = probe.getItem().getMaxStack();
            if (maxStack <= 0) {
                // Fallback: treat as non-stackable if config is weird
                maxStack = 1;
            }
        } catch (Exception ex) {
            // Invalid itemId or asset issues – treat as failure
            return false;
        }

        int remaining = quantity;

        while (remaining > 0) {
            int chunk = Math.min(maxStack, remaining);

            // Create a "request" stack representing what we want to remove this iteration
            ItemStack request = new ItemStack(itemId, chunk);

            ItemStackTransaction tx = container.removeItemStack(request);
            if (!tx.succeeded()) {
                // If a chunk fails to remove, we bail out.
                // No rollback here – this matches HytaleInventoryOps semantics.
                return false;
            }

            remaining -= chunk;
        }

        return true;
    }

    // =====================================================================
    // Inventory resolution
    // =====================================================================

    /**
     * Resolve the player's "main" ItemContainer from PlayerRef.
     *
     * Steps:
     *   1) Get Player ECS component from PlayerRef
     *   2) Get Inventory from Player
     *   3) Use reflection to find a no-arg method that returns ItemContainer
     *
     * If/when you know your Inventory API (e.g. getMainContainer()),
     * replace the reflection part with a direct call.
     */
    @Nullable
    private ItemContainer resolveInventory(@Nonnull PlayerRef playerRef) {
        // 1) ECS: PlayerRef -> Player component
        Player player = playerRef.getComponent(Player.getComponentType());
        if (player == null) {
            return null;
        }

        // 2) Player -> Inventory
        Inventory inv = player.getInventory();
        if (inv == null) {
            return null;
        }

        // 3a) Try a couple of common names first for stability
        try {
            // Example: Inventory.getMainContainer()
            try {
                Method m = inv.getClass().getMethod("getMainContainer");
                if (ItemContainer.class.isAssignableFrom(m.getReturnType())) {
                    Object result = m.invoke(inv);
                    if (result instanceof ItemContainer container) {
                        return container;
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // fall through to generic search
            }

            // Example: Inventory.getContainer()
            try {
                Method m = inv.getClass().getMethod("getContainer");
                if (ItemContainer.class.isAssignableFrom(m.getReturnType())) {
                    Object result = m.invoke(inv);
                    if (result instanceof ItemContainer container) {
                        return container;
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // fall through to generic search
            }

            // 3b) Fallback: first public no-arg method returning ItemContainer
            for (Method m : inv.getClass().getMethods()) {
                if (m.getParameterCount() == 0 &&
                        ItemContainer.class.isAssignableFrom(m.getReturnType())) {

                    Object result = m.invoke(inv);
                    if (result instanceof ItemContainer container) {
                        return container;
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.at(Level.WARNING)
                    .withCause(ex)
                    .log("[MysticNameTags] Failed to resolve ItemContainer from Inventory via reflection.");
        }

        return null;
    }
}