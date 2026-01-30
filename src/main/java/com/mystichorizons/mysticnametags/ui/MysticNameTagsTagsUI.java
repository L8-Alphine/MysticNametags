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
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.integrations.WiFlowPlaceholderSupport;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;
import com.mystichorizons.mysticnametags.tags.TagDefinition;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.tags.TagManager.TagPurchaseResult;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;

/**
 * Tag selection / purchase UI.
 *
 * Layout file: mysticnametags/Tags.ui
 */
public class MysticNameTagsTagsUI extends InteractiveCustomUIPage<MysticNameTagsTagsUI.UIEventData> {

    public static final String LAYOUT = "mysticnametags/Tags.ui";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // 10 rows, 10 tags per page
    private static final int MAX_ROWS  = 10;
    private static final int PAGE_SIZE = 10;

    private final PlayerRef playerRef;
    private final UUID uuid;

    // zero-based page index (mutable so we can flip pages without recreating the page)
    private int currentPage;

    // Optional filter applied server-side (case-insensitive, mutable)
    private String filterQuery;

    // Per-page instance cache: tagId -> canUse
    private final Map<String, Boolean> canUseCache = new HashMap<>();

    public MysticNameTagsTagsUI(@Nonnull PlayerRef playerRef, @Nonnull UUID uuid) {
        this(playerRef, uuid, 0, null);
    }

    public MysticNameTagsTagsUI(@Nonnull PlayerRef playerRef,
                                @Nonnull UUID uuid,
                                int page,
                                String filterQuery) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.uuid = uuid;
        this.currentPage = Math.max(page, 0);
        this.filterQuery = normalizeFilter(filterQuery);
    }

    private static String normalizeFilter(String filter) {
        if (filter == null) return null;
        String trimmed = filter.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

        // "Apply" / "Clear" buttons just send "set_filter" with optional Filter text.
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ApplyFilterButton",
                EventData.of("Action", "set_filter")
        );
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearFilterButton",
                new EventData()
                        .append("Action", "set_filter")
                        .append("Filter", "")
        );

        // Fill the dynamic content (rows, balance, preview)
        rebuildPage(ref, store, cmd, evt, true);
    }

    /**
     * Logical snapshot of tags to use for this UI instance:
     * - filtered by filterQuery (if not null)
     * - stable order: underlying TagManager uses LinkedHashMap
     */
    private List<TagDefinition> createFilteredSnapshot() {
        TagManager tagManager = TagManager.get();
        Collection<TagDefinition> all = tagManager.getAllTags();

        if (filterQuery == null) {
            return new ArrayList<>(all);
        }

        String needle = filterQuery.toLowerCase(Locale.ROOT);
        List<TagDefinition> filtered = new ArrayList<>();

        for (TagDefinition def : all) {
            String id = def.getId() != null ? def.getId() : "";
            String display = def.getDisplay() != null ? def.getDisplay() : "";
            String descr   = def.getDescription() != null ? def.getDescription() : "";

            String plainDisplay = ColorFormatter.stripFormatting(display);

            String haystack =
                    (id + " " + plainDisplay + " " + descr).toLowerCase(Locale.ROOT);

            if (haystack.contains(needle)) {
                filtered.add(def);
            }
        }

        return filtered;
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

        // Snapshot of tags for this filter
        List<TagDefinition> tags = createFilteredSnapshot();

        int totalTags  = tags.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalTags / (double) PAGE_SIZE));

        // Clamp the current page in case tags changed
        if (currentPage > totalPages - 1) {
            currentPage = totalPages - 1;
        }

        int startIndex = currentPage * PAGE_SIZE;
        int endIndex   = Math.min(startIndex + PAGE_SIZE, totalTags);

        // Make sure search box reflects our current filter
        if (filterQuery != null) {
            cmd.set("#TagSearchBox.PlaceholderText", "Filter: " + filterQuery);
        } else {
            cmd.set("#TagSearchBox.PlaceholderText", "Search tags...");
        }

        // Equipped tag (by id) for this player
        String equippedId = null;
        if (uuid != null) {
            TagDefinition equipped = tagManager.getEquipped(uuid);
            if (equipped != null) {
                equippedId = equipped.getId();
            }
        }

        // ---- Economy status + balance (once per rebuild) ----
        double balance = 0.0;
        boolean econEnabled = false;
        try {
            if (uuid != null) {
                econEnabled = tagManager.getIntegrations().hasAnyEconomy();
                if (econEnabled) {
                    balance = tagManager.getIntegrations().getBalance(uuid);
                }
            }
        } catch (Throwable ignored) { }

        if (!econEnabled) {
            cmd.set("#BalanceLabel.Text", "Balance: N/A (no economy found)");
        } else {
            cmd.set("#BalanceLabel.Text", "Balance: " + balance);
        }

        // ---- Fill rows for this page ----
        int row = 0;
        for (int i = startIndex; i < endIndex && row < MAX_ROWS; i++, row++) {
            TagDefinition def = tags.get(i);

            String nameSelector   = "#TagRow" + row + "Name";
            String priceSelector  = "#TagRow" + row + "Price";
            String buttonSelector = "#TagRow" + row + "Button";

            String rawDisplay = def.getDisplay();

            // Plain text for UI label, but preserves Unicode symbols
            String nameText = ColorFormatter.stripFormatting(rawDisplay);
            String hex      = ColorFormatter.extractUiTextColor(rawDisplay);

            String priceText;
            if (!def.isPurchasable() || def.getPrice() <= 0.0D) {
                priceText = "Free";
            } else if (!econEnabled) {
                priceText = def.getPrice() + " coins (economy disabled)";
            } else {
                priceText = def.getPrice() + " coins";
            }

            cmd.set(nameSelector + ".Text", nameText);
            cmd.set(priceSelector + ".Text", priceText);

            if (hex != null) {
                cmd.set(nameSelector + ".Style.TextColor", "#" + hex);
            }

            // Determine button label based on usage/equipped state
            boolean canUse;
            Boolean cached = canUseCache.get(def.getId());
            if (cached != null) {
                canUse = cached;
            } else {
                canUse = tagManager.canUseTag(playerRef, uuid, def);
                canUseCache.put(def.getId(), canUse);
            }

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

            // Row button binding – only TagId, no RowIndex needed
            if (registerRowEvents) {
                EventData rowEvent = new EventData()
                        .append("Action", "tag_click")
                        .append("TagId", def.getId() != null ? def.getId() : "");

                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        buttonSelector,
                        rowEvent,
                        false // non-locking click for Equip/Purchase/Unequip
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
        String label;
        if (totalTags == 0) {
            label = (filterQuery != null)
                    ? "No tags found for filter \"" + filterQuery + "\""
                    : "No tags defined.";
        } else {
            label = "Page " + (currentPage + 1) + "/" + totalPages;
            if (filterQuery != null) {
                label += "  (filter: \"" + filterQuery + "\")";
            }
        }

        cmd.set("#PageLabel.Text", label);
        cmd.set("#PrevPageButton.Visible", totalTags > 0 && currentPage > 0);
        cmd.set("#NextPageButton.Visible", totalTags > 0 && currentPage < totalPages - 1);

        // ----- Current nameplate preview (colored) -----
        String previewText;
        String previewHex = null;

        try {
            String baseName = playerRef.getUsername();

            // This is the *actual* nameplate (with &/hex codes) used for chat/UI
            String coloredNameplate = tagManager.buildNameplate(playerRef, baseName, uuid);

            // Strip color codes so they don't render raw in the UI
            previewText = ColorFormatter.stripFormatting(coloredNameplate);

            // Try to derive a nice color from the player's rank or active tag
            if (uuid != null) {
                // Rank prefix from integrations (may contain &#RRGGBB or &x codes)
                String rankPrefix = TagManager.get().getIntegrations().getLuckPermsPrefix(uuid);
                previewHex = ColorFormatter.extractFirstHexColor(rankPrefix);

                if (previewHex == null) {
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
                if (currentPage <= 0) return;
                currentPage--;

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder evt = new UIEventBuilder();
                rebuildPage(ref, store, cmd, evt, true);
                sendUpdate(cmd, null, false);
            }

            case "next_page" -> {
                // Need snapshot to know max page index
                List<TagDefinition> tags = createFilteredSnapshot();
                int totalPages = Math.max(1, (int) Math.ceil(tags.size() / (double) PAGE_SIZE));
                if (currentPage >= totalPages - 1) return;

                currentPage++;

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder evt = new UIEventBuilder();
                rebuildPage(ref, store, cmd, evt, true);
                sendUpdate(cmd, null, false);
            }

            case "set_filter" -> {
                String newFilter = normalizeFilter(data.filter);

                // If filter changed, clear canUse cache + reset to page 0
                if (!Objects.equals(filterQuery, newFilter)) {
                    filterQuery = newFilter;
                    currentPage = 0;
                    canUseCache.clear();
                }

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder evt = new UIEventBuilder();
                rebuildPage(ref, store, cmd, evt, true);
                sendUpdate(cmd, null, false);
            }

            case "tag_click" -> {
                if (uuid == null) return;

                TagManager manager = TagManager.get();

                // Try to resolve by TagId first (most robust)
                TagDefinition def = null;
                String resolvedId = null;

                if (data.tagId != null && !data.tagId.isEmpty()) {
                    def = manager.getTag(data.tagId);
                    if (def != null) {
                        resolvedId = def.getId();
                    }
                }

                // Fallback to page+row index if TagId isn't present / couldn't be resolved
                if (def == null) {
                    int rowIndex = data.rowIndex;
                    if (rowIndex < 0 || rowIndex >= MAX_ROWS) {
                        return; // invalid / missing row
                    }

                    List<TagDefinition> tags = createFilteredSnapshot();
                    int startIndex = currentPage * PAGE_SIZE;
                    int absIndex = startIndex + rowIndex;

                    if (absIndex < 0 || absIndex >= tags.size()) {
                        return; // clicked on an empty row
                    }

                    def = tags.get(absIndex);
                    if (def == null || def.getId() == null || def.getId().isEmpty()) {
                        return;
                    }
                    resolvedId = def.getId();
                }

                if (resolvedId == null || resolvedId.isEmpty()) {
                    return;
                }

                TagPurchaseResult result = TagPurchaseResult.NOT_FOUND;

                try {
                    // 1) Toggle ownership/equip state
                    result = manager.toggleTag(playerRef, uuid, resolvedId);

                    // 2) Update nameplate on the world thread (we have store + ref here)
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        String baseName = playerRef.getUsername(); // raw username as base

                        try {
                            switch (result) {
                                case UNLOCKED_FREE:
                                case UNLOCKED_PAID:
                                case EQUIPPED_ALREADY_OWNED: {
                                    String text = manager.buildPlainNameplate(playerRef, baseName, uuid);
                                    NameplateManager.get().apply(uuid, store, ref, text);
                                    break;
                                }
                                case UNEQUIPPED: {
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
                            .log("[MysticNameTags] Failed to handle tag_click for " + resolvedId);
                }

                // 4) Rebuild the page dynamically so the UI refreshes
                UICommandBuilder updateCmd = new UICommandBuilder();
                UIEventBuilder updateEvt = new UIEventBuilder();

                // You can safely re-register row events here if you prefer:
                rebuildPage(ref, store, updateCmd, updateEvt, true);
                sendUpdate(updateCmd, updateEvt, false);
            }
        }
    }

    private void handlePurchaseResult(TagPurchaseResult result, TagDefinition def) {
        String title = "&bMysticNameTags";

        String tagDisplay = def != null ? def.getDisplay() : "";
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
                    msg = "&cEconomy plugin is not configured. &7This tag can only be unlocked with an economy plugin installed.";
            case NOT_ENOUGH_MONEY ->
                    msg = "&cYou cannot afford that tag.";
            case TRANSACTION_FAILED ->
                    msg = "&cTransaction failed. Please try again.";
            default ->
                    msg = "&cUnknown purchase result: " + result;
        }

        // 1) Let WiFlow expand {placeholders} if the API is there
        String parsedTitle = WiFlowPlaceholderSupport.apply(playerRef, title);
        String parsedMsg   = WiFlowPlaceholderSupport.apply(playerRef, msg);

        // 2) Then apply & + hex colors
        parsedTitle = ColorFormatter.colorize(parsedTitle);
        parsedMsg   = ColorFormatter.colorize(parsedMsg);

        MysticNotificationUtil.send(
                playerRef.getPacketHandler(),
                parsedTitle,
                parsedMsg,
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
                        .append(
                                new KeyedCodec<>("Filter", Codec.STRING),
                                (e, v) -> e.filter = v,
                                e -> e.filter
                        )
                        .add()
                        .append(
                                new KeyedCodec<>("RowIndex", Codec.STRING),
                                (e, v) -> e.rowIndex = parseRowIndex(v),
                                e -> (e.rowIndex >= 0 ? String.valueOf(e.rowIndex) : null)
                        )
                        .add()
                        .build();

        public String action;
        public String tagId;
        public String filter;
        public int rowIndex = -1;

        public UIEventData() {}

        private static int parseRowIndex(String value) {
            if (value == null || value.isEmpty()) {
                return -1;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
    }
}
