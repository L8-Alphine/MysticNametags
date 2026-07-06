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
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tag pack import/export manager UI.
 * <p>
 * Layout file: mysticnametags/PackManager.ui
 */
public class MysticNameTagsPackManagerUI extends InteractiveCustomUIPage<MysticNameTagsPackManagerUI.UIEventData> {

    public static final String LAYOUT = "mysticnametags/PackManager.ui";

    private static final int MAX_RESULT_LINES = 14;
    private static final DateTimeFormatter PACK_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final TagManager.TagPackImportMode[] MODES = {
            TagManager.TagPackImportMode.APPEND,
            TagManager.TagPackImportMode.UPSERT,
            TagManager.TagPackImportMode.REPLACE
    };

    private final PlayerRef playerRef;
    private final List<String> resultLines = new ArrayList<>();

    private String selectedPack;
    private int modeIndex = 1; // UPSERT
    private String pendingExportName = "";
    private int exportCategoryIndex = 0; // 0 = All

    public MysticNameTagsPackManagerUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt,
                      @Nonnull Store<EntityStore> store) {

        cmd.append(LAYOUT);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BottomCloseButton", EventData.of("Action", "close"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshPacksButton", EventData.of("Action", "refresh"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevModeButton", EventData.of("Action", "prev_mode"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextModeButton", EventData.of("Action", "next_mode"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ImportButton", EventData.of("Action", "import"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevExportCategoryButton", EventData.of("Action", "prev_category"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextExportCategoryButton", EventData.of("Action", "next_category"));

        // Vanilla-style live value capture: payload contains ONLY the "@Value"
        // capture key; recognised by action == null.
        evt.addEventBinding(
                CustomUIEventBindingType.ValueChanged,
                "#ExportNameBox",
                EventData.of("@Value", "#ExportNameBox.Value"),
                false
        );

        evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ExportButton",
                EventData.of("Action", "export")
        );

        rebuild(cmd, evt);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {

        String action = data.action;

        // Capture-only payload from the export name field.
        if (action == null) {
            if (data.value != null && !data.value.startsWith("#ExportNameBox")) {
                pendingExportName = data.value;
            }
            return;
        }

        switch (action) {
            case "close" -> close();

            case "refresh" -> refresh();

            case "select_pack" -> {
                if (data.pack == null || data.pack.isBlank()) return;
                selectedPack = data.pack;
                refresh();
            }

            case "prev_mode" -> {
                modeIndex = (modeIndex + MODES.length - 1) % MODES.length;
                refresh();
            }

            case "next_mode" -> {
                modeIndex = (modeIndex + 1) % MODES.length;
                refresh();
            }

            case "prev_category" -> {
                int max = TagManager.get().getCategories().size();
                exportCategoryIndex--;
                if (exportCategoryIndex < 0) exportCategoryIndex = max;
                refresh();
            }

            case "next_category" -> {
                int max = TagManager.get().getCategories().size();
                exportCategoryIndex++;
                if (exportCategoryIndex > max) exportCategoryIndex = 0;
                refresh();
            }

            case "import" -> runImport();

            case "export" -> runExport();
        }
    }

    private void runImport() {
        LanguageManager lang = LanguageManager.get();

        if (selectedPack == null || selectedPack.isBlank()) {
            addResult(lang.tr("ui.packs.result_no_selection"));
            refresh();
            return;
        }

        TagManager.TagPackImportMode mode = MODES[modeIndex];
        TagManager.TagPackImportResult result =
                TagManager.get().importTagPack(selectedPack, mode, playerRef.getUsername());

        if (result.isSuccess()) {
            addResult(lang.tr("ui.packs.result_import_success", Map.of(
                    "pack", selectedPack,
                    "mode", mode.name().toLowerCase(Locale.ROOT),
                    "added", String.valueOf(result.getAdded()),
                    "replaced", String.valueOf(result.getReplaced()),
                    "skipped", String.valueOf(result.getSkipped())
            )));
        } else {
            addResult(lang.tr("ui.packs.result_import_failed", Map.of(
                    "pack", selectedPack,
                    "error", result.getError() == null ? "unknown" : result.getError()
            )));
        }

        notifyToast(result.isSuccess(), result.isSuccess()
                ? lang.tr("dashboard.import_pack_success_toast")
                : lang.tr("dashboard.import_pack_failed_toast"));
        refresh();
    }

    private void runExport() {
        LanguageManager lang = LanguageManager.get();

        String name = pendingExportName == null ? "" : pendingExportName.trim();
        if (name.isEmpty()) {
            name = "export.json";
        }

        String category = resolveExportCategory();
        TagManager.TagPackExportResult result =
                TagManager.get().exportTagPack(name, category, playerRef.getUsername());

        if (result.isSuccess()) {
            addResult(lang.tr("ui.packs.result_export_success", Map.of(
                    "count", String.valueOf(result.getExported()),
                    "file", result.getFile() == null ? name : result.getFile().getName(),
                    "category", category == null ? lang.tr("ui.tags.category_all") : category
            )));
        } else {
            addResult(lang.tr("ui.packs.result_export_failed", Map.of(
                    "error", result.getError() == null ? "unknown" : result.getError()
            )));
        }

        notifyToast(result.isSuccess(), result.isSuccess()
                ? lang.tr("dashboard.export_pack_success_toast")
                : lang.tr("dashboard.export_pack_failed_toast"));
        refresh();
    }

    @Nullable
    private String resolveExportCategory() {
        List<String> categories = TagManager.get().getCategories();
        if (exportCategoryIndex <= 0 || exportCategoryIndex > categories.size()) {
            return null;
        }
        return categories.get(exportCategoryIndex - 1);
    }

    private void addResult(@Nonnull String line) {
        resultLines.add(0, line);
        while (resultLines.size() > MAX_RESULT_LINES) {
            resultLines.remove(resultLines.size() - 1);
        }
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

    private void refresh() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        rebuild(cmd, evt);
        sendUpdate(cmd, evt, false);
    }

    private void rebuild(@Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt) {
        LanguageManager lang = LanguageManager.get();

        cmd.set("#TitleLabel.Text", lang.tr("ui.packs.title"));
        cmd.set("#PackListTitle.Text", lang.tr("ui.packs.section_packs"));
        cmd.set("#RefreshPacksButton.Text", lang.tr("ui.packs.button_refresh"));
        cmd.set("#PackFolderHint.Text", lang.tr("ui.packs.folder_hint"));
        cmd.set("#ImportSectionTitle.Text", lang.tr("ui.packs.section_import"));
        cmd.set("#SelectedPackPrefix.Text", lang.tr("ui.packs.label_pack"));
        cmd.set("#ModePrefixLabel.Text", lang.tr("ui.packs.label_mode"));
        cmd.set("#ImportButton.Text", lang.tr("ui.packs.button_import"));
        cmd.set("#ExportSectionTitle.Text", lang.tr("ui.packs.section_export"));
        cmd.set("#ExportNamePrefix.Text", lang.tr("ui.packs.label_file"));
        cmd.set("#ExportCategoryPrefix.Text", lang.tr("ui.tags.label_category"));
        cmd.set("#ExportButton.Text", lang.tr("ui.packs.button_export"));
        cmd.set("#ResultsTitle.Text", lang.tr("ui.packs.results_title"));
        cmd.set("#FooterHint.Text", lang.tr("ui.packs.footer_hint"));
        cmd.set("#BottomCloseButton.Text", lang.tr("ui.common.close"));

        TagManager.TagPackImportMode mode = MODES[modeIndex];
        cmd.set("#ModeValueLabel.Text", mode.name());
        cmd.set("#ModeHint.Text", lang.tr("ui.packs.mode_hint_" + mode.name().toLowerCase(Locale.ROOT)));

        cmd.set("#SelectedPackLabel.Text", selectedPack == null
                ? lang.tr("ui.packs.no_selection")
                : selectedPack);

        List<String> categories = TagManager.get().getCategories();
        if (exportCategoryIndex > categories.size()) {
            exportCategoryIndex = 0;
        }
        String categoryLabel = exportCategoryIndex == 0
                ? lang.tr("ui.tags.category_all")
                : categories.get(exportCategoryIndex - 1);
        cmd.set("#ExportCategoryValue.Text", categoryLabel);

        cmd.set("#ResultsText.Text", String.join("\n", resultLines));

        rebuildPackList(cmd, evt, lang);
    }

    private void rebuildPackList(@Nonnull UICommandBuilder cmd,
                                 @Nonnull UIEventBuilder evt,
                                 @Nonnull LanguageManager lang) {
        cmd.clear("#PackList");

        List<File> packs = listPackFiles();
        cmd.set("#PackListEmpty.Visible", packs.isEmpty());

        int row = 0;
        for (File pack : packs) {
            cmd.append("#PackList", "mysticnametags/PackRow.ui");
            String rowSel = "#PackList[" + row + "]";

            boolean isSelected = pack.getName().equalsIgnoreCase(selectedPack);

            cmd.set(rowSel + " #Name.Text", pack.getName());
            cmd.set(rowSel + " #Meta.Text", lang.tr("ui.packs.pack_meta", Map.of(
                    "size", formatSize(pack.length()),
                    "date", PACK_DATE_FORMAT.format(Instant.ofEpochMilli(pack.lastModified()))
            )));

            cmd.set(rowSel + " #StatePill.Visible", isSelected);
            if (isSelected) {
                cmd.set(rowSel + " #State.Text", lang.tr("ui.packs.badge_selected"));
                cmd.set(rowSel + " #State.Style.TextColor", "#3fb950");
                cmd.set(rowSel + " #StatePill.OutlineColor", "#3fb950");
                cmd.set(rowSel + " #Accent.OutlineColor", "#3fb950");
            } else {
                cmd.set(rowSel + " #Accent.OutlineColor", "#3a3a3a");
            }

            evt.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    rowSel,
                    new EventData()
                            .append("Action", "select_pack")
                            .append("Pack", pack.getName()),
                    false
            );

            row++;
        }
    }

    @Nonnull
    private List<File> listPackFiles() {
        File dataFolder = MysticNameTagsPlugin.getInstance().getDataDirectory().toFile();
        File packFolder = new File(dataFolder, "tagpacks");
        packFolder.mkdirs();

        File[] files = packFolder.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null || files.length == 0) {
            return List.of();
        }

        List<File> out = new ArrayList<>(Arrays.asList(files));
        out.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Nonnull
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static class UIEventData {

        public static final BuilderCodec<UIEventData> CODEC =
                BuilderCodec.builder(UIEventData.class, UIEventData::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING),
                                (e, v) -> e.action = v,
                                e -> e.action)
                        .add()
                        .append(new KeyedCodec<>("Pack", Codec.STRING),
                                (e, v) -> e.pack = v,
                                e -> e.pack)
                        .add()
                        .append(new KeyedCodec<>("Value", Codec.STRING),
                                (e, v) -> e.value = v,
                                e -> e.value)
                        .add()
                        .build();

        public String action;
        public String pack;
        public String value;

        public UIEventData() {
        }
    }
}
