package com.mystichorizons.mysticnametags.tags;

import javax.annotation.Nullable;

public class TagDefinition {

    String id;
    String display;      // e.g. "&x&F&F&A&A&0&0[&6Dragon&x&F&F&A&A&0&0]"
    String description;  // lore for UI
    double price;        // 0 = free
    boolean purchasable;
    String permission;   // optional extra perm (e.g. "mysticnametags.tag.dragon")
    String category;     // optional category (e.g. "Legendary", "Seasonal", "Donator")

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
}
