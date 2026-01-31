package com.mystichorizons.mysticnametags.api;

import com.mystichorizons.mysticnametags.tags.TagDefinition;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable snapshot of a tag definition, with both raw and processed
 * (colored + plain) fields, suitable for external plugins and UIs.
 */
public final class TagView {

    private final String id;

    private final String displayRaw;
    private final String displayColored;
    private final String displayPlain;

    private final String descriptionRaw;
    private final String descriptionColored;
    private final String descriptionPlain;

    private final double price;
    private final boolean purchasable;
    private final String permission;

    private TagView(String id,
                    String displayRaw,
                    String displayColored,
                    String displayPlain,
                    String descriptionRaw,
                    String descriptionColored,
                    String descriptionPlain,
                    double price,
                    boolean purchasable,
                    String permission) {
        this.id = id;
        this.displayRaw = displayRaw;
        this.displayColored = displayColored;
        this.displayPlain = displayPlain;
        this.descriptionRaw = descriptionRaw;
        this.descriptionColored = descriptionColored;
        this.descriptionPlain = descriptionPlain;
        this.price = price;
        this.purchasable = purchasable;
        this.permission = permission;
    }

    /**
     * Build a snapshot from an internal TagDefinition.
     */
    @Nonnull
    public static TagView from(@Nonnull TagDefinition def) {
        String id = def.getId();

        String displayRaw = def.getDisplay();
        String displayColored = displayRaw != null
                ? ColorFormatter.colorize(displayRaw)
                : "";
        String displayPlain = displayRaw != null
                ? ColorFormatter.stripFormatting(displayRaw)
                : "";

        String descRaw = def.getDescription();
        String descColored = descRaw != null
                ? ColorFormatter.colorize(descRaw)
                : "";
        String descPlain = descRaw != null
                ? ColorFormatter.stripFormatting(descRaw)
                : "";

        return new TagView(
                id,
                displayRaw,
                displayColored,
                displayPlain != null ? displayPlain.trim() : "",
                descRaw,
                descColored,
                descPlain != null ? descPlain.trim() : "",
                def.getPrice(),
                def.isPurchasable(),
                def.getPermission()
        );
    }

    // ----- Getters -----

    @Nonnull
    public String getId() {
        return id;
    }

    @Nullable
    public String getDisplayRaw() {
        return displayRaw;
    }

    @Nonnull
    public String getDisplayColored() {
        return displayColored;
    }

    @Nonnull
    public String getDisplayPlain() {
        return displayPlain;
    }

    @Nullable
    public String getDescriptionRaw() {
        return descriptionRaw;
    }

    @Nonnull
    public String getDescriptionColored() {
        return descriptionColored;
    }

    @Nonnull
    public String getDescriptionPlain() {
        return descriptionPlain;
    }

    public double getPrice() {
        return price;
    }

    public boolean isPurchasable() {
        return purchasable;
    }

    @Nullable
    public String getPermission() {
        return permission;
    }
}
