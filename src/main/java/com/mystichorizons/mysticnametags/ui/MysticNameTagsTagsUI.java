package com.mystichorizons.mysticnametags.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;
import com.mystichorizons.mysticnametags.tags.TagDefinition;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.tags.TagManager.TagPurchaseResult;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Tag selection / purchase UI.
 *
 * Layout file: mysticnametags/Tags.ui
 */
public class MysticNameTagsTagsUI extends InteractiveCustomUIPage<MysticNameTagsTagsUI.UIEventData> {

    public static final String LAYOUT = "mysticnametags/Tags.ui";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int MAX_ROWS  = 10;
    private static final int PAGE_SIZE = 10;

    private final PlayerRef playerRef;
    private final UUID uuid;
    private final int currentPage; // zero-based

    public MysticNameTagsTagsUI(@Nonnull PlayerRef playerRef, @Nonnull UUID uuid) {
        this(playerRef, uuid, 0);
    }

    public MysticNameTagsTagsUI(@Nonnull PlayerRef playerRef,
                                @Nonnull UUID uuid,
                                int page) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.uuid = uuid;
        this.currentPage = Math.max(page, 0);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {

        // Load layout once
        cmd.append(LAYOUT);

        // Static button bindings
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BottomCloseButton",
                EventData.of("Action", "close")
        );
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PrevPageButton",
                EventData.of("Action", "prev_page")
        );
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NextPageButton",
                EventData.of("Action", "next_page")
        );

        // Fill the dynamic content (rows, balance, preview)
        rebuildPage(ref, store, cmd, evt, true);
    }

    /**
     * Rebuilds all dynamic parts of the page:
     * - rows (tag name, price, button text, visibility)
     * - page label, arrows
     * - balance label
     * - current nameplate preview
     *
     * @param registerRowEvents if true, also attaches event bindings to row buttons.
     */
    private void rebuildPage(@Nonnull Ref<EntityStore> ref,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull UICommandBuilder cmd,
                             @Nonnull UIEventBuilder evt,
                             boolean registerRowEvents) {

        TagManager tagManager = TagManager.get();

        // Collect tags into a list for paging
        Collection<TagDefinition> all = tagManager.getAllTags();
        List<TagDefinition> tags = new ArrayList<>(all);

        int totalPages = Math.max(1, (int) Math.ceil(tags.size() / (double) PAGE_SIZE));
        int page = Math.min(currentPage, totalPages - 1);

        int startIndex = page * PAGE_SIZE;
        int endIndex   = Math.min(startIndex + PAGE_SIZE, tags.size());

        // Equipped tag (by id) for this player
        String equippedId = null;
        if (uuid != null) {
            TagDefinition equipped = tagManager.getEquipped(uuid);
            if (equipped != null) {
                equippedId = equipped.getId();
            }
        }

        // Fill rows for this page
        int row = 0;
        for (int i = startIndex; i < endIndex && row < MAX_ROWS; i++, row++) {
            TagDefinition def = tags.get(i);

            String nameSelector   = "#TagRow" + row + "Name";
            String priceSelector  = "#TagRow" + row + "Price";
            String buttonSelector = "#TagRow" + row + "Button";

            String rawDisplay = def.getDisplay();

            String nameText = ColorFormatter.stripFormatting(rawDisplay);
            String hex      = ColorFormatter.extractFirstHexColor(rawDisplay);

            String priceText;
            if (!def.isPurchasable() || def.getPrice() <= 0.0D) {
                priceText = "Free";
            } else {
                priceText = def.getPrice() + " coins";
            }

            cmd.set(nameSelector + ".Text", nameText);
            cmd.set(priceSelector + ".Text", priceText);

            if (hex != null) {
                cmd.set(nameSelector + ".Style.TextColor", "#" + hex);
            }

            // Determine button label based on usage/equipped state
            boolean canUse = tagManager.canUseTag(playerRef, uuid, def);
            boolean isEquipped = equippedId != null
                    && equippedId.equalsIgnoreCase(def.getId());

            String buttonText;
            if (isEquipped) {
                buttonText = "Unequip";
            } else if (canUse || (!def.isPurchasable() || def.getPrice() <= 0.0D)) {
                // Can use via permission, or it's a free/non-purchasable tag
                buttonText = "Equip";
            } else {
                // Locked behind economy / not owned yet
                buttonText = "Purchase";
            }

            cmd.set(buttonSelector + ".Text", buttonText);

            // show row
            cmd.set(nameSelector + ".Visible", true);
            cmd.set(priceSelector + ".Visible", true);
            cmd.set(buttonSelector + ".Visible", true);

            // Row button binding only needed on first build
            if (registerRowEvents) {
                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        buttonSelector,
                        new EventData()
                                .append("Action", "tag_click")
                                .append("TagId", def.getId())
                );
            }
        }

        // Hide unused rows
        for (; row < MAX_ROWS; row++) {
            String nameSelector   = "#TagRow" + row + "Name";
            String priceSelector  = "#TagRow" + row + "Price";
            String buttonSelector = "#TagRow" + row + "Button";

            cmd.set(nameSelector + ".Visible", false);
            cmd.set(priceSelector + ".Visible", false);
            cmd.set(buttonSelector + ".Visible", false);
        }

        // Page label + enable/disable arrows
        cmd.set("#PageLabel.Text", "Page " + (page + 1) + "/" + totalPages);
        cmd.set("#PrevPageButton.Visible", page > 0);
        cmd.set("#NextPageButton.Visible", page < totalPages - 1);

        // Balance label
        double balance = 0.0;
        try {
            if (uuid != null) {
                balance = TagManager.get().getIntegrations().getBalance(uuid);
            }
        } catch (Throwable ignored) { }

        cmd.set("#BalanceLabel.Text", "Balance: " + balance);

        // ----- Current nameplate preview (colored) -----
        String previewText;
        String previewHex = null;

        try {
            String baseName = playerRef.getUsername();

            // This is the *actual* nameplate (with § codes) used in-game
            String coloredNameplate = tagManager.buildNameplate(playerRef, baseName, uuid);

            // Strip color codes so they don't render as raw §7 etc. in the UI
            previewText = ColorFormatter.stripFormatting(coloredNameplate);

            // Try to derive a nice color from the player's rank or active tag
            if (uuid != null) {
                // Rank prefix from integrations (may contain &#RRGGBB or &x codes)
                String rankPrefix = TagManager.get().getIntegrations().getLuckPermsPrefix(uuid);
                previewHex = ColorFormatter.extractFirstHexColor(rankPrefix);

                if (previewHex == null) {
                    // Fallback to active tag color
                    TagDefinition active = tagManager.getEquipped(uuid);
                    if (active != null) {
                        previewHex = ColorFormatter.extractFirstHexColor(active.getDisplay());
                    }
                }
            }
        } catch (Throwable ignored) {
            // Fallback: just show the raw username if something goes wrong
            previewText = playerRef.getUsername();
        }

        // Apply preview text + color
        cmd.set("#CurrentNameplateLabel.Text", previewText);
        if (previewHex != null) {
            cmd.set("#CurrentNameplateLabel.Style.TextColor", "#" + previewHex);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {

        String action = data.action;
        if (action == null) return;

        switch (action) {
            case "close" -> this.close();

            case "prev_page" -> {
                int newPage = Math.max(0, currentPage - 1);
                if (newPage != currentPage) {
                    this.close();
                    reopenAtPage(ref, store, newPage);
                }
            }

            case "next_page" -> {
                int newPage = currentPage + 1; // build() clamps
                this.close();
                reopenAtPage(ref, store, newPage);
            }

            case "tag_click" -> {
                if (uuid == null) return;
                String tagId = data.tagId;
                if (tagId == null) return;

                TagPurchaseResult result = TagPurchaseResult.NOT_FOUND;

                try {
                    TagManager manager = TagManager.get();
                    TagDefinition def = manager.getTag(tagId);

                    // 1) Toggle ownership/equip state
                    result = manager.toggleTag(playerRef, uuid, tagId);

                    // 2) Update nameplate on the world thread (we have store + ref here)
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        String baseName = playerRef.getUsername(); // raw username as base

                        try {
                            switch (result) {
                                case UNLOCKED_FREE:
                                case UNLOCKED_PAID:
                                case EQUIPPED_ALREADY_OWNED: {
                                    // Always use plain nameplate for the actual Nameplate component
                                    String text = manager.buildPlainNameplate(playerRef, baseName, uuid);
                                    NameplateManager.get().apply(uuid, store, ref, text);
                                    break;
                                }
                                case UNEQUIPPED: {
                                    // Reapply with the rank rebuilt at the end
                                    NameplateManager.get().restore(uuid, store, ref, baseName);
                                    String text = manager.buildPlainNameplate(playerRef, baseName, uuid);
                                    NameplateManager.get().apply(uuid, store, ref, text);
                                    break;
                                }
                                default:
                                    // NO_PERMISSION, NOT_ENOUGH_MONEY, etc – leave nameplate alone
                                    break;
                            }
                        } catch (Throwable ignored) {
                            // Never let a nameplate failure kill the UI
                        }
                    }

                    // 3) Notify the player (with proper colors)
                    handlePurchaseResult(result, def);

                } catch (Throwable t) {
                    LOGGER.at(Level.WARNING)
                            .withCause(t)
                            .log("[MysticNameTags] Failed to handle tag_click for " + tagId);
                }

                // 4) Rebuild the page dynamically using sendUpdate, so the UI refreshes
                //    without closing and the loading overlay clears.
                UICommandBuilder updateCmd = new UICommandBuilder();
                UIEventBuilder updateEvt   = new UIEventBuilder();

                // We do NOT need to register row events again here
                rebuildPage(ref, store, updateCmd, updateEvt, false);

                sendUpdate(updateCmd, null, false);
            }
        }
    }

    private void reopenAtPage(@Nonnull Ref<EntityStore> ref,
                              @Nonnull Store<EntityStore> store,
                              int page) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        MysticNameTagsTagsUI newPage = new MysticNameTagsTagsUI(playerRef, uuid, page);
        player.getPageManager().openCustomPage(ref, store, newPage);
    }

    private void handlePurchaseResult(TagPurchaseResult result, TagDefinition def) {
        // Build messages using &-style codes, then colorize once.
        String title = "&bMysticNameTags";

        String tagDisplay = def != null ? def.getDisplay() : "&ftag";
        String msg;

        switch (result) {
            case NOT_FOUND ->
                    msg = "&cThat tag no longer exists.";
            case NO_PERMISSION ->
                    msg = "&cYou don't have access to that tag.";
            case UNLOCKED_FREE ->
                    msg = "&aUnlocked &r" + tagDisplay + " &aand equipped!";
            case UNLOCKED_PAID ->
                    msg = "&aPurchased &r" + tagDisplay + " &aand equipped!";
            case EQUIPPED_ALREADY_OWNED ->
                    msg = "&aEquipped &r" + tagDisplay + "&a.";
            case UNEQUIPPED ->
                    msg = "&7Unequipped &r" + tagDisplay + "&7.";
            case NO_ECONOMY ->
                    msg = "&cEconomy plugin is not configured.";
            case NOT_ENOUGH_MONEY ->
                    msg = "&cYou cannot afford that tag.";
            case TRANSACTION_FAILED ->
                    msg = "&cTransaction failed. Please try again.";
            default ->
                    msg = "&cUnknown purchase result: " + result;
        }

        NotificationUtil.sendNotification(
                playerRef.getPacketHandler(),
                Message.raw(ColorFormatter.colorize(title)),
                Message.raw(ColorFormatter.colorize(msg)),
                NotificationStyle.Default
        );
    }

    // ---------------- Event data ----------------

    public static class UIEventData {

        public static final BuilderCodec<UIEventData> CODEC =
                BuilderCodec.builder(UIEventData.class, UIEventData::new)
                        .append(
                                new KeyedCodec<>("Action", Codec.STRING),
                                (e, v) -> e.action = v,
                                e -> e.action
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("TagId", Codec.STRING),
                                (e, v) -> e.tagId = v,
                                e -> e.tagId
                        )
                        .add()
                        .build();

        public String action;
        public String tagId;

        public UIEventData() {}
    }
}
