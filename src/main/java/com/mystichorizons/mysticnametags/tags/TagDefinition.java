package com.mystichorizons.mysticnametags.tags;

import javax.annotation.Nullable;
import java.util.List;

public class TagDefinition {

    // JSON fields (keep package-private to let GSON populate them)
    String id;
    String display;      // e.g. "&x&F&F&A&A&0&0[&6Dragon&x&F&F&A&A&0&0]"
    String description;  // lore for UI
    double price;        // 0 = free
    boolean purchasable;
    String permission;   // optional extra perm (e.g. "mysticnametags.tag.dragon")
    String category;     // optional category (e.g. "Legendary", "Seasonal", "Donator")

    Integer requiredPlaytimeMinutes;      // null = no requirement
    List<String> requiredOwnedTags;       // null/empty = none

    // challenge / stat requirement
    String  requiredStatKey;              // null = no stat requirement
    Integer requiredStatValue;            // null/0 = no stat requirement

    // item requirements
    List<ItemRequirement> requiredItems;  // null/empty = none

    // Commands run on *first* unlock
    List<String> onUnlockCommands;        // null/empty = none

    // ----------------------------------------------------------------
    // EXISTING ACCESSORS
    // ----------------------------------------------------------------

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public String getDescription() {
        return description;
    }

    public double getPrice() {
        return price;
    }

    public boolean isPurchasable() {
        return purchasable;
    }

    public String getPermission() {
        return permission;
    }

    public String getCategory() {
        if (category == null) return "General";
        String trimmed = category.trim();
        return trimmed.isEmpty() ? "General" : trimmed;
    }

    public void setCategory(@Nullable String category) {
        this.category = category;
    }

    @Nullable
    public Integer getRequiredPlaytimeMinutes() {
        return requiredPlaytimeMinutes;
    }

    public boolean hasPlaytimeRequirement() {
        return requiredPlaytimeMinutes != null && requiredPlaytimeMinutes > 0;
    }

    public List<String> getRequiredOwnedTags() {
        return requiredOwnedTags == null ? List.of() : requiredOwnedTags;
    }

    public boolean hasRequiredOwnedTags() {
        return requiredOwnedTags != null && !requiredOwnedTags.isEmpty();
    }

    public List<String> getOnUnlockCommands() {
        return onUnlockCommands == null ? List.of() : onUnlockCommands;
    }

    public boolean hasOnUnlockCommands() {
        return onUnlockCommands != null && !onUnlockCommands.isEmpty();
    }

    // ----------------------------------------------------------------
    // NEW ACCESSORS
    // ----------------------------------------------------------------

    /**
     * e.g. "kills.goblin", "dungeons.completed.goblin_caves"
     */
    @Nullable
    public String getRequiredStatKey() {
        return requiredStatKey;
    }

    /**
     * Minimum stat value (inclusive) required for the tag.
     */
    @Nullable
    public Integer getRequiredStatValue() {
        return requiredStatValue;
    }

    public boolean hasStatRequirement() {
        return requiredStatKey != null && !requiredStatKey.isBlank()
                && requiredStatValue != null && requiredStatValue > 0;
    }

    /**
     * In-game items required to unlock/purchase this tag.
     * These are treated as a cost and consumed on successful unlock.
     */
    public List<ItemRequirement> getRequiredItems() {
        return requiredItems == null ? List.of() : requiredItems;
    }

    public boolean hasItemRequirements() {
        return requiredItems != null && !requiredItems.isEmpty();
    }

    // ----------------------------------------------------------------
    // DTO for item requirements
    // ----------------------------------------------------------------

    public static class ItemRequirement {
        String itemId;
        int amount;

        public String getItemId() {
            return itemId;
        }

        public int getAmount() {
            return amount;
        }
    }
}