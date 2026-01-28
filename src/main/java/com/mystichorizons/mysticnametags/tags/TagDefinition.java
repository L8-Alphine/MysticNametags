package com.mystichorizons.mysticnametags.tags;

public class TagDefinition {

    String id;
    String display;      // e.g. "&x&F&F&A&A&0&0[&6Dragon&x&F&F&A&A&0&0]"
    String description;  // lore for UI
    double price;        // 0 = free
    boolean purchasable;
    String permission;   // optional extra perm (e.g. "mysticnametags.tag.dragon")

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
}
