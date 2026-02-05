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
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
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

    // 10 rows per page
    private static final int MAX_ROWS  = 10;
    private static final int PAGE_SIZE = 10;

    private final PlayerRef playerRef;
    private final UUID uuid;

    // zero-based page index (mutable so we can flip pages without recreating the page)
    private int currentPage;

    // Optional filter applied server-side (case-insensitive, mutable)
    private String filterQuery;

    // Category: 0 = All, 1..N = actual categories from TagManager
    private int categoryIndex = 0;

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

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#PrevCategoryButton",
                EventData.of("Action", "prev_category")
        );
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NextCategoryButton",
                EventData.of("Action", "next_category")
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

        boolean fullGate        = Settings.get().isFullPermissionGateEnabled();
        boolean debugShowHidden = tagManager.isShowHiddenTagsForDebug();

        // Selected category (0 = All, 1..N = actual category list)
        List<String> categories = tagManager.getCategories();
        boolean hasCategories   = !categories.isEmpty();
        String selectedCategory = null;
        if (hasCategories && categoryIndex > 0 && categoryIndex <= categories.size()) {
            selectedCategory = categories.get(categoryIndex - 1);
        }

        // Fast path: no filter, no full gate, no category filter → dump everything
        if (!fullGate && filterQuery == null && selectedCategory == null) {
            return new ArrayList<>(all);
        }

        String needle = (filterQuery != null)
                ? filterQuery.toLowerCase(Locale.ROOT)
                : null;

        List<TagDefinition> filtered = new ArrayList<>();

        for (TagDefinition def : all) {
            if (def == null) continue;

            // 1) Full permission gate: hide tags the player can't use
            //    unless debug mode says "show hidden".
            if (fullGate) {
                String perm = def.getPermission();
                if (perm != null && !perm.isEmpty()) {
                    boolean canUse = uuid != null && tagManager.canUseTag(playerRef, uuid, def);

                    if (!canUse && !debugShowHidden) {
                        continue;
                    }
                }
            }

            // 2) Optional text filter (search)
            if (needle != null) {
                String id       = def.getId() != null ? def.getId() : "";
                String display  = def.getDisplay() != null ? def.getDisplay() : "";
                String descr    = def.getDescription() != null ? def.getDescription() : "";
                String category = def.getCategory() != null ? def.getCategory() : "";

                String plainDisplay = ColorFormatter.stripFormatting(display);

                String haystack = (id + " " + plainDisplay + " " + descr + " " + category)
                        .toLowerCase(Locale.ROOT);

                if (!haystack.contains(needle)) {
                    continue; // doesn't match filter
                }
            }

            // 3) Category filter (if something other than "All" selected)
            if (selectedCategory != null) {
                String defCat = def.getCategory();
                if (defCat == null || !defCat.equalsIgnoreCase(selectedCategory)) {
                    continue;
                }
            }

            filtered.add(def);
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
        IntegrationManager integrations = tagManager.getIntegrations();
        boolean fullGate        = Settings.get().isFullPermissionGateEnabled();
        boolean debugShowHidden = tagManager.isShowHiddenTagsForDebug();

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
        boolean usingCoins = false;

        try {
            if (uuid != null) {
                econEnabled = integrations.hasAnyEconomy();
                if (econEnabled) {
                    balance = integrations.getBalance(uuid);

                    usingCoins = Settings.get().isEconomySystemEnabled()
                            && Settings.get().isUseCoinSystem()
                            && integrations.isPrimaryEconomyAvailable();
                }
            }
        } catch (Throwable ignored) { }

        if (!econEnabled) {
            cmd.set("#BalanceLabel.Text", "Balance: N/A (no economy found)");
        } else if (usingCoins) {
            cmd.set("#BalanceLabel.Text", "Cash: " + (long) balance);
        } else {
            cmd.set("#BalanceLabel.Text", "Balance: " + balance);
        }

        // ---- Fill rows for this page ----
        int row = 0;
        for (int i = startIndex; i < endIndex && row < MAX_ROWS; i++, row++) {
            TagDefinition def = tags.get(i);

            String nameSelector           = "#TagRow" + row + "Name";
            String descSelector           = "#TagRow" + row + "Description";
            String priceSelector          = "#TagRow" + row + "Price";
            String buttonSelector         = "#TagRow" + row + "Button";
            String categoryPillSelector   = "#TagRow" + row + "CategoryPill";
            String categorySelector       = "#TagRow" + row + "Category";

            String rawDisplay      = def.getDisplay();
            String rawDescription  = def.getDescription();

            // ---- Name (with color extracted from codes) ----
            String nameText = ColorFormatter.stripFormatting(rawDisplay);
            String nameHex  = ColorFormatter.extractUiTextColor(rawDisplay);

            // ---- Description: strip color codes ----
            String descText = rawDescription != null
                    ? ColorFormatter.stripFormatting(rawDescription)
                    : "";

            // Soft length cap so long lore doesn’t blow the row
            if (descText.length() > 90) {
                descText = descText.substring(0, 87) + "...";
            }

            String descHex = (rawDescription != null)
                    ? ColorFormatter.extractFirstHexColor(rawDescription)
                    : null;

            // ---- Price text ----
            String priceText;
            if (!def.isPurchasable() || def.getPrice() <= 0.0D) {
                priceText = "Free";
            } else if (!econEnabled) {
                priceText = def.getPrice() + " (economy disabled)";
            } else if (usingCoins) {
                priceText = def.getPrice() + " Cash";
            } else {
                priceText = def.getPrice() + " Coins";
            }

            cmd.set(nameSelector + ".Text", nameText);
            cmd.set(descSelector + ".Text", descText);
            cmd.set(priceSelector + ".Text", priceText);

            // Override UI label colors if hex codes were present
            if (nameHex != null) {
                cmd.set(nameSelector + ".Style.TextColor", "#" + nameHex);
            }
            if (descHex != null) {
                cmd.set(descSelector + ".Style.TextColor", "#" + descHex);
            }

            // ---- Determine button label based on usage/equipped state ----
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

            boolean owns    = uuid != null && tagManager.ownsTag(uuid, def.getId());
            boolean isFree  = !def.isPurchasable() || def.getPrice() <= 0.0D;
            boolean hasCost = def.isPurchasable() && def.getPrice() > 0.0D;

            String buttonText;
            boolean isLockedByPerm = false;
            String perm = def.getPermission();

            // In Full Gate mode, permission is required to use the tag at all.
            if (fullGate && perm != null && !perm.isEmpty() && !canUse) {
                isLockedByPerm = true;
            }

            if (isLockedByPerm && debugShowHidden) {
                // Visible only for admin debugging.
                buttonText = "No access";

                // Grey the row + category so it's obvious this is just a debug ghost row.
                cmd.set(nameSelector + ".Style.TextColor", "#6b7280");
                cmd.set(descSelector + ".Style.TextColor", "#6b7280");
                cmd.set(categorySelector + ".Style.TextColor", "#9ca3af");
            } else {
                if (isEquipped) {
                    buttonText = "Unequip";
                } else if (isFree) {
                    if (!owns) {
                        buttonText = "Unlock";
                    } else {
                        buttonText = "Equip";
                    }
                } else if (hasCost) {
                    if (!owns) {
                        buttonText = "Buy";
                    } else {
                        buttonText = "Equip";
                    }
                } else {
                    buttonText = "Equip";
                }
            }

            cmd.set(buttonSelector + ".Text", buttonText);

            // ---- Category pill text + visibility ----
            String category = def.getCategory();
            if (category == null || category.trim().isEmpty()) {
                cmd.set(categoryPillSelector + ".Visible", false);
                cmd.set(categorySelector + ".Visible", false);
            } else {
                cmd.set(categoryPillSelector + ".Visible", true);
                cmd.set(categorySelector + ".Visible", true);
                cmd.set(categorySelector + ".Text", category);
                // reset to normal color in case previous state was "locked/grey"
                cmd.set(categorySelector + ".Style.TextColor", "#cbd5f5");
            }

            // Make sure the main fields for this row are visible when reused
            cmd.set(nameSelector + ".Visible", true);
            cmd.set(descSelector + ".Visible", true);
            cmd.set(priceSelector + ".Visible", true);
            cmd.set(buttonSelector + ".Visible", true);

            // Row button binding
            if (registerRowEvents) {
                EventData rowEvent = new EventData()
                        .append("Action", "tag_click")
                        .append("TagId", def.getId() != null ? def.getId() : "")
                        .append("RowIndex", String.valueOf(row));

                evt.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        buttonSelector,
                        rowEvent,
                        false
                );
            }
        }

        // Hide unused rows
        for (; row < MAX_ROWS; row++) {
            String nameSelector           = "#TagRow" + row + "Name";
            String descSelector           = "#TagRow" + row + "Description";
            String priceSelector          = "#TagRow" + row + "Price";
            String buttonSelector         = "#TagRow" + row + "Button";
            String categoryPillSelector   = "#TagRow" + row + "CategoryPill";
            String categorySelector       = "#TagRow" + row + "Category";

            cmd.set(nameSelector + ".Visible", false);
            cmd.set(descSelector + ".Visible", false);
            cmd.set(priceSelector + ".Visible", false);
            cmd.set(buttonSelector + ".Visible", false);
            cmd.set(categoryPillSelector + ".Visible", false);
            cmd.set(categorySelector + ".Visible", false);
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

        // ---- Category label + visibility ----
        List<String> categories = tagManager.getCategories();
        boolean hasCategories = !categories.isEmpty();

        String categoryLabel = "All";
        if (hasCategories && categoryIndex > 0 && categoryIndex <= categories.size()) {
            categoryLabel = categories.get(categoryIndex - 1);
        }

        cmd.set("#CategoryValueLabel.Text", categoryLabel);
        cmd.set("#CategoryValueLabel.Visible", hasCategories);
        cmd.set("#PrevCategoryButton.Visible", hasCategories);
        cmd.set("#NextCategoryButton.Visible", hasCategories);

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
                String rankPrefix = TagManager.get().getIntegrations().getPrimaryPrefix(uuid);
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
                sendUpdate(cmd, evt, false);
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
                sendUpdate(cmd, evt, false);
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
                sendUpdate(cmd, evt, false);
            }

            case "prev_category" -> {
                TagManager tagManager = TagManager.get();
                List<String> cats = tagManager.getCategories();
                int maxIndex = cats.isEmpty() ? 0 : cats.size(); // 0 = All, 1..maxIndex = categories

                if (maxIndex == 0) {
                    categoryIndex = 0; // no categories
                } else {
                    // rotate backwards in range [0, maxIndex]
                    categoryIndex--;
                    if (categoryIndex < 0) {
                        categoryIndex = maxIndex; // wrap from All → last category
                    }
                }

                currentPage = 0;
                canUseCache.clear();

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder evt = new UIEventBuilder();
                rebuildPage(ref, store, cmd, evt, true);
                sendUpdate(cmd, evt, false);
            }

            case "next_category" -> {
                TagManager tagManager = TagManager.get();
                List<String> cats = tagManager.getCategories();
                int maxIndex = cats.isEmpty() ? 0 : cats.size(); // 0 = All, 1..maxIndex = categories

                if (maxIndex == 0) {
                    categoryIndex = 0;
                } else {
                    // rotate forward in range [0, maxIndex]
                    categoryIndex++;
                    if (categoryIndex > maxIndex) {
                        categoryIndex = 0; // wrap back to All
                    }
                }

                currentPage = 0;
                canUseCache.clear();

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder evt = new UIEventBuilder();
                rebuildPage(ref, store, cmd, evt, true);
                sendUpdate(cmd, evt, false);
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
