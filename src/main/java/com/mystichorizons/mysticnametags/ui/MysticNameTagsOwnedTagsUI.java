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
    private static final int MAX_ROWS = 10;
    private static final int PAGE_SIZE = 10;

    private final PlayerRef playerRef;
    private final UUID uuid;

    private int currentPage;
    private String filterQuery;
    private long lastFilterApplyMs = 0L;

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
            if (!tagManager.ownsTag(uuid, def.getId())) continue;

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
        cmd.set("#ListHeaderTag.Text", lang.tr("ui.owned.header_tag"));
        cmd.set("#ListHeaderAction.Text", lang.tr("ui.owned.header_action"));
        cmd.set("#FooterHint.Text", lang.tr("ui.owned.footer_hint"));
        cmd.set("#ApplyFilterButton.Text", lang.tr("ui.common.apply"));
        cmd.set("#ClearFilterButton.Text", lang.tr("ui.common.clear"));
        cmd.set("#PrevPageButton.Text", lang.tr("ui.common.prev"));
        cmd.set("#NextPageButton.Text", lang.tr("ui.common.next"));
        cmd.set("#BottomCloseButton.Text", lang.tr("ui.common.close"));

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

        TagDefinition active = tagManager.getEquipped(uuid);
        if (active == null) {
            active = tagManager.resolveActiveOrDefaultTag(uuid);
        }
        String equippedId = active != null ? active.getId() : null;

        int row = 0;
        for (int i = startIndex; i < endIndex && row < MAX_ROWS; i++, row++) {
            TagDefinition def = tags.get(i);

            String cardSelector = "#TagRow" + row + "Card";
            String nameSelector = "#TagRow" + row + "Name";
            String descSelector = "#TagRow" + row + "Description";
            String buttonSelector = "#TagRow" + row + "Button";

            String rawDisplay = def.getDisplay();
            String rawDescription = def.getDescription();

            String nameText = ColorFormatter.stripFormatting(rawDisplay != null ? rawDisplay : def.getId());
            String nameHex = rawDisplay != null ? ColorFormatter.extractUiTextColor(rawDisplay) : null;

            String descText = rawDescription != null ? ColorFormatter.stripFormatting(rawDescription) : "";
            if (descText.length() > 110) {
                descText = descText.substring(0, 107) + "...";
            }

            cmd.set(cardSelector + ".Visible", true);
            cmd.set(nameSelector + ".Text", nameText);
            cmd.set(descSelector + ".Text", descText);

            if (nameHex != null) {
                cmd.set(nameSelector + ".Style.TextColor", "#" + nameHex);
            } else {
                cmd.set(nameSelector + ".Style.TextColor", "#ffffff");
            }

            boolean isEquipped = equippedId != null && equippedId.equalsIgnoreCase(def.getId());
            String buttonText = isEquipped
                    ? lang.tr("ui.tags.button_unequip")
                    : lang.tr("ui.tags.button_equip");

            cmd.set(buttonSelector + ".Text", buttonText);
            cmd.set(cardSelector + ".OutlineColor", isEquipped ? "#58a6ff" : "#333333");

            EventData rowEvent = new EventData()
                    .append("Action", "tag_click")
                    .append("TagId", def.getId())
                    .append("RowIndex", String.valueOf(row));

            evt.addEventBinding(CustomUIEventBindingType.Activating, buttonSelector, rowEvent, false);
        }

        for (; row < MAX_ROWS; row++) {
            cmd.set("#TagRow" + row + "Card.Visible", false);
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

        String previewText;
        String previewHex = null;

        try {
            String baseName = playerRef.getUsername();
            String coloredNameplate = tagManager.buildNameplate(playerRef, baseName, uuid);
            previewText = ColorFormatter.stripFormatting(coloredNameplate);

            if (uuid != null) {
                String rankPrefix = TagManager.get().getIntegrations().getPrimaryPrefix(uuid);
                previewHex = ColorFormatter.extractFirstHexColor(rankPrefix);

                if (previewHex == null && active != null) {
                    previewHex = ColorFormatter.extractFirstHexColor(active.getDisplay());
                }
            }
        } catch (Throwable ignored) {
            previewText = playerRef.getUsername();
        }

        cmd.set("#CurrentNameplateLabel.Text", previewText);
        if (previewHex != null) {
            cmd.set("#CurrentNameplateLabel.Style.TextColor", "#" + previewHex);
        } else {
            cmd.set("#CurrentNameplateLabel.Style.TextColor", "#e6edf3");
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull MysticNameTagsTagsUI.UIEventData data) {

        String action = data.action;
        if (action == null) return;

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

                String newFilter = normalizeFilter(data.filter);
                if (!Objects.equals(this.filterQuery, newFilter)) {
                    this.filterQuery = newFilter;
                    currentPage = 0;
                }
                refresh(ref, store);
            }

            case "tag_click" -> handleOwnedTagClick(ref, store, data);
        }
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
            if (rowIndex < 0 || rowIndex >= MAX_ROWS) return;

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

        MysticNotificationUtil.send(
                playerRef.getPacketHandler(),
                ColorFormatter.colorize(tagOrDefault(title)),
                ColorFormatter.colorize(tagOrDefault(msg)),
                NotificationStyle.Default
        );
    }
}
