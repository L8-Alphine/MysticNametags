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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.tags.StorageBackend;
import com.mystichorizons.mysticnametags.util.MysticLog;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

import javax.annotation.Nonnull;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.Map;
import java.util.UUID;

public class MysticNameTagsDashboardUI extends InteractiveCustomUIPage<MysticNameTagsDashboardUI.UIEventData> {

    private static final String CURSEFORGE_URL =
            "https://www.curseforge.com/hytale/mods/mysticnametags";
    private static final String BUGREPORT_URL =
            "https://github.com/L8-Alphine/MysticNametags/issues";

    public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(
                    UIEventData.class,
                    UIEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING),
                    (UIEventData e, String v) -> e.action = v,
                    e -> e.action)
            .add()
            .build();

    private final PlayerRef playerRef;

    public MysticNameTagsDashboardUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {

        LanguageManager lang = LanguageManager.get();
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();

        // Load the dashboard UI layout (supports localized override)
        commands.append(lang.resolveUi("mysticnametags/Dashboard.ui"));

        // Initial text
        commands.set("#StatusText.Text", lang.tr("dashboard.welcome"));

        // Populate dynamic labels (version, integrations, storage, resources...)
        populateDynamicFields(commands);

        // Update notifier for admins
        UpdateChecker checker = plugin.getUpdateChecker();
        if (checker != null && checker.hasVersionInfo()) {
            IntegrationManager integrations = TagManager.get().getIntegrations();

            if (integrations.hasPermission(playerRef, "mysticnametags.admin.update")) {
                String current = plugin.getResolvedVersion();
                String latest = checker.getLatestVersion();

                if (checker.isUpdateAvailable()) {
                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&b" + lang.tr("plugin.title") + " &7(&f" + lang.tr("dashboard.update_title") + "&7)",
                            lang.tr("dashboard.update_available", Map.of(
                                    "latest", latest,
                                    "current", current,
                                    "url", CURSEFORGE_URL
                            )),
                            NotificationStyle.Default
                    );
                } else if (checker.isCurrentAheadOfLatest()) {
                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&b" + lang.tr("plugin.title") + " &7(&f" + lang.tr("dashboard.devbuild_title") + "&7)",
                            lang.tr("dashboard.devbuild_ahead", Map.of(
                                    "current", current,
                                    "latest", latest
                            )),
                            NotificationStyle.Default
                    );
                }
            }
        }

        // Default tab selection
        applyTabSelection(commands, "overview");

        // Sidebar/tab buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#OverviewTabButton",
                EventData.of("Action", "tab_overview"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#IntegrationsTabButton",
                EventData.of("Action", "tab_integrations"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DebugTabButton",
                EventData.of("Action", "tab_debug"));

        // Main footer buttons
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton",
                EventData.of("Action", "refresh"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ReloadButton",
                EventData.of("Action", "reload"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ModPageButton",
                EventData.of("Action", "open_mod_page"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BugReportButton",
                EventData.of("Action", "report_bug"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", "close"));

        // Quick Actions
        events.addEventBinding(CustomUIEventBindingType.Activating, "#OpenTagsButton",
                EventData.of("Action", "open_tag_ui"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearCacheButton",
                EventData.of("Action", "clear_cache"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshNameplateButton",
                EventData.of("Action", "refresh_nameplate"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DebugSnapshotButton",
                EventData.of("Action", "debug_snapshot"));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {

        if (data.action == null) return;

        LanguageManager lang = LanguageManager.get();
        UUID uuid = playerRef.getUuid();
        String action = data.action;

        try {
            switch (action) {

                case "refresh" -> {
                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&b" + lang.tr("plugin.title"),
                            lang.tr("dashboard.refreshed_toast"),
                            NotificationStyle.Success
                    );

                    UICommandBuilder update = new UICommandBuilder();
                    update.set("#StatusText.Text", lang.tr("dashboard.refreshed_status"));
                    populateDynamicFields(update);
                    sendUpdate(update, null, false);
                }

                case "reload" -> {
                    TagManager.reload();

                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&b" + lang.tr("plugin.title"),
                            lang.tr("dashboard.reloaded_toast"),
                            NotificationStyle.Success
                    );

                    UICommandBuilder update = new UICommandBuilder();
                    update.set("#StatusText.Text", lang.tr("dashboard.reloaded_status"));
                    populateDynamicFields(update);
                    sendUpdate(update, null, false);
                }

                case "open_mod_page" -> MysticNotificationUtil.send(
                        playerRef.getPacketHandler(),
                        "&b" + lang.tr("plugin.title") + " " + lang.tr("dashboard.modpage_title_suffix"),
                        lang.tr("dashboard.modpage_open", Map.of("url", CURSEFORGE_URL)),
                        NotificationStyle.Default
                );

                case "report_bug" -> MysticNotificationUtil.send(
                        playerRef.getPacketHandler(),
                        "&c" + lang.tr("plugin.title") + " " + lang.tr("dashboard.bugreport_title_suffix"),
                        lang.tr("dashboard.bugreport_open", Map.of("url", BUGREPORT_URL)),
                        NotificationStyle.Default
                );

                case "tab_overview" -> {
                    UICommandBuilder update = new UICommandBuilder();
                    applyTabSelection(update, "overview");
                    sendUpdate(update, null, false);
                }

                case "tab_integrations" -> {
                    UICommandBuilder update = new UICommandBuilder();
                    applyTabSelection(update, "integrations");
                    sendUpdate(update, null, false);
                }

                case "tab_debug" -> {
                    UICommandBuilder update = new UICommandBuilder();
                    applyTabSelection(update, "debug");
                    sendUpdate(update, null, false);
                }

                // --- Quick Actions ---

                case "open_tag_ui" -> {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) {
                        MysticNotificationUtil.send(
                                playerRef.getPacketHandler(),
                                "&c" + lang.tr("plugin.title"),
                                lang.tr("dashboard.open_tags_failed"),
                                NotificationStyle.Warning
                        );
                        MysticLog.warn("Dashboard open_tag_ui failed for "
                                + playerRef.getUsername() + " – no Player component.");
                        return;
                    }

                    MysticNameTagsTagsUI tagsPage = new MysticNameTagsTagsUI(playerRef, uuid);
                    player.getPageManager().openCustomPage(ref, store, tagsPage);

                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&b" + lang.tr("plugin.title"),
                            lang.tr("dashboard.opened_tags_toast"),
                            NotificationStyle.Success
                    );
                }

                case "clear_cache" -> {
                    TagManager manager = TagManager.get();
                    manager.clearCanUseCache(uuid);
                    manager.forgetNameplate(uuid);

                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&b" + lang.tr("plugin.title"),
                            lang.tr("dashboard.cleared_cache_toast"),
                            NotificationStyle.Success
                    );

                    UICommandBuilder update = new UICommandBuilder();
                    update.set("#StatusText.Text", lang.tr("dashboard.cleared_cache_status"));
                    populateDynamicFields(update);
                    sendUpdate(update, null, false);
                }

                case "refresh_nameplate" -> {
                    TagManager manager = TagManager.get();
                    World world = manager.getOnlineWorld(uuid);

                    if (world == null) {
                        MysticNotificationUtil.send(
                                playerRef.getPacketHandler(),
                                "&c" + lang.tr("plugin.title"),
                                lang.tr("dashboard.refresh_nameplate_world_missing"),
                                NotificationStyle.Warning
                        );
                        return;
                    }

                    manager.refreshNameplate(playerRef, world);

                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&b" + lang.tr("plugin.title"),
                            lang.tr("dashboard.refresh_nameplate_toast"),
                            NotificationStyle.Success
                    );

                    UICommandBuilder update = new UICommandBuilder();
                    update.set("#StatusText.Text", lang.tr("dashboard.refresh_nameplate_status"));
                    populateDynamicFields(update);
                    sendUpdate(update, null, false);
                }

                case "debug_snapshot" -> {
                    TagManager manager = TagManager.get();
                    IntegrationManager integrations = manager.getIntegrations();

                    String coloredTag = manager.getColoredActiveTag(uuid);
                    String plainTag   = manager.getPlainActiveTag(uuid);

                    boolean lp          = integrations.isLuckPermsAvailable();
                    boolean permsPlus   = integrations.isPermissionsPlusActive();
                    boolean econPrimary = integrations.isPrimaryEconomyAvailable();
                    boolean econEcoTale = integrations.isEcoTaleAvailable();
                    boolean econVault   = integrations.isVaultAvailable();
                    boolean econElite   = integrations.isEliteEconomyAvailable();

                    Settings settings = Settings.get();
                    boolean wiFlowPlaceholders = settings.isWiFlowPlaceholdersEnabled();
                    boolean helpchPlaceholders = settings.isHelpchPlaceholderApiEnabled();

                    // Storage info
                    StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());
                    String storageDetails = buildStorageDetailsForDebug(settings, backend);

                    MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
                    String version = plugin.getResolvedVersion();

                    ResourceSnapshot rs = captureResourceSnapshot();

                    StringBuilder sb = new StringBuilder();
                    sb.append("[MysticNameTags DEBUG] Player=")
                            .append(playerRef.getUsername())
                            .append(" (").append(uuid).append(")\n")
                            .append("  Version: ").append(version).append('\n')
                            .append("  Tags Loaded: ").append(manager.getTagCount()).append('\n')
                            .append("  Active Tag (colored): ").append(coloredTag).append('\n')
                            .append("  Active Tag (plain):   ").append(plainTag).append('\n')
                            .append("  LuckPerms: ").append(lp).append('\n')
                            .append("  PermissionsPlus active: ").append(permsPlus).append('\n')
                            .append("  Economy: primary=").append(econPrimary)
                            .append(", ecoTale=").append(econEcoTale)
                            .append(", vault=").append(econVault)
                            .append(", elite=").append(econElite).append('\n')
                            .append("  Placeholders: WiFlow=").append(wiFlowPlaceholders)
                            .append(", helpch=").append(helpchPlaceholders).append('\n')
                            .append("  Storage: ").append(storageDetails).append('\n')
                            .append("  Uptime: ").append(formatUptime(rs.uptimeMillis)).append('\n');

                    MysticLog.debug(sb.toString());

                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&b" + lang.tr("plugin.title"),
                            lang.tr("dashboard.debug_snapshot_toast"),
                            NotificationStyle.Default
                    );

                    UICommandBuilder update = new UICommandBuilder();
                    update.set("#StatusText.Text", lang.tr("dashboard.debug_snapshot_status"));
                    populateDynamicFields(update);
                    sendUpdate(update, null, false);
                }

                case "close" -> close();

                default -> close();
            }

        } catch (Throwable t) {
            MysticLog.error("Unhandled exception in MysticNameTagsDashboardUI.handleDataEvent for "
                    + playerRef.getUsername() + " action=" + action, t);

            MysticNotificationUtil.send(
                    playerRef.getPacketHandler(),
                    "&c" + lang.tr("plugin.title"),
                    lang.tr("dashboard.internal_error_toast"),
                    NotificationStyle.Warning
            );
        }
    }

    private void populateDynamicFields(@Nonnull UICommandBuilder commands) {
        LanguageManager lang = LanguageManager.get();

        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        UpdateChecker checker = plugin.getUpdateChecker();

        String version = plugin.getResolvedVersion();

        String versionLabel = lang.tr("dashboard.version_label", Map.of("version", version));

        if (checker != null && checker.hasVersionInfo()) {
            String latest = checker.getLatestVersion();
            if (checker.isUpdateAvailable()) {
                versionLabel = lang.tr("dashboard.version_label_update", Map.of(
                        "version", version,
                        "latest", latest
                ));
            } else if (checker.isCurrentAheadOfLatest()) {
                versionLabel = lang.tr("dashboard.version_label_ahead", Map.of(
                        "version", version,
                        "latest", latest
                ));
            }
        }
        commands.set("#VersionLabel.Text", versionLabel);

        TagManager tagManager = TagManager.get();
        IntegrationManager integrations = tagManager.getIntegrations();

        boolean lp          = integrations.isLuckPermsAvailable();
        boolean permsPlus   = integrations.isPermissionsPlusActive();
        boolean econPrimary = integrations.isPrimaryEconomyAvailable();
        boolean econEcoTale = integrations.isEcoTaleAvailable();
        boolean econVault   = integrations.isVaultAvailable();
        boolean econElite   = integrations.isEliteEconomyAvailable();

        Settings settings = Settings.get();
        boolean wiFlowPlaceholders = settings.isWiFlowPlaceholdersEnabled();
        boolean helpchPlaceholders = settings.isHelpchPlaceholderApiEnabled();

        // --- Integrations summary ---
        String luckPermsText = lp
                ? lang.tr("dashboard.integration_luckperms")
                : lang.tr("dashboard.integration_luckperms_none");

        StringBuilder integrationsText = new StringBuilder();
        integrationsText.append(lang.tr("dashboard.integrations_prefix")).append(" ");
        integrationsText.append(luckPermsText);

        if (permsPlus) {
            integrationsText.append(" ").append(lang.tr("dashboard.integrations_plus_permissionsplus"));
        }

        integrationsText.append(" | ").append(lang.tr("dashboard.integrations_economy_prefix")).append(" ");

        if (econPrimary) {
            integrationsText.append(lang.tr("dashboard.economy_primary"));
            if (econEcoTale || econVault || econElite) {
                integrationsText.append(" ").append(lang.tr("dashboard.economy_fallback_prefix")).append(" ");
                boolean first = true;
                if (econEcoTale) { integrationsText.append("EcoTale"); first = false; }
                if (econVault)   { if (!first) integrationsText.append(", "); integrationsText.append("VaultUnlocked"); first = false; }
                if (econElite)   { if (!first) integrationsText.append(", "); integrationsText.append("EliteEssentials"); }
                integrationsText.append(")");
            }
        } else if (econEcoTale || econVault || econElite) {
            boolean first = true;
            if (econEcoTale) { integrationsText.append("EcoTale"); first = false; }
            if (econVault)   { if (!first) integrationsText.append(" + "); integrationsText.append("VaultUnlocked"); first = false; }
            if (econElite)   { if (!first) integrationsText.append(" + "); integrationsText.append("EliteEssentials"); }
        } else {
            integrationsText.append(lang.tr("dashboard.economy_none"));
        }

        commands.set("#IntegrationsLabel.Text", integrationsText.toString().trim());

        // Tags loaded
        commands.set("#TagCountLabel.Text", lang.tr("dashboard.loaded_tags_label", Map.of(
                "count", String.valueOf(tagManager.getTagCount())
        )));

        // --- Storage summary ---
        StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());
        String storageLabel = buildStorageLabel(lang, settings, backend);
        commands.set("#StorageLabel.Text", storageLabel);

        // --- Placeholders ---
        StringBuilder placeholderText = new StringBuilder();
        placeholderText.append(lang.tr("dashboard.placeholders_prefix")).append(" ");

        if (wiFlowPlaceholders || helpchPlaceholders) {
            boolean first = true;
            if (wiFlowPlaceholders) {
                placeholderText.append("WiFlowPlaceholderAPI");
                first = false;
            }
            if (helpchPlaceholders) {
                if (!first) placeholderText.append(" + ");
                placeholderText.append("at.helpch PlaceholderAPI");
            }
        } else {
            placeholderText.append(lang.tr("dashboard.placeholders_none"));
        }

        commands.set("#PlaceholderBackendsLabel.Text", placeholderText.toString());

        populateResourceStats(commands);
    }

    private String buildStorageLabel(LanguageManager lang, Settings settings, StorageBackend backend) {
        return switch (backend) {
            case FILE -> lang.tr("dashboard.storage_file", Map.of());
            case SQLITE -> {
                String file = settings.getSqliteFile();
                yield lang.tr("dashboard.storage_sqlite", Map.of("file", file));
            }
            case MYSQL -> {
                String host = settings.getMysqlHost();
                int port    = settings.getMysqlPort();
                String db   = settings.getMysqlDatabase();
                yield lang.tr("dashboard.storage_mysql", Map.of(
                        "host", host,
                        "port", String.valueOf(port),
                        "database", db
                ));
            }
        };
    }

    private String buildStorageDetailsForDebug(Settings settings, StorageBackend backend) {
        return switch (backend) {
            case FILE -> "FILE (playerdata/*.json)";
            case SQLITE -> "SQLITE file=" + settings.getSqliteFile();
            case MYSQL -> "MYSQL " + settings.getMysqlHost() + ":" + settings.getMysqlPort()
                    + "/" + settings.getMysqlDatabase();
        };
    }

    private void populateResourceStats(@Nonnull UICommandBuilder commands) {
        LanguageManager lang = LanguageManager.get();

        ResourceSnapshot rs = captureResourceSnapshot();

        commands.set("#RamLabel.Text",
                lang.tr("dashboard.ram_label", Map.of(
                        "used", String.valueOf(rs.usedMb),
                        "max", String.valueOf(rs.maxMb)
                )));

        String cpuText = (rs.cpuPercent >= 0.0)
                ? lang.tr("dashboard.cpu_label", Map.of(
                "percent", String.format(java.util.Locale.ROOT, "%.1f", rs.cpuPercent),
                "cores", String.valueOf(rs.availableProcessors)
        ))
                : lang.tr("dashboard.cpu_na");

        commands.set("#CpuLabel.Text", cpuText);

        commands.set("#UptimeLabel.Text", lang.tr("dashboard.uptime_label", Map.of(
                "uptime", formatUptime(rs.uptimeMillis)
        )));
    }

    private void applyTabSelection(@Nonnull UICommandBuilder commands,
                                   @Nonnull String tabKey) {

        boolean overview     = "overview".equalsIgnoreCase(tabKey);
        boolean integrations = "integrations".equalsIgnoreCase(tabKey);
        boolean debug        = "debug".equalsIgnoreCase(tabKey);

        commands.set("#TabOverviewPanel.Visible", overview);
        commands.set("#TabIntegrationsPanel.Visible", integrations);
        commands.set("#TabDebugPanel.Visible", debug);

        commands.set("#OverviewTabButtonSelected.Visible", overview);
        commands.set("#OverviewTabButton.Visible", !overview);

        commands.set("#IntegrationsTabButtonSelected.Visible", integrations);
        commands.set("#IntegrationsTabButton.Visible", !integrations);

        commands.set("#DebugTabButtonSelected.Visible", debug);
        commands.set("#DebugTabButton.Visible", !debug);
    }

    private static ResourceSnapshot captureResourceSnapshot() {
        ResourceSnapshot rs = new ResourceSnapshot();

        Runtime rt = Runtime.getRuntime();
        rs.usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        rs.maxMb = rt.maxMemory() / (1024L * 1024L);

        double cpu = -1.0;
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            try {
                var method = osBean.getClass().getMethod("getProcessCpuLoad");
                Object value = method.invoke(osBean);
                if (value instanceof Double d && d >= 0.0) cpu = d * 100.0;
            } catch (Throwable ignored) { }
        } catch (Throwable ignored) { }
        rs.cpuPercent = cpu;

        rs.availableProcessors = rt.availableProcessors();

        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();
        rs.uptimeMillis = runtimeMx.getUptime();

        return rs;
    }

    private static String formatUptime(long millis) {
        long seconds = millis / 1000L;
        long minutes = seconds / 60L;
        long hours   = minutes / 60L;
        long days    = hours / 24L;

        seconds %= 60L;
        minutes %= 60L;
        hours   %= 24L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    private static final class ResourceSnapshot {
        long usedMb;
        long maxMb;
        double cpuPercent;
        int availableProcessors;
        long uptimeMillis;
    }

    public static class UIEventData {
        private String action;

        public UIEventData() { }

        public String getAction() {
            return action;
        }
    }
}
