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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.LanguageManager;
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
import java.lang.reflect.Method;
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

    private static final int MAX_ROWS  = 10;
    private static final int PAGE_SIZE = 10;

    private final PlayerRef playerRef;
    private final UUID uuid;

    private int currentPage;
    private String filterQuery;

    // 0 = All, 1..N = categories
    private int categoryIndex = 0;

    private long lastFilterApplyMs = 0L;

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

        // Load layout once (supports localized override)
        cmd.append(LanguageManager.get().resolveUi(LAYOUT));

        // Static button bindings
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BottomCloseButton", EventData.of("Action", "close"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",   EventData.of("Action", "prev_page"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",   EventData.of("Action", "next_page"));

        // Filter controls
        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ApplyFilterButton",
                new EventData()
                        .append("Action", "set_filter")
                        .append("Filter", "#TagSearchBox.Text")
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearFilterButton",
                new EventData()
                        .append("Action", "set_filter")
                        .append("Filter", "")
        );

        // Category controls
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevCategoryButton", EventData.of("Action", "prev_category"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextCategoryButton", EventData.of("Action", "next_category"));

        rebuildPage(ref, store, cmd, evt, true);
    }

    private List<TagDefinition> createFilteredSnapshot() {
        TagManager tagManager = TagManager.get();
        Collection<TagDefinition> all = tagManager.getAllTags();

        boolean fullGate        = Settings.get().isFullPermissionGateEnabled();
        boolean debugShowHidden = tagManager.isShowHiddenTagsForDebug();

        List<String> categories = tagManager.getCategories();
        boolean hasCategories   = !categories.isEmpty();
        String selectedCategory = null;

        if (hasCategories && categoryIndex > 0 && categoryIndex <= categories.size()) {
            selectedCategory = categories.get(categoryIndex - 1);
        }

        if (!fullGate && filterQuery == null && selectedCategory == null) {
            return new ArrayList<>(all);
        }

        String needle = (filterQuery != null) ? filterQuery.toLowerCase(Locale.ROOT) : null;
        List<TagDefinition> filtered = new ArrayList<>();

        for (TagDefinition def : all) {
            if (def == null) continue;

            // 1) Full permission gate
            if (fullGate) {
                String perm = def.getPermission();
                if (perm != null && !perm.isEmpty()) {
                    boolean canUse = uuid != null && tagManager.canUseTag(playerRef, uuid, def);
                    if (!canUse && !debugShowHidden) continue;
                }
            }

            // 2) Text filter
            if (needle != null) {
                String id       = def.getId() != null ? def.getId() : "";
                String display  = def.getDisplay() != null ? def.getDisplay() : "";
                String descr    = def.getDescription() != null ? def.getDescription() : "";
                String category = def.getCategory() != null ? def.getCategory() : "";

                String plainDisplay = ColorFormatter.stripFormatting(display);

                String haystack = (id + " " + plainDisplay + " " + descr + " " + category)
                        .toLowerCase(Locale.ROOT);

                if (!haystack.contains(needle)) continue;
            }

            // 3) Category filter
            if (selectedCategory != null) {
                String defCat = def.getCategory();
                if (defCat == null || !defCat.equalsIgnoreCase(selectedCategory)) continue;
            }

            filtered.add(def);
        }

        return filtered;
    }

    private void rebuildPage(@Nonnull Ref<EntityStore> ref,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull UICommandBuilder cmd,
                             @Nonnull UIEventBuilder evt,
                             boolean registerRowEvents) {

        LanguageManager lang = LanguageManager.get();

        TagManager tagManager = TagManager.get();
        IntegrationManager integrations = tagManager.getIntegrations();
        boolean fullGate        = Settings.get().isFullPermissionGateEnabled();
        boolean debugShowHidden = tagManager.isShowHiddenTagsForDebug();

        List<TagDefinition> tags = createFilteredSnapshot();

        int totalTags  = tags.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalTags / (double) PAGE_SIZE));

        if (currentPage > totalPages - 1) currentPage = totalPages - 1;

        cmd.set("#RequirementsPanel.Visible", false);

        int startIndex = currentPage * PAGE_SIZE;
        int endIndex   = Math.min(startIndex + PAGE_SIZE, totalTags);

        // Search box placeholder
        if (filterQuery != null) {
            cmd.set("#TagSearchBox.PlaceholderText", lang.tr("ui.tags.search_filter_prefix", Map.of("filter", filterQuery)));
        } else {
            cmd.set("#TagSearchBox.PlaceholderText", lang.tr("ui.tags.search_placeholder"));
        }

        // Equipped tag id
        TagDefinition active = tagManager.getEquipped(uuid);
        if (active == null) {
            active = tagManager.resolveActiveOrDefaultTag(uuid); // expose or replicate logic
        }
        String equippedId = (active != null) ? active.getId() : null;

        // ---- Balance ----
        double balance = 0.0;
        boolean econEnabled = false;
        boolean usingCash = false;
        boolean usingPhysical = false;

        try {
            if (uuid != null) {
                econEnabled = integrations.hasAnyEconomy();
                usingPhysical = integrations.isUsingPhysicalCoins();

                if (econEnabled) {
                    balance = integrations.getBalance(playerRef, uuid);

                    // "Cash" only applies to your primary ledger economy
                    usingCash = !usingPhysical
                            && Settings.get().isEconomySystemEnabled()
                            && Settings.get().isUseCoinSystem()
                            && integrations.isPrimaryEconomyAvailable();
                }
            }
        } catch (Throwable ignored) { }

        if (!econEnabled) {
            cmd.set("#BalanceLabel.Text", lang.tr("ui.tags.balance_na"));
        } else if (usingPhysical) {
            cmd.set("#BalanceLabel.Text", lang.tr("ui.tags.balance_coins", Map.of("amount", String.valueOf((long) balance))));
        } else if (usingCash) {
            cmd.set("#BalanceLabel.Text", lang.tr("ui.tags.balance_cash", Map.of("amount", String.valueOf((long) balance))));
        } else {
            cmd.set("#BalanceLabel.Text", lang.tr("ui.tags.balance_coins", Map.of("amount", String.valueOf(balance))));
        }

        // ---- Rows ----
        int row = 0;
        for (int i = startIndex; i < endIndex && row < MAX_ROWS; i++, row++) {
            TagDefinition def = tags.get(i);

            String nameSelector         = "#TagRow" + row + "Name";
            String descSelector         = "#TagRow" + row + "Description";
            String priceSelector        = "#TagRow" + row + "Price";
            String buttonSelector       = "#TagRow" + row + "Button";
            String categoryPillSelector = "#TagRow" + row + "CategoryPill";
            String categorySelector     = "#TagRow" + row + "Category";
            String infoSelector         = "#TagRow" + row + "Info";

            String rawDisplay     = def.getDisplay();
            String rawDescription = def.getDescription();

            String nameText = ColorFormatter.stripFormatting(rawDisplay);
            String nameHex  = ColorFormatter.extractUiTextColor(rawDisplay);

            String descText = rawDescription != null ? ColorFormatter.stripFormatting(rawDescription) : "";
            if (descText.length() > 90) descText = descText.substring(0, 87) + "...";

            String descHex = (rawDescription != null) ? ColorFormatter.extractFirstHexColor(rawDescription) : null;

            // Price text
            String priceText;
            if (!def.isPurchasable() || def.getPrice() <= 0.0D) {
                priceText = lang.tr("ui.tags.price_free");
            } else if (!econEnabled) {
                priceText = lang.tr("ui.tags.price_economy_disabled", Map.of("price", String.valueOf(def.getPrice())));
            } else if (usingCash) {
                priceText = lang.tr("ui.tags.price_cash", Map.of("price", String.valueOf(def.getPrice())));
            } else {
                priceText = lang.tr("ui.tags.price_coins", Map.of("price", String.valueOf(def.getPrice())));
            }

            cmd.set(nameSelector + ".Text", nameText);
            cmd.set(descSelector + ".Text", descText);
            cmd.set(priceSelector + ".Text", priceText);

            if (nameHex != null) cmd.set(nameSelector + ".Style.TextColor", "#" + nameHex);
            if (descHex != null) cmd.set(descSelector + ".Style.TextColor", "#" + descHex);

            // canUse (cached)
            boolean canUse;
            Boolean cached = canUseCache.get(def.getId());
            if (cached != null) {
                canUse = cached;
            } else {
                canUse = tagManager.canUseTag(playerRef, uuid, def);
                canUseCache.put(def.getId(), canUse);
            }

            boolean isEquipped = equippedId != null && equippedId.equalsIgnoreCase(def.getId());
            boolean owns       = uuid != null && tagManager.ownsTag(uuid, def.getId());
            boolean isFree     = !def.isPurchasable() || def.getPrice() <= 0.0D;
            boolean hasCost    = def.isPurchasable() && def.getPrice() > 0.0D;

            String perm = def.getPermission();
            boolean isLockedByPerm = fullGate && perm != null && !perm.isEmpty() && !canUse;

            String buttonText;
            if (isLockedByPerm && debugShowHidden) {
                buttonText = lang.tr("ui.tags.button_no_access");

                cmd.set(nameSelector + ".Style.TextColor", "#6b7280");
                cmd.set(descSelector + ".Style.TextColor", "#6b7280");
                cmd.set(categorySelector + ".Style.TextColor", "#9ca3af");
            } else {
                if (isEquipped) {
                    buttonText = lang.tr("ui.tags.button_unequip");
                } else if (isFree) {
                    buttonText = (!owns) ? lang.tr("ui.tags.button_unlock") : lang.tr("ui.tags.button_equip");
                } else if (hasCost) {
                    buttonText = (!owns) ? lang.tr("ui.tags.button_buy") : lang.tr("ui.tags.button_equip");
                } else {
                    buttonText = lang.tr("ui.tags.button_equip");
                }
            }

            cmd.set(buttonSelector + ".Text", buttonText);

            boolean locked = isLocked(def, canUse, owns);
            cmd.set(infoSelector + ".Visible", locked);
            cmd.set(infoSelector + ".Text", lang.tr("ui.tags.info_icon"));

            // Category pill
            String category = def.getCategory();
            if (category == null || category.trim().isEmpty()) {
                cmd.set(categoryPillSelector + ".Visible", false);
                cmd.set(categorySelector + ".Visible", false);
            } else {
                cmd.set(categoryPillSelector + ".Visible", true);
                cmd.set(categorySelector + ".Visible", true);
                cmd.set(categorySelector + ".Text", category);
                cmd.set(categorySelector + ".Style.TextColor", "#cbd5f5");
            }

            cmd.set(nameSelector + ".Visible", true);
            cmd.set(descSelector + ".Visible", true);
            cmd.set(priceSelector + ".Visible", true);
            cmd.set(buttonSelector + ".Visible", true);

            if (registerRowEvents) {
                EventData rowEvent = new EventData()
                        .append("Action", "tag_click")
                        .append("TagId", def.getId() != null ? def.getId() : "")
                        .append("RowIndex", String.valueOf(row));

                EventData showReq = new EventData()
                        .append("Action", "hover_tag")
                        .append("TagId", def.getId() != null ? def.getId() : "");

                evt.addEventBinding(CustomUIEventBindingType.Activating, infoSelector, showReq, false);
                evt.addEventBinding(CustomUIEventBindingType.Activating, buttonSelector, rowEvent, false);
            }
        }

        // Hide unused rows
        for (; row < MAX_ROWS; row++) {
            String nameSelector         = "#TagRow" + row + "Name";
            String descSelector         = "#TagRow" + row + "Description";
            String priceSelector        = "#TagRow" + row + "Price";
            String buttonSelector       = "#TagRow" + row + "Button";
            String categoryPillSelector = "#TagRow" + row + "CategoryPill";
            String categorySelector     = "#TagRow" + row + "Category";
            String infoSelector         = "#TagRow" + row + "Info";

            cmd.set(infoSelector + ".Visible", false);

            cmd.set(nameSelector + ".Visible", false);
            cmd.set(descSelector + ".Visible", false);
            cmd.set(priceSelector + ".Visible", false);
            cmd.set(buttonSelector + ".Visible", false);
            cmd.set(categoryPillSelector + ".Visible", false);
            cmd.set(categorySelector + ".Visible", false);
        }

        // Page label + nav
        String label;
        if (totalTags == 0) {
            label = (filterQuery != null)
                    ? lang.tr("ui.tags.page_none_for_filter", Map.of("filter", filterQuery))
                    : lang.tr("ui.tags.page_none_defined");
        } else {
            label = lang.tr("ui.tags.page_label", Map.of(
                    "page", String.valueOf(currentPage + 1),
                    "pages", String.valueOf(totalPages)
            ));
            if (filterQuery != null) {
                label += "  " + lang.tr("ui.tags.page_filter_suffix", Map.of("filter", filterQuery));
            }
        }

        cmd.set("#PageLabel.Text", label);
        cmd.set("#PrevPageButton.Visible", totalTags > 0 && currentPage > 0);
        cmd.set("#NextPageButton.Visible", totalTags > 0 && currentPage < totalPages - 1);

        // Category label + visibility
        List<String> categories = tagManager.getCategories();
        boolean hasCategories = !categories.isEmpty();

        String categoryLabel = lang.tr("ui.tags.category_all");
        if (hasCategories && categoryIndex > 0 && categoryIndex <= categories.size()) {
            categoryLabel = categories.get(categoryIndex - 1);
        }

        cmd.set("#CategoryValueLabel.Text", categoryLabel);
        cmd.set("#CategoryValueLabel.Visible", hasCategories);
        cmd.set("#PrevCategoryButton.Visible", hasCategories);
        cmd.set("#NextCategoryButton.Visible", hasCategories);

        // Current nameplate preview
        String previewText;
        String previewHex = null;

        try {
            String baseName = playerRef.getUsername();
            String coloredNameplate = tagManager.buildNameplate(playerRef, baseName, uuid);
            previewText = ColorFormatter.stripFormatting(coloredNameplate);

            if (uuid != null) {
                String rankPrefix = TagManager.get().getIntegrations().getPrimaryPrefix(uuid);
                previewHex = ColorFormatter.extractFirstHexColor(rankPrefix);

                if (previewHex == null) {
                    if (active != null) previewHex = ColorFormatter.extractFirstHexColor(active.getDisplay());
                }
            }
        } catch (Throwable ignored) {
            previewText = playerRef.getUsername();
        }

        cmd.set("#CurrentNameplateLabel.Text", previewText);
        if (previewHex != null) cmd.set("#CurrentNameplateLabel.Style.TextColor", "#" + previewHex);
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
                List<TagDefinition> tags = createFilteredSnapshot();
                int totalPages = Math.max(1, (int) Math.ceil(tags.size() / (double) PAGE_SIZE));
                if (currentPage >= totalPages - 1) return;

                currentPage++;

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder evt = new UIEventBuilder();
                rebuildPage(ref, store, cmd, evt, true);
                sendUpdate(cmd, evt, false);
            }

            case "filter_changed" -> filterQuery = normalizeFilter(data.filter);

            case "set_filter" -> {
                long now = System.currentTimeMillis();
                if (now - lastFilterApplyMs < 200) return;
                lastFilterApplyMs = now;

                String requested = data.filter; // now always passed from Apply/Clear
                String newFilter = normalizeFilter(requested);

                if (!Objects.equals(this.filterQuery, newFilter)) {
                    this.filterQuery = newFilter;
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
                int maxIndex = cats.isEmpty() ? 0 : cats.size();

                if (maxIndex == 0) {
                    categoryIndex = 0;
                } else {
                    categoryIndex--;
                    if (categoryIndex < 0) categoryIndex = maxIndex;
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
                int maxIndex = cats.isEmpty() ? 0 : cats.size();

                if (maxIndex == 0) {
                    categoryIndex = 0;
                } else {
                    categoryIndex++;
                    if (categoryIndex > maxIndex) categoryIndex = 0;
                }

                currentPage = 0;
                canUseCache.clear();

                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder evt = new UIEventBuilder();
                rebuildPage(ref, store, cmd, evt, true);
                sendUpdate(cmd, evt, false);
            }

            case "hover_tag" -> {
                try {
                    if (uuid == null) return;

                    TagManager manager = TagManager.get();
                    TagDefinition def = (data.tagId != null && !data.tagId.isEmpty()) ? manager.getTag(data.tagId) : null;
                    if (def == null) return;

                    boolean canUse = manager.canUseTag(playerRef, uuid, def);
                    boolean owns = manager.ownsTag(uuid, def.getId());

                    boolean econEnabled = manager.getIntegrations().hasAnyEconomy();
                    boolean usingCash = Settings.get().isEconomySystemEnabled()
                            && Settings.get().isUseCoinSystem()
                            && manager.getIntegrations().isPrimaryEconomyAvailable();

                    if (!isLocked(def, canUse, owns)) {
                        UICommandBuilder cmd = new UICommandBuilder();
                        cmd.set("#RequirementsPanel.Visible", false);
                        sendUpdate(cmd, new UIEventBuilder(), false);
                        return;
                    }

                    String title = ColorFormatter.stripFormatting(def.getDisplay() != null ? def.getDisplay() : def.getId());
                    String body = buildRequirementsBody(def, canUse, owns, econEnabled, usingCash);

                    UICommandBuilder cmd = new UICommandBuilder();
                    cmd.set("#RequirementsPanel.Visible", true);
                    cmd.set("#ReqTitleLabel.Text", title);
                    cmd.set("#ReqBodyLabel.Text", body);

                    sendUpdate(cmd, new UIEventBuilder(), false);
                }
                catch (Throwable t) {
                    LOGGER.at(Level.WARNING)
                            .withCause(t)
                            .log("[MysticNameTags] Error while handling hover_tag for %s", data.tagId);

                    UICommandBuilder cmd = new UICommandBuilder();
                    cmd.set("#RequirementsPanel.Visible", false);
                    sendUpdate(cmd, new UIEventBuilder(), false);
                }
            }

            case "hover_clear" -> {
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#RequirementsPanel.Visible", false);
                sendUpdate(cmd, new UIEventBuilder(), false);
            }

            case "tag_click" -> {
                if (uuid == null) return;

                TagManager manager = TagManager.get();

                TagDefinition def = null;
                String resolvedId = null;

                if (data.tagId != null && !data.tagId.isEmpty()) {
                    def = manager.getTag(data.tagId);
                    if (def != null) {
                        resolvedId = def.getId();
                    }
                }

                if (def == null) {
                    int rowIndex = data.rowIndex;
                    if (rowIndex < 0 || rowIndex >= MAX_ROWS) return;

                    List<TagDefinition> tags = createFilteredSnapshot();
                    int startIndex = currentPage * PAGE_SIZE;
                    int absIndex = startIndex + rowIndex;

                    if (absIndex < 0 || absIndex >= tags.size()) return;

                    def = tags.get(absIndex);
                    if (def == null || def.getId() == null || def.getId().isEmpty()) return;

                    resolvedId = def.getId();
                }

                if (resolvedId == null || resolvedId.isEmpty()) return;

                TagPurchaseResult result;

                try {
                    result = manager.toggleTag(playerRef, uuid, resolvedId);

                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        String baseName = playerRef.getUsername();
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
                                    break;
                            }
                        } catch (Throwable ignored) { }
                    }

                    handlePurchaseResult(result, def);

                } catch (Throwable t) {
                    LOGGER.at(Level.WARNING).withCause(t)
                            .log("[MysticNameTags] Failed to handle tag_click for " + resolvedId);
                }

                // IMPORTANT: local UI cache of canUseTag is now stale (perms/ownership changed)
                canUseCache.clear();

                UICommandBuilder updateCmd = new UICommandBuilder();
                UIEventBuilder updateEvt = new UIEventBuilder();
                rebuildPage(ref, store, updateCmd, updateEvt, true);
                sendUpdate(updateCmd, updateEvt, false);
            }
        }
    }

    private static boolean hasAnyRequirements(TagDefinition def) {
        if (def == null) return false;

        String perm = def.getPermission();
        boolean hasPerm = perm != null && !perm.isEmpty();

        boolean hasPlaytime = def.getRequiredPlaytimeMinutes() != null
                && def.getRequiredPlaytimeMinutes() > 0;

        List<String> required = def.getRequiredOwnedTags();
        boolean hasOwnedTags = required != null && !required.isEmpty();

        boolean hasStat = def.getRequiredStatKey() != null && !def.getRequiredStatKey().isBlank()
                && def.getRequiredStatValue() != null && def.getRequiredStatValue() > 0;

        boolean hasItems = def.getRequiredItems() != null && !def.getRequiredItems().isEmpty();

        return hasPerm || hasPlaytime || hasOwnedTags || hasStat || hasItems;
    }

    private boolean isLocked(TagDefinition def, boolean canUse, boolean owns) {
        if (def == null) return false;

        boolean lockedByReq = hasAnyRequirements(def) && !canUse;

        boolean hasCost = def.isPurchasable() && def.getPrice() > 0.0D;
        boolean lockedByPay = hasCost && !owns;

        return lockedByReq || lockedByPay;
    }

    private String buildRequirementsBody(TagDefinition def,
                                         boolean canUse,
                                         boolean owns,
                                         boolean econEnabled,
                                         boolean usingCash) {

        LanguageManager lang = LanguageManager.get();
        StringBuilder sb = new StringBuilder();

        // ----- Permission -----
        String perm = def.getPermission();
        if (perm != null && !perm.isEmpty()) {
            sb.append(lang.tr("ui.tags.req_permission_title"))
                    .append("\n  ").append(perm).append("\n\n");
        }

        // ----- Playtime -----
        Integer mins = def.getRequiredPlaytimeMinutes();
        if (mins != null) {
            sb.append(lang.tr("ui.tags.req_playtime_title"))
                    .append("\n  ")
                    .append(lang.tr("ui.tags.req_playtime_value", Map.of("minutes", String.valueOf(mins))))
                    .append("\n\n");
        }

        // ----- Owned Tags -----
        List<String> required = def.getRequiredOwnedTags();
        if (required != null && !required.isEmpty()) {
            sb.append(lang.tr("ui.tags.req_owned_title")).append("\n");
            for (String id : required) {
                if (id == null || id.isBlank()) continue;
                boolean have = (uuid != null) && TagManager.get().ownsTag(uuid, id);

                String pretty = resolveTagDisplayName(id);

                sb.append("  • ").append(have ? "+ " : "- ").append(pretty).append("\n");
            }
            sb.append("\n");
        }

        // ----- Stat requirement -----
        String statKey = def.getRequiredStatKey();
        Integer statValue = def.getRequiredStatValue();
        if (statKey != null && !statKey.isBlank() && statValue != null && statValue > 0) {

            String keyPretty = statKey;

            // If this is a kill stat, show pretty entity name instead of raw id
            // (but keep Player as "Player")
            if (statKey.startsWith("killed.")) {
                keyPretty = "Kills: " + resolveKillEntityDisplayNameFromStatKey(statKey);
            }

            sb.append(lang.tr("ui.tags.req_stat_title"))
                    .append("\n  ")
                    .append(lang.tr("ui.tags.req_stat_value", Map.of(
                            "key", keyPretty,
                            "value", String.valueOf(statValue)
                    )))
                    .append("\n\n");
        }

        // ----- Item requirements -----
        List<TagDefinition.ItemRequirement> items = def.getRequiredItems();
        if (items != null && !items.isEmpty()) {
            sb.append(lang.tr("ui.tags.req_items_title")).append("\n");
            for (TagDefinition.ItemRequirement req : items) {
                if (req == null || req.getItemId() == null || req.getItemId().isBlank()) continue;

                String prettyItem = resolveItemDisplayName(req.getItemId());

                sb.append("  • ")
                        .append(req.getAmount())
                        .append("+ ")
                        .append(prettyItem)
                        .append("\n");
            }
            sb.append("\n");
        }

        // ----- Purchase cost -----
        boolean hasCost = def.isPurchasable() && def.getPrice() > 0.0D;
        if (hasCost && !owns) {
            sb.append(lang.tr("ui.tags.req_purchase_title"))
                    .append("\n  ")
                    .append(lang.tr(usingCash ? "ui.tags.req_purchase_value_cash" : "ui.tags.req_purchase_value_coins",
                            Map.of("price", String.valueOf(def.getPrice()))));

            if (!econEnabled) {
                sb.append(" ").append(lang.tr("ui.tags.req_purchase_missing_econ_suffix"));
            }
            sb.append("\n\n");
        }

        // ----- Status line -----
        if (!canUse && hasAnyRequirements(def)) {
            sb.append(lang.tr("ui.tags.status_locked_requirements"));
        } else if (hasCost && !owns) {
            sb.append(lang.tr("ui.tags.status_locked_not_purchased"));
        } else {
            sb.append(lang.tr("ui.tags.status_available"));
        }

        return sb.toString().trim();
    }

    // =====================================================================
    // Pretty-name helpers for Requirements panel
    // =====================================================================

    @Nonnull
    private static String prettifyId(@Nonnull String rawId) {
        String id = rawId.trim();
        if (id.isEmpty()) return rawId;

        // strip namespace (hytale:goblin -> goblin)
        int colon = id.indexOf(':');
        if (colon >= 0 && colon < id.length() - 1) {
            id = id.substring(colon + 1);
        }

        // normalize separators
        id = id.replace('_', ' ').replace('-', ' ');

        // title-case words
        String[] parts = id.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.length() > 0 ? out.toString() : rawId;
    }

    @Nonnull
    private String resolveTagDisplayName(@Nonnull String tagId) {
        TagDefinition def = TagManager.get().getTag(tagId);
        if (def != null) {
            String disp = def.getDisplay();
            if (disp != null && !disp.isBlank()) {
                return ColorFormatter.stripFormatting(disp);
            }
            String id = def.getId();
            if (id != null && !id.isBlank()) return prettifyId(id);
        }
        return prettifyId(tagId);
    }

    @Nonnull
    private static String resolveItemDisplayName(@Nonnull String itemId) {
        // Try to get a real item name via API (reflection-safe),
        // fall back to prettified id.
        try {
            ItemStack probe = new ItemStack(itemId, 1);
            Object item = probe.getItem();
            if (item != null) {
                // Common patterns: getDisplayName(), getName(), getTitle(), etc.
                for (String mName : new String[]{"getDisplayName", "getName", "getTitle"}) {
                    try {
                        Method m = item.getClass().getMethod(mName);
                        if (m.getReturnType() == String.class) {
                            String s = (String) m.invoke(item);
                            if (s != null && !s.isBlank()) return s;
                        }
                    } catch (NoSuchMethodException ignored) { }
                }
            }
        } catch (Throwable ignored) {
            // invalid id / asset load issue / api mismatch
        }
        return prettifyId(itemId);
    }

    @Nonnull
    private static String resolveKillEntityDisplayNameFromStatKey(@Nonnull String statKey) {
        // Expecting keys like:
        //  - killed.Player
        //  - killed.hytale:goblin
        //  - killed.goblin
        String key = statKey.trim();
        if (key.isEmpty()) return statKey;

        String entityPart = key;
        int dot = key.indexOf('.');
        if (dot >= 0 && dot < key.length() - 1) {
            entityPart = key.substring(dot + 1);
        }

        if ("Player".equalsIgnoreCase(entityPart)) {
            return "Player";
        }

        return prettifyId(entityPart);
    }

    private void handlePurchaseResult(TagPurchaseResult result, TagDefinition def) {
        LanguageManager lang = LanguageManager.get();

        // Keep this colorized title, but make the text localizable
        String title = "&b" + lang.tr("plugin.title");

        String tagDisplay = def != null ? def.getDisplay() : "";

        String msgKey;
        Map<String, String> vars;

        switch (result) {
            case NOT_FOUND -> {
                msgKey = "tags.not_found";
                vars = Map.of();
            }
            case NO_PERMISSION -> {
                msgKey = "tags.no_permission";
                vars = Map.of();
            }
            case UNLOCKED_FREE -> {
                msgKey = "tags.unlocked_free";
                vars = Map.of("tag", tagDisplay);
            }
            case UNLOCKED_PAID -> {
                msgKey = "tags.unlocked_paid";
                vars = Map.of("tag", tagDisplay);
            }
            case EQUIPPED_ALREADY_OWNED -> {
                msgKey = "tags.equipped";
                vars = Map.of("tag", tagDisplay);
            }
            case UNEQUIPPED -> {
                msgKey = "tags.unequipped";
                vars = Map.of("tag", tagDisplay);
            }
            case NO_ECONOMY -> {
                msgKey = "tags.no_economy";
                vars = Map.of();
            }
            case NOT_ENOUGH_MONEY -> {
                msgKey = "tags.not_enough_money";
                vars = Map.of();
            }
            case TRANSACTION_FAILED -> {
                msgKey = "tags.transaction_failed";
                vars = Map.of();
            }
            case REQUIREMENTS_NOT_MET -> {
                msgKey = "tags.requirements_not_met";
                vars = Map.of();
            }
            default -> {
                msgKey = "tags.unknown_result";
                vars = Map.of("result", String.valueOf(result));
            }
        }

        String msg = lang.tr(msgKey, vars);

        String parsedTitle = WiFlowPlaceholderSupport.apply(playerRef, title);
        String parsedMsg   = WiFlowPlaceholderSupport.apply(playerRef, msg);

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
                        .append(new KeyedCodec<>("Action", Codec.STRING),
                                (e, v) -> e.action = v,
                                e -> e.action)
                        .add()
                        .append(new KeyedCodec<>("TagId", Codec.STRING),
                                (e, v) -> e.tagId = v,
                                e -> e.tagId)
                        .add()
                        .append(new KeyedCodec<>("Filter", Codec.STRING),
                                (e, v) -> e.filter = v,
                                e -> e.filter)
                        .add()
                        .append(new KeyedCodec<>("RowIndex", Codec.STRING),
                                (e, v) -> e.rowIndex = parseRowIndex(v),
                                e -> (e.rowIndex >= 0 ? String.valueOf(e.rowIndex) : null))
                        .add()
                        .build();

        public String action;
        public String tagId;
        public String filter;
        public int rowIndex = -1;

        public UIEventData() {}

        private static int parseRowIndex(String value) {
            if (value == null || value.isEmpty()) return -1;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
    }
}
