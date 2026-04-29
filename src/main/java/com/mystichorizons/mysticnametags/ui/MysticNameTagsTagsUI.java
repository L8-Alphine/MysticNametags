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
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.integrations.WiFlowPlaceholderSupport;
import com.mystichorizons.mysticnametags.tags.TagDefinition;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.tags.TagManager.TagPurchaseResult;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Tag selection / purchase UI.
 * <p>
 * Layout file: mysticnametags/Tags.ui
 */
public class MysticNameTagsTagsUI extends InteractiveCustomUIPage<MysticNameTagsTagsUI.UIEventData> {

    public static final String LAYOUT = "mysticnametags/Tags.ui";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int MAX_ROWS = 10;
    private static final int PAGE_SIZE = 10;

    private static final String COLOR_TEXT_PRIMARY = "#e6edf3";
    private static final String COLOR_TEXT_MUTED = "#6b7280";
    private static final String COLOR_TEXT_SELECTED = "#ffffff";
    private static final String COLOR_TEXT_CATEGORY = "#cbd5f5";
    private static final String COLOR_OUTLINE_ROW = "#3a3a3a";
    private static final String COLOR_OUTLINE_SELECT = "#58a6ff";
    /**
     * Last time (ms) a tag was successfully EQUIPPED for each player.
     * Used for enforcing the configurable equip cooldown.
     */
    private static final Map<UUID, Long> LAST_EQUIP_TIME = new ConcurrentHashMap<>();
    private final PlayerRef playerRef;
    private final UUID uuid;
    /**
     * Cache of canUseTag per tag id for the current player/session.
     * Cleared on filter/category changes and after purchase/equip actions.
     */
    private final Map<String, Boolean> canUseCache = new HashMap<>();
    private final boolean ownedOnly;
    private int currentPage;
    private String filterQuery;
    private String pendingFilterQuery;
    // 0 = All, 1..N = categories
    private int categoryIndex = 0;
    private long lastFilterApplyMs = 0L;
    private String cooldownWarningText;
    /**
     * The currently selected tag in the right-side detail panel.
     */
    private String selectedTagId;
    /**
     * Whether the help/info panel is currently visible.
     */
    private boolean detailHelpVisible = false;

    public MysticNameTagsTagsUI(@Nonnull PlayerRef playerRef, @Nonnull UUID uuid) {
        this(playerRef, uuid, 0, null, false);
    }

    public MysticNameTagsTagsUI(@Nonnull PlayerRef playerRef,
                                @Nonnull UUID uuid,
                                int page,
                                String filterQuery) {
        this(playerRef, uuid, page, filterQuery, false);
    }

    public MysticNameTagsTagsUI(@Nonnull PlayerRef playerRef,
                                @Nonnull UUID uuid,
                                int page,
                                String filterQuery,
                                boolean ownedOnly) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
        this.uuid = uuid;
        this.currentPage = Math.max(page, 0);
        this.filterQuery = normalizeFilter(filterQuery);
        this.pendingFilterQuery = this.filterQuery;
        this.ownedOnly = ownedOnly;
    }

    private static String normalizeFilter(String filter) {
        if (filter == null) return null;
        String trimmed = filter.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void addTerm(@Nonnull List<String> terms, @Nullable String value) {
        if (value == null) return;

        String stripped = ColorFormatter.stripFormatting(value);
        if (stripped != null) {
            stripped = stripped.trim();
            if (!stripped.isEmpty()) {
                terms.add(stripped);
            }
        }

        value = value.trim();
        if (!value.isEmpty() && (stripped == null || !value.equals(stripped))) {
            terms.add(value);
        }
    }

    @Nonnull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static boolean hasAnyRequirements(TagDefinition def) {
        if (def == null) return false;

        String perm = def.getPermission();
        boolean hasPerm = perm != null && !perm.isEmpty();

        boolean hasPlaytime = def.getRequiredPlaytimeMinutes() != null
                && def.getRequiredPlaytimeMinutes() > 0;

        List<String> required = def.getRequiredOwnedTags();
        boolean hasOwnedTags = required != null && !required.isEmpty();

        List<TagDefinition.StatRequirement> statReqs = def.getRequiredStats();
        boolean hasStats = statReqs != null && !statReqs.isEmpty();

        boolean hasItems = def.getRequiredItems() != null && !def.getRequiredItems().isEmpty();

        List<TagDefinition.PlaceholderRequirement> placeholderReqs = def.getPlaceholderRequirements();
        boolean hasPlaceholders = placeholderReqs != null && !placeholderReqs.isEmpty();

        return hasPerm || hasPlaytime || hasOwnedTags || hasStats || hasItems || hasPlaceholders;
    }

    @Nullable
    private static Double tryParseDouble(@Nullable String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nonnull
    private static String prettifyId(@Nonnull String rawId) {
        String id = rawId.trim();
        if (id.isEmpty()) return rawId;

        int colon = id.indexOf(':');
        if (colon >= 0 && colon < id.length() - 1) {
            id = id.substring(colon + 1);
        }

        id = id.replace('_', ' ').replace('-', ' ');

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
    private static String resolveItemDisplayName(@Nonnull String itemId) {
        try {
            ItemStack probe = new ItemStack(itemId, 1);
            Object item = probe.getItem();
            if (item != null) {
                for (String mName : new String[]{"getDisplayName", "getName", "getTitle"}) {
                    try {
                        Method m = item.getClass().getMethod(mName);
                        if (m.getReturnType() == String.class) {
                            String s = (String) m.invoke(item);
                            if (s != null && !s.isBlank()) return s;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return prettifyId(itemId);
    }

    @Nonnull
    private static String resolveKillEntityDisplayNameFromStatKey(@Nonnull String statKey) {
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

        boolean wildcard = entityPart.contains("*");
        String pretty = prettifyId(entityPart.replace("*", "").trim());

        if (!wildcard) {
            return pretty;
        }

        if (pretty.isBlank()) {
            return "Matching Entities";
        }

        return pretty + " (any)";
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {

        cmd.append(LAYOUT);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BottomCloseButton", EventData.of("Action", "close"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton", EventData.of("Action", "prev_page"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton", EventData.of("Action", "next_page"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevCategoryButton", EventData.of("Action", "prev_category"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextCategoryButton", EventData.of("Action", "next_category"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SelectBtn", EventData.of("Action", "activate_selected"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DetailHelpButton", EventData.of("Action", "toggle_help"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#HowItWorksCloseButton", EventData.of("Action", "toggle_help"));

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ApplyFilterButton",
                EventData.of("Action", "apply_filter")
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearFilterButton",
                EventData.of("Action", "clear_filter")
        );

        rebuildPage(ref, store, cmd, evt, true);
    }

    private List<TagDefinition> createFilteredSnapshot() {
        TagManager tagManager = TagManager.get();
        Collection<TagDefinition> all = tagManager.getAllTags();

        boolean fullGate = Settings.get().isFullPermissionGateEnabled();
        boolean debugShowHidden = tagManager.isShowHiddenTagsForDebug();

        List<String> categories = tagManager.getCategories();
        String selectedCategory = null;

        if (!categories.isEmpty() && categoryIndex > 0 && categoryIndex <= categories.size()) {
            selectedCategory = categories.get(categoryIndex - 1);
        }

        String needle = (filterQuery != null) ? filterQuery.toLowerCase(Locale.ROOT) : null;
        List<TagDefinition> filtered = new ArrayList<>();

        for (TagDefinition def : all) {
            if (def == null) continue;

            if (ownedOnly) {
                if (uuid == null || def.getId() == null || !tagManager.ownsTag(uuid, def.getId())) {
                    continue;
                }
            }

            if (fullGate) {
                String perm = def.getPermission();
                if (perm != null && !perm.isEmpty()) {
                    boolean canUse = uuid != null && tagManager.canUseTag(playerRef, uuid, def);
                    if (!canUse && !debugShowHidden) continue;
                }
            }

            if (needle != null && !matchesFilter(def, needle, tagManager, fullGate)) {
                continue;
            }

            if (selectedCategory != null) {
                String defCat = def.getCategory();
                if (defCat == null || !defCat.equalsIgnoreCase(selectedCategory)) continue;
            }

            filtered.add(def);
        }

        return filtered;
    }

    private boolean matchesFilter(@Nonnull TagDefinition def,
                                  @Nonnull String needle,
                                  @Nonnull TagManager tagManager,
                                  boolean fullGate) {

        String id = safe(def.getId());
        String display = safe(def.getDisplay());
        String description = safe(def.getDescription());
        String category = safe(def.getCategory());
        String permission = safe(def.getPermission());

        String plainDisplay = safe(ColorFormatter.stripFormatting(display));
        String prettyId = prettifyId(id);

        boolean owns = uuid != null && id != null && !id.isBlank() && tagManager.ownsTag(uuid, id);
        boolean canUse = canUseTag(tagManager, def);

        TagDefinition equipped = tagManager.getEquipped(uuid);
        if (equipped == null) {
            equipped = tagManager.resolveActiveOrDefaultTag(uuid);
        }

        boolean isEquipped = equipped != null
                && equipped.getId() != null
                && id != null
                && equipped.getId().equalsIgnoreCase(id);

        boolean hasCost = def.isPurchasable() && def.getPrice() > 0.0D;
        boolean hasRequirements = hasAnyRequirements(def);
        boolean lockedByReq = hasRequirements && !canUse;
        boolean lockedByPerm = fullGate && permission != null && !permission.isBlank() && !canUse;
        boolean lockedByPay = hasCost && !owns;
        boolean locked = lockedByReq || lockedByPerm || lockedByPay;

        List<String> terms = new ArrayList<>();

        // Core textual fields
        addTerm(terms, id);
        addTerm(terms, prettyId);
        addTerm(terms, display);
        addTerm(terms, plainDisplay);
        addTerm(terms, description);
        addTerm(terms, ColorFormatter.stripFormatting(description));
        addTerm(terms, category);
        addTerm(terms, permission);

        // Searchable state words
        if (owns) {
            addTerm(terms, "owned");
            addTerm(terms, "unlocked");
        } else {
            addTerm(terms, "not owned");
            addTerm(terms, "locked");
        }

        if (isEquipped) {
            addTerm(terms, "equipped");
            addTerm(terms, "active");
            addTerm(terms, "selected");
        }

        if (hasCost) {
            addTerm(terms, "buy");
            addTerm(terms, "purchase");
            addTerm(terms, "purchasable");
            addTerm(terms, "paid");
            addTerm(terms, String.valueOf(def.getPrice()));
        } else {
            addTerm(terms, "free");
        }

        if (locked) {
            addTerm(terms, "locked");
        }

        if (lockedByReq || hasRequirements) {
            addTerm(terms, "requirements");
            addTerm(terms, "requirement");
        }

        if (def.getRequiredOwnedTags() != null && !def.getRequiredOwnedTags().isEmpty()) {
            addTerm(terms, "owned tag");
            addTerm(terms, "prerequisite");
            for (String reqTag : def.getRequiredOwnedTags()) {
                addTerm(terms, reqTag);
                addTerm(terms, resolveTagDisplayName(reqTag));
            }
        }

        if (def.getRequiredStats() != null && !def.getRequiredStats().isEmpty()) {
            addTerm(terms, "stat");
            addTerm(terms, "stats");
            for (TagDefinition.StatRequirement req : def.getRequiredStats()) {
                if (req == null || req.getKey() == null) continue;
                addTerm(terms, req.getKey());
                addTerm(terms, resolveStatDisplayName(req.getKey()));
            }
        }

        if (def.getRequiredItems() != null && !def.getRequiredItems().isEmpty()) {
            addTerm(terms, "item");
            addTerm(terms, "items");
            for (TagDefinition.ItemRequirement req : def.getRequiredItems()) {
                if (req == null || req.getItemId() == null) continue;
                addTerm(terms, req.getItemId());
                addTerm(terms, resolveItemDisplayName(req.getItemId()));
            }
        }

        if (def.getPlaceholderRequirements() != null && !def.getPlaceholderRequirements().isEmpty()) {
            addTerm(terms, "placeholder");
            addTerm(terms, "placeholders");
            for (TagDefinition.PlaceholderRequirement req : def.getPlaceholderRequirements()) {
                if (req == null) continue;
                addTerm(terms, req.getPlaceholder());
                addTerm(terms, req.getOperator());
                addTerm(terms, req.getValue());
            }
        }

        Integer mins = def.getRequiredPlaytimeMinutes();
        if (mins != null && mins > 0) {
            addTerm(terms, "playtime");
            addTerm(terms, "time");
            addTerm(terms, String.valueOf(mins));
        }

        String haystack = String.join(" ", terms).toLowerCase(Locale.ROOT);
        return haystack.contains(needle.toLowerCase(Locale.ROOT));
    }

    private void rebuildPage(@Nonnull Ref<EntityStore> ref,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull UICommandBuilder cmd,
                             @Nonnull UIEventBuilder evt,
                             boolean registerRowEvents) {

        LanguageManager lang = LanguageManager.get();
        TagManager tagManager = TagManager.get();
        IntegrationManager integrations = tagManager.getIntegrations();

        boolean fullGate = Settings.get().isFullPermissionGateEnabled();
        boolean debugShowHidden = tagManager.isShowHiddenTagsForDebug();

        List<TagDefinition> tags = createFilteredSnapshot();

        int totalTags = tags.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalTags / (double) PAGE_SIZE));
        if (currentPage > totalPages - 1) currentPage = totalPages - 1;

        int startIndex = currentPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalTags);

        cmd.set("#TitleLabel.Text", lang.tr("ui.tags.title"));
        cmd.set("#PlayerSectionTitle.Text", lang.tr("ui.tags.player_section"));
        cmd.set("#FiltersSectionTitle.Text", lang.tr("ui.tags.filters_section"));
        cmd.set("#AvailableTagsTitle.Text", lang.tr("ui.tags.list_section"));
        cmd.set("#DetailSectionTitle.Text", lang.tr("ui.tags.details_section"));
        cmd.set("#ProgressSectionTitle.Text", lang.tr("ui.tags.progress_section"));
        cmd.set("#RequirementsTitle.Text", lang.tr("ui.tags.requirements_title"));
        cmd.set("#HowItWorksTitle.Text", lang.getHowItWorksPanelTitle());
        cmd.set("#FooterCloseHint.Text", lang.tr("ui.tags.footer_close_hint"));

        cmd.set("#PlayerLabel.Text", lang.tr("ui.tags.label_player"));
        cmd.set("#BalancePrefixLabel.Text", lang.tr("ui.tags.label_balance"));
        cmd.set("#CurrentTagPrefixLabel.Text", lang.tr("ui.tags.label_current_tag"));
        cmd.set("#CategoryPrefixLabel.Text", lang.tr("ui.tags.label_category"));

        cmd.set("#DetailCategoryPrefix.Text", lang.tr("ui.tags.label_category"));
        cmd.set("#DetailPricePrefix.Text", lang.tr("ui.tags.label_price"));
        cmd.set("#DetailStatusPrefix.Text", lang.tr("ui.tags.label_status"));
        cmd.set("#DetailPreviewPrefix.Text", lang.tr("ui.tags.label_preview"));

        cmd.set("#ApplyFilterButton.Text", lang.tr("ui.common.apply"));
        cmd.set("#ClearFilterButton.Text", lang.tr("ui.common.clear"));
        cmd.set("#PrevPageButton.Text", lang.tr("ui.common.prev"));
        cmd.set("#NextPageButton.Text", lang.tr("ui.common.next"));
        cmd.set("#BottomCloseButton.Text", lang.tr("ui.common.close"));
        cmd.set("#HowItWorksCloseButton.Text", lang.tr("ui.common.close"));
        cmd.set("#DetailHelpButton.Text", lang.tr("ui.tags.button_help"));

        if (filterQuery != null) {
            cmd.set("#TagSearchBox.PlaceholderText",
                    lang.tr("ui.tags.search_filter_prefix", Map.of("filter", filterQuery)));
        } else {
            cmd.set("#TagSearchBox.PlaceholderText", lang.tr("ui.tags.search_placeholder"));
        }
        String fieldValue = pendingFilterQuery;
        if (fieldValue != null && fieldValue.startsWith("#")) {
            fieldValue = "";
        }
        cmd.set("#TagSearchBox.Value", fieldValue != null ? fieldValue : "");

        TagDefinition active = tagManager.getEquipped(uuid);
        if (active == null) {
            active = tagManager.resolveActiveOrDefaultTag(uuid);
        }
        String equippedId = (active != null) ? active.getId() : null;

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
                    usingCash = !usingPhysical
                            && Settings.get().isEconomySystemEnabled()
                            && Settings.get().isUseCoinSystem()
                            && integrations.isPrimaryEconomyAvailable();
                }
            }
        } catch (Throwable ignored) {
        }

        if (!econEnabled) {
            cmd.set("#BalanceLabel.Text", lang.tr("ui.tags.balance_na"));
        } else if (usingPhysical) {
            cmd.set("#BalanceLabel.Text",
                    lang.tr("ui.tags.balance_coins", Map.of("amount", String.valueOf((long) balance))));
        } else if (usingCash) {
            cmd.set("#BalanceLabel.Text",
                    lang.tr("ui.tags.balance_cash", Map.of("amount", String.valueOf((long) balance))));
        } else {
            cmd.set("#BalanceLabel.Text",
                    lang.tr("ui.tags.balance_coins", Map.of("amount", String.valueOf(balance))));
        }

        applyPlayerDisplay(cmd, active, lang);

        ensureValidSelection(tags, active);
        TagDefinition selected = resolveSelectedDefinition(tags);

        int row = 0;
        for (int i = startIndex; i < endIndex && row < MAX_ROWS; i++, row++) {
            TagDefinition def = tags.get(i);

            String cardSelector = "#TagRow" + row + "Card";
            String nameSelector = "#TagRow" + row + "Name";
            String priceSelector = "#TagRow" + row + "Price";
            String buttonSelector = "#TagRow" + row + "Button";
            String categoryPillSelector = "#TagRow" + row + "CategoryPill";
            String categorySelector = "#TagRow" + row + "Category";
            String stateSelector = "#TagRow" + row + "State";
            String stateBadgeSelector = "#TagRow" + row + "StateBadge";

            cmd.set(cardSelector + ".Visible", true);

            String rawDisplay = def.getDisplay();
            String nameText = ColorFormatter.stripFormatting(rawDisplay != null ? rawDisplay : def.getId());
            String nameHex = rawDisplay != null ? ColorFormatter.extractUiTextColor(rawDisplay) : null;
            String priceText = buildPriceText(def, econEnabled, usingCash, lang);

            cmd.set(nameSelector + ".Text", nameText);
            cmd.set(priceSelector + ".Text", priceText);

            boolean canUse = canUseTag(tagManager, def);
            boolean isEquipped = equippedId != null && equippedId.equalsIgnoreCase(def.getId());
            boolean owns = uuid != null && def.getId() != null && tagManager.ownsTag(uuid, def.getId());
            boolean hasCost = def.isPurchasable() && def.getPrice() > 0.0D;

            String perm = def.getPermission();
            boolean isLockedByPerm = fullGate && perm != null && !perm.isEmpty() && !canUse;
            boolean isSelected = selected != null
                    && selected.getId() != null
                    && selected.getId().equalsIgnoreCase(def.getId());

            String resolvedNameColor = nameHex != null ? "#" + nameHex : COLOR_TEXT_PRIMARY;
            if (isLockedByPerm && debugShowHidden) {
                resolvedNameColor = COLOR_TEXT_MUTED;
            } else if (isSelected) {
                resolvedNameColor = COLOR_TEXT_SELECTED;
            }
            cmd.set(nameSelector + ".Style.TextColor", resolvedNameColor);

            String category = def.getCategory();
            if (category == null || category.trim().isEmpty()) {
                cmd.set(categoryPillSelector + ".Visible", false);
                cmd.set(categorySelector + ".Visible", false);
            } else {
                cmd.set(categoryPillSelector + ".Visible", true);
                cmd.set(categorySelector + ".Visible", true);
                cmd.set(categorySelector + ".Text", category);
                cmd.set(categorySelector + ".Style.TextColor", COLOR_TEXT_CATEGORY);
            }

            RowBadge badge = buildRowBadge(def, canUse, owns, isEquipped, hasCost);
            cmd.set(stateBadgeSelector + ".Visible", true);
            cmd.set(stateSelector + ".Text", badge.text);
            cmd.set(stateSelector + ".Style.TextColor", badge.textColor);

            cmd.set(cardSelector + ".OutlineSize", isSelected ? 2 : 1);
            cmd.set(cardSelector + ".OutlineColor", isSelected ? COLOR_OUTLINE_SELECT : COLOR_OUTLINE_ROW);

            cmd.set(nameSelector + ".Visible", true);
            cmd.set(priceSelector + ".Visible", true);
            cmd.set(buttonSelector + ".Visible", true);

            if (registerRowEvents) {
                EventData rowEvent = new EventData()
                        .append("Action", "select_tag")
                        .append("TagId", def.getId() != null ? def.getId() : "")
                        .append("RowIndex", String.valueOf(row));

                evt.addEventBinding(CustomUIEventBindingType.Activating, buttonSelector, rowEvent, false);
            }
        }

        for (; row < MAX_ROWS; row++) {
            String cardSelector = "#TagRow" + row + "Card";
            String nameSelector = "#TagRow" + row + "Name";
            String priceSelector = "#TagRow" + row + "Price";
            String buttonSelector = "#TagRow" + row + "Button";
            String categoryPillSelector = "#TagRow" + row + "CategoryPill";
            String categorySelector = "#TagRow" + row + "Category";
            String stateBadgeSelector = "#TagRow" + row + "StateBadge";

            cmd.set(cardSelector + ".Visible", false);
            cmd.set(nameSelector + ".Visible", false);
            cmd.set(priceSelector + ".Visible", false);
            cmd.set(buttonSelector + ".Visible", false);
            cmd.set(categoryPillSelector + ".Visible", false);
            cmd.set(categorySelector + ".Visible", false);
            cmd.set(stateBadgeSelector + ".Visible", false);
        }

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

        populateDetailPanel(cmd, selected, active, econEnabled, usingCash);
    }

    private void applyPlayerDisplay(@Nonnull UICommandBuilder cmd,
                                    @Nullable TagDefinition active,
                                    @Nonnull LanguageManager lang) {

        String username = playerRef.getUsername() != null ? playerRef.getUsername() : "Player";

        String playerDisplayText = username;
        String playerDisplayHex = null;

        try {
            String fullNameplate = TagManager.get().buildNameplate(playerRef, username, uuid);
            String stripped = ColorFormatter.stripFormatting(fullNameplate);
            if (stripped != null && !stripped.isBlank()) {
                playerDisplayText = stripped;
            }

            String firstHex = ColorFormatter.extractFirstHexColor(fullNameplate);
            if (firstHex != null && !firstHex.isBlank()) {
                playerDisplayHex = firstHex;
            }
        } catch (Throwable ignored) {
        }

        cmd.set("#PlayerNameLabel.Text", playerDisplayText);
        if (playerDisplayHex != null) {
            cmd.set("#PlayerNameLabel.Style.TextColor", "#" + playerDisplayHex);
        } else {
            cmd.set("#PlayerNameLabel.Style.TextColor", COLOR_TEXT_PRIMARY);
        }

        String currentTagText = lang.tr("ui.tags.current_tag_none");
        String currentTagHex = null;

        if (active != null) {
            String activeDisplay = active.getDisplay();
            if (activeDisplay != null && !activeDisplay.isBlank()) {
                currentTagText = ColorFormatter.stripFormatting(activeDisplay);
                String hex = ColorFormatter.extractUiTextColor(activeDisplay);
                if (hex == null) {
                    hex = ColorFormatter.extractFirstHexColor(activeDisplay);
                }
                currentTagHex = hex;
            } else if (active.getId() != null && !active.getId().isBlank()) {
                currentTagText = prettifyId(active.getId());
            }
        }

        cmd.set("#CurrentNameplateLabel.Text", currentTagText);
        if (currentTagHex != null) {
            cmd.set("#CurrentNameplateLabel.Style.TextColor", "#" + currentTagHex);
        } else {
            cmd.set("#CurrentNameplateLabel.Style.TextColor", COLOR_TEXT_PRIMARY);
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
                detailHelpVisible = false;
                refresh(ref, store);
            }

            case "next_page" -> {
                List<TagDefinition> tags = createFilteredSnapshot();
                int totalPages = Math.max(1, (int) Math.ceil(tags.size() / (double) PAGE_SIZE));
                if (currentPage >= totalPages - 1) return;
                currentPage++;
                detailHelpVisible = false;
                refresh(ref, store);
            }

            case "filter_changed" -> {
                pendingFilterQuery = normalizeFilter(data.filter);
            }

            case "apply_filter" -> {
                long now = System.currentTimeMillis();
                if (now - lastFilterApplyMs < 200) return;
                lastFilterApplyMs = now;

                String incoming = normalizeFilter(data.filter);
                if (incoming != null && incoming.startsWith("#")) {
                    incoming = null;
                }

                String newFilter = incoming;
                if (!Objects.equals(this.filterQuery, newFilter)) {
                    this.filterQuery = newFilter;
                    this.pendingFilterQuery = newFilter;
                    this.currentPage = 0;
                    this.selectedTagId = null;
                    this.detailHelpVisible = false;
                    this.canUseCache.clear();
                }

                refresh(ref, store);
            }

            case "clear_filter" -> {
                long now = System.currentTimeMillis();
                if (now - lastFilterApplyMs < 200) return;
                lastFilterApplyMs = now;

                this.pendingFilterQuery = null;
                this.filterQuery = null;
                this.currentPage = 0;
                this.selectedTagId = null;
                this.detailHelpVisible = false;
                this.canUseCache.clear();

                refresh(ref, store);
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
                selectedTagId = null;
                detailHelpVisible = false;
                canUseCache.clear();
                refresh(ref, store);
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
                selectedTagId = null;
                detailHelpVisible = false;
                canUseCache.clear();
                refresh(ref, store);
            }

            case "select_tag" -> {
                if (data.tagId == null || data.tagId.isEmpty()) return;
                selectedTagId = data.tagId;
                detailHelpVisible = false;
                refresh(ref, store);
            }

            case "toggle_help" -> {
                detailHelpVisible = !detailHelpVisible;
                refresh(ref, store);
            }

            case "activate_selected" -> activateSelectedTag(ref, store);
        }
    }

    private void activateSelectedTag(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull Store<EntityStore> store) {

        if (uuid == null || selectedTagId == null || selectedTagId.isEmpty()) {
            return;
        }

        TagManager manager = TagManager.get();
        TagDefinition def = manager.getTag(selectedTagId);
        if (def == null || def.getId() == null || def.getId().isEmpty()) {
            return;
        }

        String resolvedId = def.getId();

        int delaySeconds = Settings.get().getTagEquipDelaySeconds();
        boolean ownsBefore = manager.ownsTag(uuid, resolvedId);

        if (delaySeconds > 0 && ownsBefore) {
            TagDefinition currentlyEquipped = manager.getEquipped(uuid);
            boolean isCurrentlyEquipped = currentlyEquipped != null
                    && resolvedId.equalsIgnoreCase(currentlyEquipped.getId());

            if (!isCurrentlyEquipped) {
                long now = System.currentTimeMillis();
                Long last = LAST_EQUIP_TIME.get(uuid);
                long minDelta = delaySeconds * 1000L;

                if (last != null && now - last < minDelta) {
                    long remainingMs = minDelta - (now - last);
                    long remainingSec = (remainingMs + 999L) / 1000L;

                    cooldownWarningText = LanguageManager.get().tr(
                            "tags.equip_cooldown",
                            Map.of("seconds", String.valueOf(remainingSec))
                    );

                    sendEquipCooldownNotification(remainingSec);
                    refresh(ref, store);
                    return;
                }
            }
        }

        try {
            TagPurchaseResult result = manager.toggleTag(playerRef, uuid, resolvedId);

            try {
                World world = manager.getOnlineWorld(uuid);
                if (world != null) {
                    manager.forceRefreshNameplate(playerRef, world);
                }
            } catch (Throwable ignored) {
            }

            switch (result) {
                case UNLOCKED_FREE:
                case UNLOCKED_PAID:
                case EQUIPPED_ALREADY_OWNED:
                    LAST_EQUIP_TIME.put(uuid, System.currentTimeMillis());
                    break;
                default:
                    break;
            }

            handlePurchaseResult(result, def);
            cooldownWarningText = null;
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to handle activate_selected for " + resolvedId);
        }

        detailHelpVisible = false;
        canUseCache.clear();
        refresh(ref, store);
    }

    private void refresh(@Nonnull Ref<EntityStore> ref,
                         @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        rebuildPage(ref, store, cmd, evt, true);
        sendUpdate(cmd, evt, false);
    }

    private void ensureValidSelection(@Nonnull List<TagDefinition> tags,
                                      @Nullable TagDefinition active) {
        if (!tags.isEmpty()) {
            if (selectedTagId != null) {
                for (TagDefinition def : tags) {
                    if (def != null && def.getId() != null && def.getId().equalsIgnoreCase(selectedTagId)) {
                        return;
                    }
                }
            }

            if (active != null && active.getId() != null) {
                for (TagDefinition def : tags) {
                    if (def != null && def.getId() != null && def.getId().equalsIgnoreCase(active.getId())) {
                        selectedTagId = active.getId();
                        return;
                    }
                }
            }

            TagDefinition first = tags.get(0);
            selectedTagId = first != null ? first.getId() : null;
            return;
        }

        selectedTagId = null;
    }

    @Nullable
    private TagDefinition resolveSelectedDefinition(@Nonnull List<TagDefinition> tags) {
        if (selectedTagId == null || selectedTagId.isEmpty()) return null;

        for (TagDefinition def : tags) {
            if (def != null && def.getId() != null && def.getId().equalsIgnoreCase(selectedTagId)) {
                return def;
            }
        }
        return null;
    }

    private String buildPriceText(@Nonnull TagDefinition def,
                                  boolean econEnabled,
                                  boolean usingCash,
                                  @Nonnull LanguageManager lang) {
        if (!def.isPurchasable() || def.getPrice() <= 0.0D) {
            return lang.tr("ui.tags.price_free");
        } else if (!econEnabled) {
            return lang.tr("ui.tags.price_economy_disabled", Map.of("price", String.valueOf(def.getPrice())));
        } else if (usingCash) {
            return lang.tr("ui.tags.price_cash", Map.of("price", String.valueOf(def.getPrice())));
        } else {
            return lang.tr("ui.tags.price_coins", Map.of("price", String.valueOf(def.getPrice())));
        }
    }

    private void populateDetailPanel(@Nonnull UICommandBuilder cmd,
                                     @Nullable TagDefinition def,
                                     @Nullable TagDefinition active,
                                     boolean econEnabled,
                                     boolean usingCash) {

        LanguageManager lang = LanguageManager.get();
        TagManager manager = TagManager.get();

        if (def == null) {
            cmd.set("#DetailEmpty.Visible", true);
            cmd.set("#DetailContent.Visible", false);
            cmd.set("#RequirementsPanel.Visible", false);
            cmd.set("#HowItWorksPopup.Visible", false);
            cmd.set("#RequirementsText.Text", "");
            cmd.set("#HowItWorksText.Text", "");
            return;
        }

        boolean canUse = canUseTag(manager, def);
        boolean owns = uuid != null && def.getId() != null && manager.ownsTag(uuid, def.getId());
        boolean isEquipped = active != null
                && active.getId() != null
                && def.getId() != null
                && active.getId().equalsIgnoreCase(def.getId());

        boolean hasCost = def.isPurchasable() && def.getPrice() > 0.0D;

        String detailName = ColorFormatter.stripFormatting(def.getDisplay() != null ? def.getDisplay() : def.getId());
        String detailNameHex = def.getDisplay() != null ? ColorFormatter.extractUiTextColor(def.getDisplay()) : null;

        String detailDesc = def.getDescription() != null
                ? ColorFormatter.stripFormatting(def.getDescription())
                : "";

        String category = (def.getCategory() != null && !def.getCategory().isBlank())
                ? def.getCategory()
                : lang.tr("ui.tags.category_none");

        String price = buildPriceText(def, econEnabled, usingCash, lang);

        String status;
        if (isEquipped) {
            status = lang.tr("ui.tags.detail_status_active");
        } else if (isLocked(def, canUse, owns)) {
            if (hasCost && !owns) {
                status = lang.tr("ui.tags.status_locked_not_purchased");
            } else {
                status = lang.tr("ui.tags.status_locked_requirements");
            }
        } else if (owns) {
            status = lang.tr("ui.tags.detail_status_owned");
        } else if (hasCost) {
            status = lang.tr("ui.tags.detail_status_purchasable");
        } else {
            status = lang.tr("ui.tags.detail_status_free");
        }

        String preview = detailName;
        String previewHex = detailNameHex;

        String selectButtonText;
        if (isEquipped) {
            selectButtonText = lang.tr("ui.tags.button_unequip");
        } else if (!def.isPurchasable() || def.getPrice() <= 0.0D) {
            selectButtonText = owns
                    ? lang.tr("ui.tags.button_equip")
                    : lang.tr("ui.tags.button_unlock");
        } else {
            selectButtonText = owns
                    ? lang.tr("ui.tags.button_equip")
                    : lang.tr("ui.tags.button_buy");
        }

        cmd.set("#DetailEmpty.Visible", false);
        cmd.set("#DetailContent.Visible", true);

        cmd.set("#DetailName.Text", detailName);
        if (detailNameHex != null) {
            cmd.set("#DetailName.Style.TextColor", "#" + detailNameHex);
        } else {
            cmd.set("#DetailName.Style.TextColor", COLOR_TEXT_PRIMARY);
        }

        cmd.set("#DetailLore.Text", def.getId() != null ? def.getId() : "");
        cmd.set("#DetailDesc.Text", detailDesc);
        cmd.set("#DetailCategory.Text", category);
        cmd.set("#DetailPrice.Text", price);
        cmd.set("#DetailStatus.Text", status);
        cmd.set("#DetailPreview.Text", preview);
        cmd.set("#SelectBtn.Text", selectButtonText);

        if (cooldownWarningText != null && !cooldownWarningText.isBlank()) {
            cmd.set("#CooldownWarning.Text", cooldownWarningText);
            cmd.set("#CooldownWarning.Visible", true);
        } else {
            cmd.set("#CooldownWarning.Text", "");
            cmd.set("#CooldownWarning.Visible", false);
        }

        if (previewHex != null) {
            cmd.set("#DetailPreview.Style.TextColor", "#" + previewHex);
        } else {
            cmd.set("#DetailPreview.Style.TextColor", COLOR_TEXT_PRIMARY);
        }

        List<String> reqLines = buildRequirementLines(def, canUse, owns, econEnabled, usingCash);
        boolean showReq = !reqLines.isEmpty();

        cmd.set("#RequirementsPanel.Visible", showReq);
        cmd.set("#RequirementsText.Text", toLineBlock(reqLines));

        List<String> helpLines = buildHelpLines(def, canUse, owns, isEquipped, hasCost);
        cmd.set("#HowItWorksTitle.Text", lang.getHowItWorksPanelTitle());
        cmd.set("#HowItWorksPopup.Visible", detailHelpVisible);
        cmd.set("#HowItWorksText.Text", toLineBlock(helpLines));

        double progress = calculateProgressFraction(def, canUse, owns);
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        cmd.set("#DetailProgressBar.Value", progress);
        cmd.set("#DetailProgressText.Text", buildProgressText(def, progress, canUse, owns));
    }

    @Nonnull
    private String toLineBlock(@Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();

            if (!first) {
                sb.append("\n");
            }
            sb.append(trimmed);
            first = false;
        }

        return sb.toString();
    }

    private boolean canUseTag(@Nonnull TagManager tagManager, @Nonnull TagDefinition def) {
        if (uuid == null || def.getId() == null) return false;
        return canUseCache.computeIfAbsent(def.getId(), id -> tagManager.canUseTag(playerRef, uuid, def));
    }

    @Nonnull
    private RowBadge buildRowBadge(@Nonnull TagDefinition def,
                                   boolean canUse,
                                   boolean owns,
                                   boolean isEquipped,
                                   boolean hasCost) {
        LanguageManager lang = LanguageManager.get();

        if (isEquipped) {
            return new RowBadge(lang.tr("ui.tags.badge_active"), "#3fb950");
        }

        if (isLocked(def, canUse, owns)) {
            return new RowBadge(lang.tr("ui.tags.badge_locked"), "#f85149");
        }

        if (owns) {
            return new RowBadge(lang.tr("ui.tags.badge_owned"), "#58a6ff");
        }

        if (hasCost) {
            return new RowBadge(lang.tr("ui.tags.badge_buy"), "#f0b429");
        }

        return new RowBadge(lang.tr("ui.tags.badge_free"), "#c9d1d9");
    }

    private boolean isLocked(TagDefinition def, boolean canUse, boolean owns) {
        if (def == null) return false;

        boolean lockedByReq = hasAnyRequirements(def) && !canUse;
        boolean hasCost = def.isPurchasable() && def.getPrice() > 0.0D;
        boolean lockedByPay = hasCost && !owns;

        return lockedByReq || lockedByPay;
    }

    @Nonnull
    private List<String> buildRequirementLines(TagDefinition def,
                                               boolean canUse,
                                               boolean owns,
                                               boolean econEnabled,
                                               boolean usingCash) {

        LanguageManager lang = LanguageManager.get();
        List<String> lines = new ArrayList<>();

        String perm = def.getPermission();
        boolean permissionGate = Settings.get().isPermissionGateEnabled();
        boolean fullGate = Settings.get().isFullPermissionGateEnabled();

        if (perm != null && !perm.isEmpty() && !canUse) {
            String gateSuffix = "";
            if (fullGate) {
                gateSuffix = " (" + lang.tr("ui.tags.req_permission_gate_full") + ")";
            } else if (permissionGate) {
                gateSuffix = " (" + lang.tr("ui.tags.req_permission_gate_soft") + ")";
            }
            lines.add(lang.tr("ui.tags.req_permission_title") + ": " + perm + gateSuffix);
        }

        Integer mins = def.getRequiredPlaytimeMinutes();
        if (mins != null && mins > 0) {
            int currentMinutes = 0;

            if (uuid != null) {
                try {
                    Integer cur = TagManager.get().getIntegrations().getPlaytimeMinutes(uuid);
                    if (cur != null && cur > 0) {
                        currentMinutes = cur;
                    }
                } catch (Throwable ignored) {
                }
            }

            if (currentMinutes < mins) {
                lines.add(lang.tr("ui.tags.req_playtime_progress", Map.of(
                        "current", String.valueOf(currentMinutes),
                        "required", String.valueOf(mins)
                )));
            }
        }

        List<String> required = def.getRequiredOwnedTags();
        if (required != null) {
            for (String id : required) {
                if (id == null || id.isBlank()) continue;

                boolean have = (uuid != null) && TagManager.get().ownsTag(uuid, id);
                if (!have) {
                    lines.add(lang.tr("ui.tags.req_owned_missing", Map.of(
                            "tag", resolveTagDisplayName(id)
                    )));
                }
            }
        }

        List<TagDefinition.StatRequirement> statReqs = def.getRequiredStats();
        if (statReqs != null) {
            for (TagDefinition.StatRequirement req : statReqs) {
                if (req == null || !req.isValid()) continue;

                Integer current = null;
                try {
                    if (uuid != null) {
                        current = TagManager.get().getIntegrations().getStatValue(uuid, req.getKey());
                    }
                } catch (Throwable ignored) {
                }

                int currentVal = current != null ? current : 0;
                Integer min = req.getMin();
                if (min != null && currentVal < min) {
                    lines.add(lang.tr("ui.tags.req_stat_progress", Map.of(
                            "stat", resolveStatDisplayName(req.getKey()),
                            "current", String.valueOf(currentVal),
                            "required", String.valueOf(min)
                    )));
                }
            }
        }

        List<TagDefinition.ItemRequirement> items = def.getRequiredItems();
        if (items != null) {
            for (TagDefinition.ItemRequirement req : items) {
                if (req == null || req.getItemId() == null || req.getItemId().isBlank()) continue;
                lines.add(lang.tr("ui.tags.req_item_line", Map.of(
                        "amount", String.valueOf(req.getAmount()),
                        "item", resolveItemDisplayName(req.getItemId())
                )));
            }
        }

        List<TagDefinition.PlaceholderRequirement> phReqs = def.getPlaceholderRequirements();
        if (phReqs != null) {
            for (TagDefinition.PlaceholderRequirement req : phReqs) {
                if (req == null) continue;

                String ph = req.getPlaceholder();
                String op = req.getOperator();
                String val = req.getValue();

                if (ph == null || op == null || val == null) continue;

                String actual = null;
                try {
                    actual = TagManager.get().getIntegrations().resolvePlaceholderRequirement(playerRef, ph, op, val);
                } catch (Throwable ignored) {
                }

                boolean matched = false;
                if (actual != null) {
                    matched = evaluatePlaceholderRequirement(actual, op, val);
                }

                if (!matched) {
                    lines.add(lang.tr("ui.tags.req_placeholder_line", Map.of(
                            "placeholder", ph,
                            "operator", op,
                            "value", val
                    )));
                }
            }
        }

        boolean hasCost = def.isPurchasable() && def.getPrice() > 0.0D;
        if (hasCost && !owns) {
            String priceText = lang.tr(
                    usingCash ? "ui.tags.req_purchase_value_cash" : "ui.tags.req_purchase_value_coins",
                    Map.of("price", String.valueOf(def.getPrice()))
            );
            if (!econEnabled) {
                priceText += " " + lang.tr("ui.tags.req_purchase_missing_econ_suffix");
            }
            lines.add(lang.tr("ui.tags.req_purchase_title") + ": " + priceText);
        }

        return lines;
    }

    @Nonnull
    private List<String> buildHelpLines(@Nonnull TagDefinition def,
                                        boolean canUse,
                                        boolean owns,
                                        boolean isEquipped,
                                        boolean hasCost) {

        LanguageManager lang = LanguageManager.get();

        List<String> customLines = lang.getHowItWorksPanelLines();
        if (!customLines.isEmpty()) {
            return customLines;
        }

        List<String> lines = new ArrayList<>();

        lines.add(lang.tr("ui.tags.howitworks.inspect"));
        lines.add(lang.tr("ui.tags.howitworks.preview"));
        lines.add(lang.tr("ui.tags.howitworks.progress"));

        if (isEquipped) {
            lines.add("");
            lines.add(lang.tr("ui.tags.howitworks.active"));
            lines.add(lang.tr("ui.tags.howitworks.unequip"));
        } else if (owns) {
            lines.add("");
            lines.add(lang.tr("ui.tags.howitworks.owned"));
            lines.add(lang.tr("ui.tags.howitworks.equip"));
        } else if (hasCost) {
            lines.add("");
            lines.add(lang.tr("ui.tags.howitworks.purchase_required"));
            lines.add(lang.tr("ui.tags.howitworks.purchase_requirements"));
        } else {
            lines.add("");
            lines.add(lang.tr("ui.tags.howitworks.free_unlock"));
            lines.add(lang.tr("ui.tags.howitworks.unlock_when_met"));
        }

        if (!canUse && hasAnyRequirements(def)) {
            lines.add("");
            lines.add(lang.tr("ui.tags.howitworks.check_requirements"));
        } else {
            lines.add("");
            lines.add(lang.tr("ui.tags.howitworks.requirements_met"));
        }

        String category = def.getCategory();
        if (category != null && !category.isBlank()) {
            lines.add("");
            lines.add(lang.tr("ui.tags.howitworks.category_line", Map.of(
                    "category", category
            )));
        }

        String rawDesc = def.getDescription();
        if (rawDesc != null && !rawDesc.isBlank()) {
            String clean = ColorFormatter.stripFormatting(rawDesc).trim();
            if (!clean.isEmpty()) {
                lines.add(lang.tr("ui.tags.howitworks.description_line", Map.of(
                        "description", clean
                )));
            }
        }

        return lines;
    }

    private double calculateProgressFraction(@Nonnull TagDefinition def,
                                             boolean canUse,
                                             boolean owns) {

        int totalChecks = 0;
        int completed = 0;

        String perm = def.getPermission();
        if (perm != null && !perm.isEmpty()) {
            totalChecks++;
            if (canUse || !Settings.get().isFullPermissionGateEnabled()) {
                completed++;
            }
        }

        Integer mins = def.getRequiredPlaytimeMinutes();
        if (mins != null && mins > 0) {
            totalChecks++;

            int currentMinutes = 0;
            if (uuid != null) {
                try {
                    Integer cur = TagManager.get().getIntegrations().getPlaytimeMinutes(uuid);
                    if (cur != null) currentMinutes = cur;
                } catch (Throwable ignored) {
                }
            }

            if (currentMinutes >= mins) {
                completed++;
            }
        }

        List<String> required = def.getRequiredOwnedTags();
        if (required != null && !required.isEmpty()) {
            for (String id : required) {
                if (id == null || id.isBlank()) continue;

                totalChecks++;
                if (uuid != null && TagManager.get().ownsTag(uuid, id)) {
                    completed++;
                }
            }
        }

        List<TagDefinition.StatRequirement> statReqs = def.getRequiredStats();
        if (statReqs != null) {
            for (TagDefinition.StatRequirement req : statReqs) {
                if (req == null || !req.isValid()) continue;

                totalChecks++;

                try {
                    Integer val = null;
                    if (uuid != null) {
                        val = TagManager.get().getIntegrations().getStatValue(uuid, req.getKey());
                    }

                    Integer min = req.getMin();
                    if (val != null && min != null && val >= min) {
                        completed++;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        List<TagDefinition.ItemRequirement> items = def.getRequiredItems();
        if (items != null) {
            for (TagDefinition.ItemRequirement req : items) {
                if (req == null || req.getItemId() == null || req.getItemId().isBlank()) continue;
                totalChecks++;
            }
        }

        List<TagDefinition.PlaceholderRequirement> phReqs = def.getPlaceholderRequirements();
        if (phReqs != null) {
            for (TagDefinition.PlaceholderRequirement req : phReqs) {
                if (req == null) continue;
                totalChecks++;
            }
        }

        if (def.isPurchasable() && def.getPrice() > 0.0D) {
            totalChecks++;
            if (owns) {
                completed++;
            }
        }

        if (totalChecks <= 0) {
            return owns || canUse ? 1.0D : 0.0D;
        }

        return completed / (double) totalChecks;
    }

    @Nonnull
    private String buildProgressText(@Nonnull TagDefinition def,
                                     double progress,
                                     boolean canUse,
                                     boolean owns) {
        LanguageManager lang = LanguageManager.get();
        int percent = (int) Math.round(progress * 100.0D);

        if (isLocked(def, canUse, owns)) {
            return lang.tr("ui.tags.progress_percent", Map.of("percent", String.valueOf(percent)));
        }

        if (owns) {
            return lang.tr("ui.tags.progress_ready");
        }

        return lang.tr("ui.tags.progress_percent", Map.of("percent", String.valueOf(percent)));
    }

    private boolean evaluatePlaceholderRequirement(@Nonnull String actual,
                                                   @Nonnull String operator,
                                                   @Nonnull String expected) {
        String a = actual.trim();
        String op = operator.trim();
        String exp = expected.trim();

        if (op.equalsIgnoreCase("true")) {
            return "true".equalsIgnoreCase(a);
        }
        if (op.equalsIgnoreCase("false")) {
            return "false".equalsIgnoreCase(a);
        }

        Double aNum = tryParseDouble(a);
        Double eNum = tryParseDouble(exp);

        if (aNum != null && eNum != null) {
            return switch (op) {
                case "==" -> Double.compare(aNum, eNum) == 0;
                case "!=" -> Double.compare(aNum, eNum) != 0;
                case ">" -> aNum > eNum;
                case ">=" -> aNum >= eNum;
                case "<" -> aNum < eNum;
                case "<=" -> aNum <= eNum;
                default -> false;
            };
        }

        return switch (op) {
            case "==" -> a.equalsIgnoreCase(exp);
            case "!=" -> !a.equalsIgnoreCase(exp);
            case "contains" -> a.toLowerCase(Locale.ROOT).contains(exp.toLowerCase(Locale.ROOT));
            default -> false;
        };
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
    private String resolveStatDisplayName(@Nonnull String statKey) {
        String key = statKey.trim();
        if (key.isEmpty()) return statKey;

        boolean wildcard = key.contains("*");
        LanguageManager lang = LanguageManager.get();

        if (key.startsWith("endlessleveling.")) {
            String tail = key.substring("endlessleveling.".length());

            if (tail.equalsIgnoreCase("level")) {
                return lang.tr("ui.stats.endlessleveling.level");
            }

            if (tail.equalsIgnoreCase("xp")) {
                return lang.tr("ui.stats.endlessleveling.xp");
            }

            String lowerTail = tail.toLowerCase(Locale.ROOT);
            if (lowerTail.startsWith("skill.")) {
                String rawAttr = tail.substring("skill.".length());
                String pretty = prettifyId(rawAttr);

                String label = lang.tr(
                        "ui.stats.endlessleveling.skill_prefix",
                        Map.of("name", pretty)
                );

                if (!label.equals("ui.stats.endlessleveling.skill_prefix")) {
                    return label;
                }
                return "Skill Level: " + pretty;
            }

            return prettifyId(tail);
        }

        if (key.startsWith("custom.")) {
            String statPart = key.substring("custom.".length());
            String langKey = "ui.stats.custom." + statPart;

            String translated = lang.tr(langKey);
            if (!translated.equals(langKey)) {
                return translated;
            }
        }

        if (key.startsWith("killed.")) {
            String entityName = resolveKillEntityDisplayNameFromStatKey(key);

            if (wildcard) {
                entityName = entityName.replace("*", "").trim();
                if (entityName.endsWith("_")) {
                    entityName = entityName.substring(0, entityName.length() - 1).trim();
                }
                if (entityName.isEmpty()) {
                    entityName = "Matching Entities";
                } else {
                    entityName = entityName + " (any)";
                }
            }

            String translated = lang.tr("ui.stats.prefix.kills", Map.of("name", entityName));
            if (!translated.equals("ui.stats.prefix.kills")) {
                return translated;
            }
            return "Kills: " + entityName;
        }

        if (key.startsWith("mined.")) {
            String blockId = key.substring("mined.".length());
            String pretty = prettifyId(blockId);
            String translated = lang.tr("ui.stats.prefix.mined", Map.of("name", pretty));
            if (!translated.equals("ui.stats.prefix.mined")) {
                return translated;
            }
            return "Blocks Mined: " + pretty;
        }

        if (key.startsWith("placed.")) {
            String blockId = key.substring("placed.".length());
            String pretty = prettifyId(blockId);
            String translated = lang.tr("ui.stats.prefix.placed", Map.of("name", pretty));
            if (!translated.equals("ui.stats.prefix.placed")) {
                return translated;
            }
            return "Blocks Placed: " + pretty;
        }

        int dot = key.indexOf('.');
        if (dot >= 0 && dot < key.length() - 1) {
            String statPart = key.substring(dot + 1);
            return prettifyId(statPart);
        }

        return prettifyId(key);
    }

    private void handlePurchaseResult(TagPurchaseResult result, TagDefinition def) {
        LanguageManager lang = LanguageManager.get();

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
        String parsedMsg = WiFlowPlaceholderSupport.apply(playerRef, msg);

        parsedTitle = ColorFormatter.colorize(parsedTitle);
        parsedMsg = ColorFormatter.colorize(parsedMsg);

        MysticNotificationUtil.send(
                playerRef.getPacketHandler(),
                parsedTitle,
                parsedMsg,
                NotificationStyle.Default
        );
    }

    private void sendEquipCooldownNotification(long secondsLeft) {
        LanguageManager lang = LanguageManager.get();

        String title = "&b" + lang.tr("plugin.title");
        String msg = lang.tr("tags.equip_cooldown",
                Map.of("seconds", String.valueOf(secondsLeft)));

        String parsedTitle = WiFlowPlaceholderSupport.apply(playerRef, title);
        String parsedMsg = WiFlowPlaceholderSupport.apply(playerRef, msg);

        parsedTitle = ColorFormatter.colorize(parsedTitle);
        parsedMsg = ColorFormatter.colorize(parsedMsg);

        MysticNotificationUtil.send(
                playerRef.getPacketHandler(),
                parsedTitle,
                parsedMsg,
                NotificationStyle.Default
        );
    }

    private static final class RowBadge {
        final String text;
        final String textColor;

        private RowBadge(String text, String textColor) {
            this.text = text;
            this.textColor = textColor;
        }
    }

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

        public UIEventData() {
        }

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
