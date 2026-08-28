package com.mystichorizons.mysticnametags.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.tags.TagDefinition;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Level;

public class MysticNameTagsOwnedTagsUI extends InteractiveCustomUIPage<MysticNameTagsTagsUI.UIEventData> {

    public static final String LAYOUT = "mysticnametags/OwnedTags.ui";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int PAGE_SIZE = 10;

    /** Must match @RowDescStyle's TextColor - spans don't inherit the label style color. */
    private static final String COLOR_TEXT_DESCRIPTION = "#94a3b8";

    /**
     * Inline row container appended into #OwnedList; the three row widgets
     * (main button, favorite button, equip button) are appended into it and
     * addressed as "#OwnedList[i][0..2]".
     */
    private static final String ROW_GROUP_SOURCE =
            "Group { LayoutMode: Left; Anchor: (Height: 52, Bottom: 8); "
                    + "Background: (Color: #161b22(0.92)); OutlineColor: #333333; OutlineSize: 1; "
                    + "Padding: (Top: 8, Bottom: 8, Right: 12); }";
    private static final String QUICK_LOADOUT_NAME = "quick";

    private final PlayerRef playerRef;
    private final UUID uuid;

    private int currentPage;
    private String filterQuery;
    private String pendingFilterQuery;
    private long lastFilterApplyMs = 0L;
    private boolean resetSearchBox;

    public MysticNameTagsOwnedTagsUI(@Nonnull PlayerRef playerRef, @Nonnull UUID uuid) {
        this(playerRef, uuid, 0, null);
    }

    public MysticNameTagsOwnedTagsUI(@Nonnull PlayerRef playerRef,
                                     @Nonnull UUID uuid,
                                     int page,
                                     String filterQuery) {
        super(playerRef, CustomPageLifetime.CanDismiss, MysticNameTagsTagsUI.UIEventData.CODEC);
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

    private static String tagOrDefault(String s) {
        return s == null ? "" : s;
    }

    @Override
    public void build(@NotNull Ref<EntityStore> ref,
                      @NotNull UICommandBuilder cmd,
                      @NotNull UIEventBuilder evt,
                      @NotNull Store<EntityStore> store) {

        cmd.append(LAYOUT);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BottomCloseButton", EventData.of("Action", "close"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton", EventData.of("Action", "prev_page"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton", EventData.of("Action", "next_page"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RandomOwnedButton", EventData.of("Action", "random_owned"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RandomFavoriteButton", EventData.of("Action", "random_favorites"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SaveQuickLoadoutButton", EventData.of("Action", "save_quick_loadout"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EquipQuickLoadoutButton", EventData.of("Action", "equip_quick_loadout"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteQuickLoadoutButton", EventData.of("Action", "delete_quick_loadout"));

        // Vanilla-style live value capture: payload contains ONLY the
        // "@Filter" capture key; recognised by action == null.
        evt.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#TagSearchBox",
                EventData.of("@Filter", "#TagSearchBox.Value"),
                false
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ApplyFilterButton",
                EventData.of("Action", "set_filter")
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearFilterButton",
                EventData.of("Action", "clear_filter")
        );

        rebuildPage(ref, store, cmd, evt);
    }

    private void refresh(@Nonnull Ref<EntityStore> ref,
                         @Nonnull Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        rebuildPage(ref, store, cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private List<TagDefinition> createOwnedSnapshot() {
        TagManager tagManager = TagManager.get();
        Collection<TagDefinition> all = tagManager.getAllTags();

        List<TagDefinition> owned = new ArrayList<>();
        String needle = (filterQuery != null) ? filterQuery.toLowerCase(Locale.ROOT) : null;

        for (TagDefinition def : all) {
            if (def == null || def.getId() == null) continue;
            if (!tagManager.effectivelyOwnsTag(playerRef, uuid, def)) continue;

            if (needle != null) {
                String id = def.getId() != null ? def.getId() : "";
                String display = def.getDisplay() != null ? def.getDisplay() : "";
                String descr = def.getDescription() != null ? def.getDescription() : "";
                String category = def.getCategory() != null ? def.getCategory() : "";

                String plainDisplay = ColorFormatter.stripFormatting(display);
                String haystack = (id + " " + plainDisplay + " " + descr + " " + category)
                        .toLowerCase(Locale.ROOT);

                if (!haystack.contains(needle)) continue;
            }

            owned.add(def);
        }

        return owned;
    }

    private void rebuildPage(@Nonnull Ref<EntityStore> ref,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull UICommandBuilder cmd,
                             @Nonnull UIEventBuilder evt) {

        LanguageManager lang = LanguageManager.get();
        TagManager tagManager = TagManager.get();

        // Localized static labels
        cmd.set("#TitleLabel.Text", lang.tr("ui.owned.title"));
        cmd.set("#SubtitleLabel.Text", lang.tr("ui.owned.subtitle"));
        cmd.set("#CurrentTagPrefix.Text", lang.tr("ui.owned.current_prefix"));
        cmd.set("#ListSectionTitle.Text", lang.tr("ui.owned.section_title"));
        cmd.set("#FooterHint.Text", lang.tr("ui.owned.footer_hint"));
        cmd.set("#ApplyFilterButton.Text", lang.tr("ui.common.apply"));
        cmd.set("#ClearFilterButton.Text", lang.tr("ui.common.clear"));
        cmd.set("#PrevPageButton.Text", lang.tr("ui.common.prev"));
        cmd.set("#NextPageButton.Text", lang.tr("ui.common.next"));
        cmd.set("#BottomCloseButton.Text", lang.tr("ui.common.close"));
        cmd.set("#RandomOwnedButton.Text", lang.tr("ui.tags.button_random_owned"));
        cmd.set("#RandomFavoriteButton.Text", lang.tr("ui.tags.button_random_favorite"));
        cmd.set("#SaveQuickLoadoutButton.Text", lang.tr("ui.tags.button_save_quick_loadout"));
        cmd.set("#EquipQuickLoadoutButton.Text", lang.tr("ui.tags.button_equip_quick_loadout"));
        cmd.set("#DeleteQuickLoadoutButton.Text", lang.tr("ui.tags.button_delete_quick_loadout"));

        List<TagDefinition> tags = createOwnedSnapshot();

        int totalTags = tags.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalTags / (double) PAGE_SIZE));
        if (currentPage > totalPages - 1) currentPage = totalPages - 1;

        int startIndex = currentPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalTags);

        if (filterQuery != null) {
            cmd.set("#TagSearchBox.PlaceholderText",
                    lang.tr("ui.owned.search_filter_prefix", Map.of("filter", filterQuery)));
        } else {
            cmd.set("#TagSearchBox.PlaceholderText", lang.tr("ui.owned.search_placeholder"));
        }
        // Only push a value into the search box when explicitly clearing;
        // writing it on every refresh would fight the player's typing.
        if (resetSearchBox) {
            cmd.set("#TagSearchBox.Value", "");
            resetSearchBox = false;
        }

        TagDefinition active = tagManager.getEquipped(uuid);
        if (active == null) {
            active = tagManager.resolveActiveOrDefaultTag(uuid);
        }
        String equippedId = active != null ? active.getId() : null;

        cmd.clear("#OwnedList");

        int row = 0;
        for (int i = startIndex; i < endIndex; i++, row++) {
            TagDefinition def = tags.get(i);

            String rowSel = "#OwnedList[" + row + "]";
            cmd.appendInline("#OwnedList", ROW_GROUP_SOURCE);
            cmd.append(rowSel, "mysticnametags/OwnedTagRowMain.ui");
            cmd.append(rowSel, "mysticnametags/OwnedTagRowFav.ui");
            cmd.append(rowSel, "mysticnametags/OwnedTagRowEquip.ui");

            String mainSel = rowSel + "[0]";
            String favSel = rowSel + "[1]";
            String equipSel = rowSel + "[2]";

            String rawDisplay = def.getDisplay();
            String rawDescription = def.getDescription();

            // Truncate on visible characters so color codes neither eat the budget nor get cut
            // in half. Base color matches #Description's own style, since spans don't inherit it.
            String descText = ColorFormatter.truncateVisible(rawDescription, 107, "...");

            cmd.set(mainSel + " #Description.TextSpans",
                    ColorFormatter.toTextSpans(descText, COLOR_TEXT_DESCRIPTION));
            cmd.set(mainSel + " #Name.TextSpans",
                    ColorFormatter.toTextSpans(rawDisplay != null ? rawDisplay : def.getId()));

            boolean isEquipped = equippedId != null && equippedId.equalsIgnoreCase(def.getId());
            boolean favorite = tagManager.getFavoriteTags(uuid).contains(def.getId().toLowerCase(Locale.ROOT));

            cmd.set(equipSel + ".Text", isEquipped
                    ? lang.tr("ui.tags.button_unequip")
                    : lang.tr("ui.tags.button_equip"));
            cmd.set(favSel + ".Text", favorite
                    ? lang.tr("ui.tags.button_unfavorite")
                    : lang.tr("ui.tags.button_favorite"));

            // Status pill + accent: ACTIVE > FAVORITE > OWNED.
            String stateText;
            String stateColor;
            if (isEquipped) {
                stateText = lang.tr("ui.tags.badge_active");
                stateColor = "#3fb950";
            } else if (favorite) {
                stateText = lang.tr("ui.owned.badge_favorite");
                stateColor = "#f0b429";
            } else {
                stateText = lang.tr("ui.tags.badge_owned");
                stateColor = "#58a6ff";
            }
            cmd.set(mainSel + " #State.TextSpans", ColorFormatter.toFlatTextSpans(stateText, stateColor));
            cmd.set(mainSel + " #StatePill.OutlineColor", stateColor);
            cmd.set(mainSel + " #Accent.OutlineColor", stateColor);
            cmd.set(rowSel + ".OutlineColor", isEquipped ? "#58a6ff" : "#333333");

            EventData rowEvent = new EventData()
                    .append("Action", "tag_click")
                    .append("TagId", def.getId())
                    .append("RowIndex", String.valueOf(row));

            EventData favoriteEvent = new EventData()
                    .append("Action", "favorite_click")
                    .append("TagId", def.getId())
                    .append("RowIndex", String.valueOf(row));

            evt.addEventBinding(CustomUIEventBindingType.Activating, mainSel, rowEvent, false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, equipSel, rowEvent, false);
            evt.addEventBinding(CustomUIEventBindingType.Activating, favSel, favoriteEvent, false);
        }

        boolean empty = totalTags <= 0;
        cmd.set("#EmptyOwnedLabel.Visible", empty);
        cmd.set("#EmptyOwnedLabel.Text",
                filterQuery != null
                        ? lang.tr("ui.owned.none_for_filter", Map.of("filter", filterQuery))
                        : lang.tr("ui.owned.none"));

        String label;
        if (empty) {
            label = filterQuery != null
                    ? lang.tr("ui.owned.none_for_filter", Map.of("filter", filterQuery))
                    : lang.tr("ui.owned.none");
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

        String quickTag = tagManager.getLoadouts(uuid).get(QUICK_LOADOUT_NAME);
        String quickLabel = (quickTag == null || quickTag.isBlank())
                ? lang.tr("ui.tags.quick_loadout_empty")
                : lang.tr("ui.tags.quick_loadout_value", Map.of("tagId", quickTag));
        cmd.set("#QuickLoadoutLabel.Text", quickLabel);
        cmd.set("#EquipQuickLoadoutButton.Visible", quickTag != null && !quickTag.isBlank());
        cmd.set("#DeleteQuickLoadoutButton.Visible", quickTag != null && !quickTag.isBlank());

        // Spans preserve each colored segment of the nameplate, so this preview matches the
        // real thing instead of flattening to one color.
        String previewSource;
        try {
            previewSource = tagManager.buildNameplate(playerRef, playerRef.getUsername(), uuid);
        } catch (Throwable ignored) {
            previewSource = playerRef.getUsername();
        }

        cmd.set("#CurrentNameplateLabel.TextSpans", ColorFormatter.toTextSpans(previewSource));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull MysticNameTagsTagsUI.UIEventData data) {

        String action = data.action;

        // Capture-only payload from the search box: live filtering.
        if (action == null) {
            if (data.filter != null && !data.filter.startsWith("#TagSearchBox")) {
                String newFilter = normalizeFilter(data.filter);
                pendingFilterQuery = newFilter;
                if (!Objects.equals(this.filterQuery, newFilter)) {
                    this.filterQuery = newFilter;
                    this.currentPage = 0;
                    refresh(ref, store);
                }
            }
            return;
        }

        switch (action) {
            case "close" -> close();

            case "prev_page" -> {
                if (currentPage <= 0) return;
                currentPage--;
                refresh(ref, store);
            }

            case "next_page" -> {
                List<TagDefinition> tags = createOwnedSnapshot();
                int totalPages = Math.max(1, (int) Math.ceil(tags.size() / (double) PAGE_SIZE));
                if (currentPage >= totalPages - 1) return;
                currentPage++;
                refresh(ref, store);
            }

            case "set_filter" -> {
                long now = System.currentTimeMillis();
                if (now - lastFilterApplyMs < 200L) return;
                lastFilterApplyMs = now;

                String newFilter = normalizeFilter(pendingFilterQuery);
                if (!Objects.equals(this.filterQuery, newFilter)) {
                    this.filterQuery = newFilter;
                    currentPage = 0;
                }
                refresh(ref, store);
            }

            case "clear_filter" -> {
                long now = System.currentTimeMillis();
                if (now - lastFilterApplyMs < 200L) return;
                lastFilterApplyMs = now;

                this.pendingFilterQuery = null;
                this.filterQuery = null;
                this.currentPage = 0;
                this.resetSearchBox = true;
                refresh(ref, store);
            }

            case "tag_click" -> handleOwnedTagClick(ref, store, data);

            case "favorite_click" -> handleFavoriteClick(ref, store, data);

            case "random_owned" -> equipRandomTag(false, ref, store);

            case "random_favorites" -> equipRandomTag(true, ref, store);

            case "save_quick_loadout" -> saveQuickLoadout(ref, store);

            case "equip_quick_loadout" -> equipQuickLoadout(ref, store);

            case "delete_quick_loadout" -> deleteQuickLoadout(ref, store);
        }
    }

    private void handleFavoriteClick(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull MysticNameTagsTagsUI.UIEventData data) {
        if (uuid == null || data.tagId == null || data.tagId.isBlank()) {
            return;
        }

        TagManager.FavoriteResult result = TagManager.get().toggleFavorite(uuid, data.tagId, playerRef.getUsername());
        String key = switch (result) {
            case ADDED -> "cmd.favorite.added";
            case REMOVED -> "cmd.favorite.removed";
            case NOT_OWNED -> "cmd.favorite.not_owned";
            case NOT_FOUND -> "cmd.favorite.not_found";
        };
        sendUiNotification(key, Map.of("tagId", data.tagId), NotificationStyle.Default);
        refresh(ref, store);
    }

    private void equipRandomTag(boolean favoritesOnly,
                                @Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store) {
        TagManager manager = TagManager.get();
        TagManager.TagPurchaseResult result = manager.equipRandomTag(playerRef, uuid, favoritesOnly);
        if (result == TagManager.TagPurchaseResult.NOT_FOUND) {
            sendUiNotification(favoritesOnly ? "cmd.random.no_favorites" : "cmd.random.no_owned",
                    Map.of(),
                    NotificationStyle.Warning);
        } else {
            handlePurchaseResult(result, manager.getEquipped(uuid));
        }
        refresh(ref, store);
    }

    private void saveQuickLoadout(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull Store<EntityStore> store) {
        TagManager.LoadoutResult result = TagManager.get().saveLoadout(uuid, QUICK_LOADOUT_NAME, playerRef.getUsername());
        sendLoadoutNotification(result, null);
        refresh(ref, store);
    }

    private void equipQuickLoadout(@Nonnull Ref<EntityStore> ref,
                                   @Nonnull Store<EntityStore> store) {
        TagManager.LoadoutEquipResult result = TagManager.get().equipLoadout(playerRef, uuid, QUICK_LOADOUT_NAME);
        if (result.getLoadoutResult() != TagManager.LoadoutResult.EQUIPPED) {
            sendLoadoutNotification(result.getLoadoutResult(), result.getTagId());
        } else {
            sendUiNotification("cmd.loadout.equipped",
                    Map.of(
                            "name", QUICK_LOADOUT_NAME,
                            "tagId", result.getTagId() == null ? "unknown" : result.getTagId(),
                            "result", result.getTagResult().name()
                    ),
                    NotificationStyle.Default);
        }
        refresh(ref, store);
    }

    private void deleteQuickLoadout(@Nonnull Ref<EntityStore> ref,
                                    @Nonnull Store<EntityStore> store) {
        TagManager.LoadoutResult result = TagManager.get().deleteLoadout(uuid, QUICK_LOADOUT_NAME, playerRef.getUsername());
        sendLoadoutNotification(result, null);
        refresh(ref, store);
    }

    private void sendLoadoutNotification(@Nonnull TagManager.LoadoutResult result,
                                         String tagId) {
        String key = switch (result) {
            case SAVED -> "cmd.loadout.saved";
            case DELETED -> "cmd.loadout.deleted";
            case NOT_FOUND -> "cmd.loadout.not_found";
            case INVALID_NAME -> "cmd.loadout.invalid_name";
            case NO_EQUIPPED_TAG -> "cmd.loadout.no_equipped";
            case EQUIPPED -> "cmd.loadout.equipped";
        };

        Map<String, String> vars = tagId == null
                ? Map.of("name", QUICK_LOADOUT_NAME)
                : Map.of("name", QUICK_LOADOUT_NAME, "tagId", tagId, "result", TagManager.TagPurchaseResult.EQUIPPED_ALREADY_OWNED.name());
        sendUiNotification(key, vars, NotificationStyle.Default);
    }

    private void handleOwnedTagClick(@Nonnull Ref<EntityStore> ref,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull MysticNameTagsTagsUI.UIEventData data) {

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
            if (rowIndex < 0 || rowIndex >= PAGE_SIZE) return;

            List<TagDefinition> tags = createOwnedSnapshot();
            int absIndex = (currentPage * PAGE_SIZE) + rowIndex;
            if (absIndex < 0 || absIndex >= tags.size()) return;

            def = tags.get(absIndex);
            if (def == null || def.getId() == null || def.getId().isEmpty()) return;
            resolvedId = def.getId();
        }

        if (resolvedId == null || resolvedId.isEmpty()) return;

        try {
            TagManager.TagPurchaseResult result = manager.toggleTag(playerRef, uuid, resolvedId);

            try {
                World world = manager.getOnlineWorld(uuid);
                if (world != null) {
                    manager.forceRefreshNameplate(playerRef, world);
                }
            } catch (Throwable ignored) {
            }

            handlePurchaseResult(result, def);

        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to handle owned-tag click for " + resolvedId);
        }

        refresh(ref, store);
    }

    private void handlePurchaseResult(TagManager.TagPurchaseResult result, TagDefinition def) {
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
            case UNAVAILABLE -> {
                String custom = cleanAvailabilityMessage(def);
                if (!custom.isBlank()) {
                    msgKey = null;
                    vars = Map.of("message", custom);
                } else {
                    msgKey = "tags.unavailable";
                    vars = Map.of();
                }
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

        String msg = msgKey == null ? vars.getOrDefault("message", "") : lang.tr(msgKey, vars);

        MysticNotificationUtil.send(
                playerRef.getPacketHandler(),
                ColorFormatter.colorize(tagOrDefault(title)),
                ColorFormatter.colorize(tagOrDefault(msg)),
                NotificationStyle.Default
        );
    }

    private void sendUiNotification(@Nonnull String messageKey,
                                    @Nonnull Map<String, String> vars,
                                    @Nonnull NotificationStyle style) {
        LanguageManager lang = LanguageManager.get();

        MysticNotificationUtil.send(
                playerRef.getPacketHandler(),
                ColorFormatter.colorize("&b" + lang.tr("plugin.title")),
                ColorFormatter.colorize(lang.tr(messageKey, vars)),
                style
        );
    }

    @Nonnull
    private static String cleanAvailabilityMessage(TagDefinition def) {
        if (def == null || def.getAvailabilityMessage() == null) {
            return "";
        }
        return ColorFormatter.stripFormatting(def.getAvailabilityMessage()).trim();
    }
}
