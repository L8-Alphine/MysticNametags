package com.mystichorizons.mysticnametags.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.tags.TagDefinition;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dedicated in-game tag editor UI (create / edit / delete tag definitions).
 * <p>
 * Layout file: mysticnametags/TagEditor.ui
 */
public class MysticNameTagsTagEditorUI extends InteractiveCustomUIPage<MysticNameTagsTagEditorUI.UIEventData> {

    public static final String LAYOUT = "mysticnametags/TagEditor.ui";

    private static final int PAGE_SIZE = 12;

    private final PlayerRef playerRef;

    private int currentPage;
    private String filterQuery;
    private String pendingFilterQuery;
    private boolean resetSearchBox;

    private String selectedTagId = "";
    private String draftTagId = "";
    private String draftDisplay = "";
    private String draftDescription = "";
    private String draftCategory = "General";
    private String draftPrice = "0";
    private String draftPermission = "";
    private String statusLine = "";

    public MysticNameTagsTagEditorUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
    }

    private static String normalizeFilter(@Nullable String filter) {
        if (filter == null) return null;
        String trimmed = filter.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditorNewButton", EventData.of("Action", "new_tag"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditorSaveButton", EventData.of("Action", "save"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditorClearButton", EventData.of("Action", "new_tag"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#EditorDeleteButton", EventData.of("Action", "delete"));

        // Vanilla-style live value capture: each payload contains ONLY one
        // "@Key" capture entry; the handler recognises payloads by which
        // codec field arrived non-null (action == null).
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TagSearchBox",
                EventData.of("@Filter", "#TagSearchBox.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyFilterButton",
                EventData.of("Action", "apply_filter"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ClearFilterButton",
                EventData.of("Action", "clear_filter"));

        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EditorTagIdBox",
                EventData.of("@EditId", "#EditorTagIdBox.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EditorDisplayBox",
                EventData.of("@EditDisplay", "#EditorDisplayBox.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EditorDescriptionBox",
                EventData.of("@EditDesc", "#EditorDescriptionBox.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EditorCategoryBox",
                EventData.of("@EditCat", "#EditorCategoryBox.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EditorPriceBox",
                EventData.of("@EditPrice", "#EditorPriceBox.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#EditorPermissionBox",
                EventData.of("@EditPerm", "#EditorPermissionBox.Value"), false);

        rebuild(cmd, evt, true);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {

        String action = data.action;

        // Capture-only payloads: live search + live draft field updates.
        if (action == null) {
            if (data.filter != null && !data.filter.startsWith("#TagSearchBox")) {
                String newFilter = normalizeFilter(data.filter);
                pendingFilterQuery = newFilter;
                if (!java.util.Objects.equals(filterQuery, newFilter)) {
                    filterQuery = newFilter;
                    currentPage = 0;
                    refresh(false);
                }
                return;
            }

            boolean draftChanged = false;
            if (data.editId != null) { draftTagId = data.editId; draftChanged = true; }
            if (data.editDisplay != null) { draftDisplay = data.editDisplay; draftChanged = true; }
            if (data.editDesc != null) { draftDescription = data.editDesc; draftChanged = true; }
            if (data.editCat != null) { draftCategory = data.editCat; draftChanged = true; }
            if (data.editPrice != null) { draftPrice = data.editPrice; draftChanged = true; }
            if (data.editPerm != null) { draftPermission = data.editPerm; draftChanged = true; }

            if (draftChanged) {
                // Update only the preview so typing is not interrupted.
                UICommandBuilder cmd = new UICommandBuilder();
                applyPreview(cmd);
                sendUpdate(cmd, null, false);
            }
            return;
        }

        LanguageManager lang = LanguageManager.get();

        switch (action) {
            case "close" -> close();

            case "prev_page" -> {
                if (currentPage <= 0) return;
                currentPage--;
                refresh(false);
            }

            case "next_page" -> {
                int totalPages = Math.max(1, (int) Math.ceil(filteredTags().size() / (double) PAGE_SIZE));
                if (currentPage >= totalPages - 1) return;
                currentPage++;
                refresh(false);
            }

            case "apply_filter" -> {
                filterQuery = normalizeFilter(pendingFilterQuery);
                pendingFilterQuery = filterQuery;
                currentPage = 0;
                refresh(false);
            }

            case "clear_filter" -> {
                filterQuery = null;
                pendingFilterQuery = null;
                currentPage = 0;
                resetSearchBox = true;
                refresh(true);
            }

            case "select_tag" -> {
                if (data.tagId == null || data.tagId.isBlank()) return;
                loadTag(data.tagId);
                statusLine = lang.tr("ui.editor.status_loaded", Map.of("tagId", data.tagId));
                refresh(true);
            }

            case "new_tag" -> {
                clearDraft();
                statusLine = lang.tr("ui.editor.status_cleared");
                refresh(true);
            }

            case "save" -> {
                TagManager.TagEditResult result = TagManager.get().upsertSimpleTag(
                        draftTagId,
                        draftDisplay,
                        draftDescription,
                        draftCategory,
                        draftPrice,
                        draftPermission,
                        playerRef.getUsername()
                );

                if (result.isSuccess()) {
                    if (result.getTag() != null) {
                        loadFromDefinition(result.getTag());
                    }
                    statusLine = lang.tr("ui.editor.status_saved", Map.of(
                            "tagId", draftTagId,
                            "status", result.getStatus().name().toLowerCase(Locale.ROOT)
                    ));
                    notifyToast(true, lang.tr("dashboard.editor_saved_toast"));
                } else {
                    statusLine = lang.tr("ui.editor.status_failed", Map.of(
                            "error", result.getMessage() == null ? result.getStatus().name() : result.getMessage()
                    ));
                    notifyToast(false, statusLine);
                }
                refresh(true);
            }

            case "delete" -> {
                if (draftTagId == null || draftTagId.isBlank()) {
                    statusLine = lang.tr("ui.editor.status_no_id");
                    refresh(false);
                    return;
                }

                TagManager.TagEditResult result =
                        TagManager.get().deleteTagDefinition(draftTagId, playerRef.getUsername());

                if (result.isSuccess()) {
                    statusLine = lang.tr("ui.editor.status_deleted", Map.of("tagId", draftTagId));
                    clearDraft();
                    notifyToast(true, lang.tr("dashboard.editor_deleted_toast"));
                } else {
                    statusLine = lang.tr("ui.editor.status_failed", Map.of(
                            "error", result.getMessage() == null ? result.getStatus().name() : result.getMessage()
                    ));
                    notifyToast(false, statusLine);
                }
                refresh(true);
            }
        }
    }

    private void loadTag(@Nonnull String tagId) {
        TagDefinition def = TagManager.get().getTag(tagId);
        if (def == null) {
            statusLine = LanguageManager.get().tr("ui.editor.status_not_found", Map.of("tagId", tagId));
            return;
        }
        loadFromDefinition(def);
    }

    private void loadFromDefinition(@Nonnull TagDefinition def) {
        selectedTagId = def.getId() == null ? "" : def.getId();
        draftTagId = selectedTagId;
        draftDisplay = def.getDisplay() == null ? "" : def.getDisplay();
        draftDescription = def.getDescription() == null ? "" : def.getDescription();
        draftCategory = def.getCategory() == null ? "" : def.getCategory();
        draftPrice = String.valueOf(def.getPrice());
        draftPermission = def.getPermission() == null ? "" : def.getPermission();
    }

    private void clearDraft() {
        selectedTagId = "";
        draftTagId = "";
        draftDisplay = "";
        draftDescription = "";
        draftCategory = "General";
        draftPrice = "0";
        draftPermission = "";
    }

    private void notifyToast(boolean success, @Nonnull String message) {
        LanguageManager lang = LanguageManager.get();
        MysticNotificationUtil.send(
                playerRef.getPacketHandler(),
                ColorFormatter.colorize("&b" + lang.tr("plugin.title")),
                ColorFormatter.colorize(message),
                success ? NotificationStyle.Success : NotificationStyle.Warning
        );
    }

    @Nonnull
    private List<TagDefinition> filteredTags() {
        List<TagDefinition> out = new ArrayList<>();
        String needle = filterQuery == null ? null : filterQuery.toLowerCase(Locale.ROOT);

        for (TagDefinition def : TagManager.get().getAllTags()) {
            if (def == null || def.getId() == null) continue;

            if (needle != null) {
                String display = def.getDisplay() == null ? "" : def.getDisplay();
                String category = def.getCategory() == null ? "" : def.getCategory();
                String haystack = (def.getId() + " "
                        + ColorFormatter.stripFormatting(display) + " "
                        + category).toLowerCase(Locale.ROOT);
                if (!haystack.contains(needle)) continue;
            }

            out.add(def);
        }

        out.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getId(), b.getId()));
        return out;
    }

    private void refresh(boolean pushDraftValues) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        rebuild(cmd, evt, pushDraftValues);
        sendUpdate(cmd, evt, false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd,
                         @Nonnull UIEventBuilder evt,
                         boolean pushDraftValues) {
        LanguageManager lang = LanguageManager.get();

        cmd.set("#TitleLabel.Text", lang.tr("ui.editor.title"));
        cmd.set("#EditorListTitle.Text", lang.tr("ui.editor.list_title"));
        cmd.set("#EditorFormTitle.Text", lang.tr("ui.editor.form_title"));
        cmd.set("#EditorNewButton.Text", lang.tr("ui.editor.button_new"));
        cmd.set("#EditorHelpLine.Text", lang.tr("ui.dashboard.editor_help"));
        cmd.set("#EditorIdLabel.Text", lang.tr("ui.dashboard.editor_id"));
        cmd.set("#EditorDisplayLabel.Text", lang.tr("ui.dashboard.editor_display"));
        cmd.set("#EditorDescriptionLabel.Text", lang.tr("ui.dashboard.editor_description"));
        cmd.set("#EditorCategoryLabel.Text", lang.tr("ui.dashboard.editor_category"));
        cmd.set("#EditorPriceLabel.Text", lang.tr("ui.dashboard.editor_price"));
        cmd.set("#EditorPermissionLabel.Text", lang.tr("ui.dashboard.editor_permission"));
        cmd.set("#EditorPreviewTitle.Text", lang.tr("ui.editor.preview_title"));
        cmd.set("#EditorSaveButton.Text", lang.tr("ui.dashboard.editor_save"));
        cmd.set("#EditorClearButton.Text", lang.tr("ui.dashboard.editor_clear"));
        cmd.set("#EditorDeleteButton.Text", lang.tr("ui.dashboard.editor_delete"));
        cmd.set("#FooterHint.Text", lang.tr("ui.editor.footer_hint"));
        cmd.set("#BottomCloseButton.Text", lang.tr("ui.common.close"));
        cmd.set("#ApplyFilterButton.Text", lang.tr("ui.common.apply"));
        cmd.set("#ClearFilterButton.Text", lang.tr("ui.common.clear"));
        cmd.set("#PrevPageButton.Text", lang.tr("ui.common.prev"));
        cmd.set("#NextPageButton.Text", lang.tr("ui.common.next"));
        cmd.set("#TagSearchBox.PlaceholderText", lang.tr("ui.editor.search_placeholder"));
        if (resetSearchBox) {
            cmd.set("#TagSearchBox.Value", "");
            resetSearchBox = false;
        }

        cmd.set("#EditorStatusLine.Text", statusLine == null ? "" : statusLine);

        if (pushDraftValues) {
            cmd.set("#EditorTagIdBox.Value", draftTagId);
            cmd.set("#EditorDisplayBox.Value", draftDisplay);
            cmd.set("#EditorDescriptionBox.Value", draftDescription);
            cmd.set("#EditorCategoryBox.Value", draftCategory);
            cmd.set("#EditorPriceBox.Value", draftPrice);
            cmd.set("#EditorPermissionBox.Value", draftPermission);
        }

        applyPreview(cmd);
        rebuildList(cmd, evt, lang);
    }

    private void applyPreview(@Nonnull UICommandBuilder cmd) {
        LanguageManager lang = LanguageManager.get();

        if (draftDisplay == null || draftDisplay.isBlank()) {
            cmd.set("#EditorPreviewLine.TextSpans",
                    ColorFormatter.toFlatTextSpans(lang.tr("dashboard.editor_preview_empty"), "#6e7681"));
            return;
        }

        // Spans render the draft with every color it actually declares, so the editor preview
        // shows gradients and multi-color displays truthfully.
        cmd.set("#EditorPreviewLine.TextSpans", ColorFormatter.toTextSpans(draftDisplay));
    }

    private void rebuildList(@Nonnull UICommandBuilder cmd,
                             @Nonnull UIEventBuilder evt,
                             @Nonnull LanguageManager lang) {

        List<TagDefinition> tags = filteredTags();

        int totalTags = tags.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalTags / (double) PAGE_SIZE));
        if (currentPage > totalPages - 1) currentPage = totalPages - 1;

        int startIndex = currentPage * PAGE_SIZE;
        int endIndex = Math.min(startIndex + PAGE_SIZE, totalTags);

        cmd.clear("#EditorTagList");
        cmd.set("#EditorListEmpty.Visible", totalTags == 0);

        int row = 0;
        for (int i = startIndex; i < endIndex; i++, row++) {
            TagDefinition def = tags.get(i);

            cmd.append("#EditorTagList", "mysticnametags/EditorTagRow.ui");
            String rowSel = "#EditorTagList[" + row + "]";

            String display = def.getDisplay();
            boolean isSelected = def.getId().equalsIgnoreCase(selectedTagId);

            cmd.set(rowSel + " #Name.TextSpans",
                    ColorFormatter.toTextSpans(display != null ? display : def.getId()));
            cmd.set(rowSel + " #Meta.Text", def.getId()
                    + (def.getCategory() == null || def.getCategory().isBlank() ? "" : "  |  " + def.getCategory()));
            cmd.set(rowSel + " #Accent.OutlineColor", isSelected ? "#3fb950" : "#3a3a3a");

            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    rowSel,
                    new EventData()
                            .append("Action", "select_tag")
                            .append("TagId", def.getId()),
                    false
            );
        }

        cmd.set("#PageLabel.Text", lang.tr("ui.tags.page_label", Map.of(
                "page", String.valueOf(currentPage + 1),
                "pages", String.valueOf(totalPages)
        )));
        cmd.set("#PrevPageButton.Visible", totalTags > 0 && currentPage > 0);
        cmd.set("#NextPageButton.Visible", totalTags > 0 && currentPage < totalPages - 1);
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
                        .append(new KeyedCodec<>("EditId", Codec.STRING),
                                (e, v) -> e.editId = v,
                                e -> e.editId)
                        .add()
                        .append(new KeyedCodec<>("EditDisplay", Codec.STRING),
                                (e, v) -> e.editDisplay = v,
                                e -> e.editDisplay)
                        .add()
                        .append(new KeyedCodec<>("EditDesc", Codec.STRING),
                                (e, v) -> e.editDesc = v,
                                e -> e.editDesc)
                        .add()
                        .append(new KeyedCodec<>("EditCat", Codec.STRING),
                                (e, v) -> e.editCat = v,
                                e -> e.editCat)
                        .add()
                        .append(new KeyedCodec<>("EditPrice", Codec.STRING),
                                (e, v) -> e.editPrice = v,
                                e -> e.editPrice)
                        .add()
                        .append(new KeyedCodec<>("EditPerm", Codec.STRING),
                                (e, v) -> e.editPerm = v,
                                e -> e.editPerm)
                        .add()
                        .build();

        public String action;
        public String tagId;
        public String filter;
        public String editId;
        public String editDisplay;
        public String editDesc;
        public String editCat;
        public String editPrice;
        public String editPerm;

        public UIEventData() {
        }
    }
}
