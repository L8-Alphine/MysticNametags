package com.mystichorizons.mysticnametags.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import com.mystichorizons.mysticnametags.tags.StorageBackend;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.MysticLog;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MysticNameTagsDashboardUI extends InteractiveCustomUIPage<MysticNameTagsDashboardUI.UIEventData> {

    public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(
                    UIEventData.class,
                    UIEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING),
                    (UIEventData e, String v) -> e.action = v,
                    e -> e.action)
            .add()
            .build();
    private static final String LAYOUT = "mysticnametags/Dashboard.ui";
    private static final String CURSEFORGE_URL =
            "https://www.curseforge.com/hytale/mods/mysticnametags";
    private static final String BUGREPORT_URL =
            "https://github.com/L8-Alphine/MysticNametags/issues";
    private static final String TAB_OVERVIEW = "Overview";
    private static final String TAB_INTEGRATIONS = "Integrations";
    private static final String TAB_DEBUG = "Debug";
    private static final String TAB_SUPPORT = "Support";
    private final PlayerRef playerRef;
    private String activeTab = TAB_OVERVIEW;

    public MysticNameTagsDashboardUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, CODEC);
        this.playerRef = playerRef;
    }

    private static String resolvePermissionBackendName(@Nonnull IntegrationManager integrations) {
        try {
            var backend = integrations.getPermissionsBackend();
            if (backend == null) return "None";
            try {
                String name = backend.getBackendName();
                if (name != null && !name.isBlank()) return name;
            } catch (Throwable ignored) {
            }
            return backend.getClass().getSimpleName();
        } catch (Throwable t) {
            return "None";
        }
    }

    private static String getActivePermissionBackendName(@Nonnull IntegrationManager integrations) {
        try {
            Method m = integrations.getClass().getMethod("getActivePermissionBackendName");
            Object result = m.invoke(integrations);
            if (result instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return resolvePermissionBackendName(integrations);
    }

    private static String getPlaytimeProviderName(@Nonnull IntegrationManager integrations) {
        try {
            Method m = integrations.getClass().getMethod("getPlaytimeProviderName");
            Object result = m.invoke(integrations);
            if (result instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Throwable ignored) {
        }

        try {
            Object provider = integrations.getPlaytimeProvider();
            return provider != null ? provider.getClass().getSimpleName() : "None";
        } catch (Throwable ignored) {
            return "None";
        }
    }

    private static boolean invokeBooleanMethodIfPresent(@Nonnull Object target, @Nonnull String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object result = m.invoke(target);
            return result instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
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
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        rs.cpuPercent = cpu;

        rs.availableProcessors = rt.availableProcessors();

        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();
        rs.uptimeMillis = runtimeMx.getUptime();

        return rs;
    }

    private static String formatUptime(long millis) {
        long seconds = millis / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;

        seconds %= 60L;
        minutes %= 60L;
        hours %= 24L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    @Nonnull
    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {

        LanguageManager lang = LanguageManager.get();
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();

        commands.append(LAYOUT);

        applyStaticText(commands);
        populateDynamicFields(commands);
        populateIntegrationDetails(commands);
        applyTabSelection(commands, activeTab);
        commands.set("#StatusText.Text", lang.tr("dashboard.welcome"));

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

        events.addEventBinding(CustomUIEventBindingType.Activating, "#OverviewTabButton",
                EventData.of("Action", "tab_overview"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#IntegrationsTabButton",
                EventData.of("Action", "tab_integrations"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DebugTabButton",
                EventData.of("Action", "tab_debug"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SupportTabButton",
                EventData.of("Action", "tab_support"));

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
                    refreshDashboard(lang.tr("dashboard.refreshed_status"));
                }

                case "reload" -> {
                    TagManager.reload();

                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&b" + lang.tr("plugin.title"),
                            lang.tr("dashboard.reloaded_toast"),
                            NotificationStyle.Success
                    );
                    refreshDashboard(lang.tr("dashboard.reloaded_status"));
                }

                case "open_mod_page" -> sendLinkToChatOrFallback(
                        "&b" + lang.tr("plugin.title") + " " + lang.tr("dashboard.modpage_title_suffix"),
                        "MysticNameTags Mod Page",
                        CURSEFORGE_URL
                );

                case "report_bug" -> sendLinkToChatOrFallback(
                        "&c" + lang.tr("plugin.title") + " " + lang.tr("dashboard.bugreport_title_suffix"),
                        "MysticNameTags Bug Reports",
                        BUGREPORT_URL
                );

                case "tab_overview" -> {
                    activeTab = TAB_OVERVIEW;
                    refreshDashboard(null);
                }

                case "tab_integrations" -> {
                    activeTab = TAB_INTEGRATIONS;
                    refreshDashboard(null);
                }

                case "tab_debug" -> {
                    activeTab = TAB_DEBUG;
                    refreshDashboard(null);
                }

                case "tab_support" -> {
                    activeTab = TAB_SUPPORT;
                    refreshDashboard(null);
                }

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

                    refreshDashboard(lang.tr("dashboard.cleared_cache_status"));
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

                    refreshDashboard(lang.tr("dashboard.refresh_nameplate_status"));
                }

                case "debug_snapshot" -> {
                    TagManager manager = TagManager.get();
                    IntegrationManager integrations = manager.getIntegrations();

                    String coloredTag = manager.getColoredActiveTag(uuid);
                    String plainTag = manager.getPlainActiveTag(uuid);

                    boolean lp = integrations.isLuckPermsAvailable();
                    boolean hyperPerms = integrations.isHyperPermsAvailable();
                    boolean permsPlus = integrations.isPermissionsPlusActive();

                    String permBackend = resolvePermissionBackendName(integrations);

                    boolean econPrimary = integrations.isPrimaryEconomyAvailable();
                    boolean econEcoTale = integrations.isEcoTaleAvailable();
                    boolean econVault = integrations.isVaultAvailable();
                    boolean econElite = integrations.isEliteEconomyAvailable();

                    Settings settings = Settings.get();
                    boolean wiFlowPlaceholders = settings.isWiFlowPlaceholdersEnabled();
                    boolean helpchPlaceholders = settings.isHelpchPlaceholderApiEnabled();

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
                            .append("  Active Tag (plain): ").append(plainTag).append('\n')
                            .append("  Permission Backend: ").append(permBackend).append('\n')
                            .append("  LuckPerms: ").append(lp).append('\n')
                            .append("  HyperPerms: ").append(hyperPerms).append('\n')
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

                    refreshDashboard(lang.tr("dashboard.debug_snapshot_status"));
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

    private void refreshDashboard(String statusOverride) {
        LanguageManager lang = LanguageManager.get();

        UICommandBuilder update = new UICommandBuilder();
        applyStaticText(update);
        populateDynamicFields(update);
        populateIntegrationDetails(update);
        applyTabSelection(update, activeTab);
        update.set("#StatusText.Text", statusOverride != null ? statusOverride : lang.tr("dashboard.welcome"));
        sendUpdate(update, null, false);
    }

    private void applyStaticText(@Nonnull UICommandBuilder commands) {
        LanguageManager lang = LanguageManager.get();

        commands.set("#TitleLabel.Text", lang.tr("ui.dashboard.title"));
        commands.set("#FooterCloseHint.Text", lang.tr("ui.dashboard.close_hint"));
        commands.set("#MainCloseHint.Text", lang.tr("ui.dashboard.close_hint"));

        commands.set("#QuickActionsHeader.Text", lang.tr("ui.dashboard.quick_actions"));

        commands.set("#OpenTagsButton.Text", lang.tr("ui.dashboard.action_open_tags"));
        commands.set("#ClearCacheButton.Text", lang.tr("ui.dashboard.action_clear_cache"));
        commands.set("#RefreshNameplateButton.Text", lang.tr("ui.dashboard.action_refresh_nameplate"));
        commands.set("#DebugSnapshotButton.Text", lang.tr("ui.dashboard.action_debug_snapshot"));

        commands.set("#RefreshButton.Text", lang.tr("ui.dashboard.button_refresh"));
        commands.set("#ReloadButton.Text", lang.tr("ui.dashboard.button_reload"));
        commands.set("#ModPageButton.Text", lang.tr("ui.dashboard.button_mod_page"));
        commands.set("#BugReportButton.Text", lang.tr("ui.dashboard.button_bug_report"));
        commands.set("#CloseButton.Text", lang.tr("ui.common.close"));

        commands.set("#OverviewHeader.Text", lang.tr("ui.dashboard.tab_overview").toUpperCase(Locale.ROOT));
        commands.set("#OverviewHintsHeader.Text", lang.tr("ui.dashboard.hints_header").toUpperCase(Locale.ROOT));

        commands.set("#IntegrationsHeader.Text", lang.tr("ui.dashboard.tab_integrations").toUpperCase(Locale.ROOT));
        commands.set("#PermissionsSectionHeader.Text", "PERMISSION BACKENDS");
        commands.set("#PrefixSectionHeader.Text", "PREFIX PROVIDERS");
        commands.set("#EconomySectionHeader.Text", "ECONOMY BACKENDS");
        commands.set("#PlaytimeSectionHeader.Text", "PLAYTIME / STATS / ITEMS");
        commands.set("#PlaceholderSectionHeader.Text", lang.tr("ui.dashboard.placeholders_header").toUpperCase(Locale.ROOT));
        commands.set("#NameplateSectionHeader.Text", "NAMEPLATE EXTENSIONS");
        commands.set("#NotesSectionHeader.Text", "NOTES");

        commands.set("#PermissionsCardTitle.Text", "PERMISSIONS");
        commands.set("#PrefixesCardTitle.Text", "PREFIXES");
        commands.set("#EconomyCardTitle.Text", "ECONOMY");
        commands.set("#PlaytimeCardTitle.Text", "PLAYTIME");
        commands.set("#PlaceholdersCardTitle.Text", "PLACEHOLDERS");
        commands.set("#NameplateCardTitle.Text", "NAMEPLATES");

        commands.set("#DebugHeader.Text", lang.tr("ui.dashboard.tab_debug_support").toUpperCase(Locale.ROOT));
        commands.set("#SupportHeader.Text", lang.tr("ui.dashboard.tab_support").toUpperCase(Locale.ROOT));

        commands.set("#OverviewLine0.Text", lang.tr("ui.dashboard.overview.line0"));
        commands.set("#OverviewLine1.Text", lang.tr("ui.dashboard.overview.line1"));
        commands.set("#OverviewLine2.Text", lang.tr("ui.dashboard.overview.line2"));
        commands.set("#OverviewLine3.Text", lang.tr("ui.dashboard.overview.line3"));

        commands.set("#OverviewHint0.Text", lang.tr("ui.dashboard.overview.hint0"));
        commands.set("#OverviewHint1.Text", lang.tr("ui.dashboard.overview.hint1"));
        commands.set("#OverviewHint2.Text", lang.tr("ui.dashboard.overview.hint2"));

        commands.set("#DebugLine0.Text", lang.tr("ui.dashboard.debug.line0"));
        commands.set("#DebugLine1.Text", lang.tr("ui.dashboard.debug.line1"));
        commands.set("#DebugLine2.Text", lang.tr("ui.dashboard.debug.line2"));
        commands.set("#DebugLine3.Text", lang.tr("ui.dashboard.debug.line3"));
        commands.set("#DebugLine4.Text", lang.tr("ui.dashboard.debug.line4"));
        commands.set("#DebugLine5.Text", lang.tr("ui.dashboard.debug.line5"));
        commands.set("#DebugLine6.Text", lang.tr("ui.dashboard.debug.line6"));

        commands.set("#SupportLine0.Text", lang.tr("ui.dashboard.support.line0"));
        commands.set("#SupportLine1.Text", lang.tr("ui.dashboard.support.line1"));
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

        boolean econPrimary = integrations.isPrimaryEconomyAvailable();
        boolean econEcoTale = integrations.isEcoTaleAvailable();
        boolean econVault = integrations.isVaultAvailable();
        boolean econElite = integrations.isEliteEconomyAvailable();

        Settings settings = Settings.get();
        boolean wiFlowPlaceholders = settings.isWiFlowPlaceholdersEnabled();
        boolean helpchPlaceholders = settings.isHelpchPlaceholderApiEnabled();

        StringBuilder integrationsText = new StringBuilder();
        integrationsText.append(lang.tr("dashboard.integrations_prefix")).append(" ");

        String permBackendName = resolvePermissionBackendName(integrations);
        if (permBackendName != null && !permBackendName.isBlank() && !"None".equalsIgnoreCase(permBackendName)) {
            integrationsText.append(permBackendName);
        } else {
            integrationsText.append("None");
        }

        integrationsText.append(" ").append(lang.tr("dashboard.integrations_economy_prefix")).append(" ");

        if (econPrimary) {
            integrationsText.append(lang.tr("dashboard.economy_primary"));
            if (econEcoTale || econVault || econElite) {
                integrationsText.append(" ").append(lang.tr("dashboard.economy_fallback_prefix"));
                boolean first = true;
                if (econEcoTale) {
                    integrationsText.append("EcoTale");
                    first = false;
                }
                if (econVault) {
                    if (!first) integrationsText.append(", ");
                    integrationsText.append("VaultUnlocked");
                    first = false;
                }
                if (econElite) {
                    if (!first) integrationsText.append(", ");
                    integrationsText.append("EliteEssentials");
                }
                integrationsText.append(")");
            }
        } else if (econEcoTale || econVault || econElite) {
            boolean first = true;
            if (econEcoTale) {
                integrationsText.append("EcoTale");
                first = false;
            }
            if (econVault) {
                if (!first) integrationsText.append(" + ");
                integrationsText.append("VaultUnlocked");
                first = false;
            }
            if (econElite) {
                if (!first) integrationsText.append(" + ");
                integrationsText.append("EliteEssentials");
            }
        } else {
            integrationsText.append(lang.tr("dashboard.economy_none"));
        }

        commands.set("#IntegrationsLabel.Text", integrationsText.toString().trim());

        commands.set("#TagCountLabel.Text", lang.tr("dashboard.loaded_tags_label", Map.of(
                "count", String.valueOf(tagManager.getTagCount())
        )));

        StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());
        commands.set("#StorageLabel.Text", buildStorageLabel(lang, settings, backend));

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

    private void applyTabSelection(@Nonnull UICommandBuilder commands,
                                   @Nonnull String tabKey) {

        boolean overview = TAB_OVERVIEW.equalsIgnoreCase(tabKey);
        boolean integrations = TAB_INTEGRATIONS.equalsIgnoreCase(tabKey);
        boolean debug = TAB_DEBUG.equalsIgnoreCase(tabKey);
        boolean support = TAB_SUPPORT.equalsIgnoreCase(tabKey);

        commands.set("#DashboardTabs.SelectedTab", overview ? TAB_OVERVIEW
                : integrations ? TAB_INTEGRATIONS
                : debug ? TAB_DEBUG
                : TAB_SUPPORT);

        commands.set("#TabOverviewPanel.Visible", overview);
        commands.set("#TabIntegrationsPanel.Visible", integrations);
        commands.set("#TabDebugPanel.Visible", debug);
        commands.set("#TabSupportPanel.Visible", support);

        LanguageManager lang = LanguageManager.get();
        String activeTitle;
        if (overview) {
            activeTitle = lang.tr("ui.dashboard.tab_overview");
        } else if (integrations) {
            activeTitle = lang.tr("ui.dashboard.tab_integrations");
        } else if (debug) {
            activeTitle = lang.tr("ui.dashboard.tab_debug_support");
        } else {
            activeTitle = lang.tr("ui.dashboard.tab_support");
        }

        commands.set("#ActiveTabTitle.Text", activeTitle);
    }

    private void populateIntegrationDetails(@Nonnull UICommandBuilder commands) {
        IntegrationManager integrations = TagManager.get().getIntegrations();
        Settings settings = Settings.get();

        boolean luckPerms = integrations.isLuckPermsAvailable();
        boolean hyperPerms = integrations.isHyperPermsAvailable();
        boolean permsPlus = integrations.isPermissionsPlusActive();

        boolean prefixesPlus = invokeBooleanMethodIfPresent(integrations, "isPrefixesPlusAvailable");
        boolean anyPrefixProvider = prefixesPlus || hyperPerms || luckPerms
                || invokeBooleanMethodIfPresent(integrations, "isAnyPrefixProviderAvailable");

        boolean economyEnabled = settings.isEconomySystemEnabled();
        boolean physicalCoinsConfigured = settings.isUsePhysicalCoinEconomy();
        boolean useCoinSystem = settings.isUseCoinSystem();

        boolean economyPrimary = integrations.isPrimaryEconomyAvailable();
        boolean hyEssentialsX = integrations.isHyEssentialsXAvailable();
        boolean ecoTale = integrations.isEcoTaleAvailable();
        boolean vault = integrations.isVaultAvailable();
        boolean elite = integrations.isEliteEconomyAvailable();
        boolean coinsAndMarkets = invokeBooleanMethodIfPresent(integrations, "isCoinsAndMarketsAvailable");

        boolean wiflow = settings.isWiFlowPlaceholdersEnabled();
        boolean helpch = settings.isHelpchPlaceholderApiEnabled();

        boolean statProvider = integrations.getStatProvider() != null
                || invokeBooleanMethodIfPresent(integrations, "isStatProviderAvailable");
        boolean itemHandler = invokeBooleanMethodIfPresent(integrations, "isItemRequirementHandlerAvailable");
        boolean endlessNameplate = invokeBooleanMethodIfPresent(integrations, "isEndlessLevelingNameplateAttached");

        String playtimeProviderName = getPlaytimeProviderName(integrations);
        String activePermissionBackend = getActivePermissionBackendName(integrations);

        commands.set("#IntegrationsIntro0.Text",
                "This panel shows the currently detected backends and which integration paths MysticNameTags is using.");
        commands.set("#IntegrationsIntro1.Text",
                "Use this page to verify permission, economy, placeholder, playtime, and extended nameplate support.");

        commands.set("#PermissionsCardStatus.Text", "Active: " + activePermissionBackend);
        commands.set("#PermissionsCardMeta.Text",
                "LuckPerms: " + yesNo(luckPerms) + " | HyperPerms: " + yesNo(hyperPerms));

        String prefixProvider;
        if (prefixesPlus) {
            prefixProvider = "PrefixesPlus";
        } else if (hyperPerms) {
            prefixProvider = "HyperPerms";
        } else if (luckPerms) {
            prefixProvider = "LuckPerms";
        } else {
            prefixProvider = "None";
        }
        commands.set("#PrefixesCardStatus.Text", "Provider: " + prefixProvider);
        commands.set("#PrefixesCardMeta.Text", "Available: " + yesNo(anyPrefixProvider));

        commands.set("#EconomyCardStatus.Text", "Mode: " + integrations.getEconomyMode().name());
        commands.set("#EconomyCardMeta.Text", "Available: " + yesNo(integrations.hasAnyEconomy()));

        commands.set("#PlaytimeCardStatus.Text", "Provider: " + playtimeProviderName);
        commands.set("#PlaytimeCardMeta.Text",
                "Stats: " + yesNo(statProvider) + " | Items: " + yesNo(itemHandler));

        commands.set("#PlaceholdersCardStatus.Text", "WiFlow: " + yesNo(wiflow));
        commands.set("#PlaceholdersCardMeta.Text", "Helpch: " + yesNo(helpch));

        commands.set("#NameplateCardStatus.Text", "EndlessLeveling: " + yesNo(endlessNameplate));
        commands.set("#NameplateCardMeta.Text",
                endlessNameplate ? "Extended rendering active" : "Standard rendering active");

        commands.set("#PermissionsLine0.Text", "Active backend: " + activePermissionBackend);
        commands.set("#PermissionsLine1.Text", "LuckPerms detected: " + yesNo(luckPerms)
                + " | HyperPerms detected: " + yesNo(hyperPerms));
        commands.set("#PermissionsLine2.Text", "PermissionsPlus active: " + yesNo(permsPlus));
        commands.set("#PermissionsLine3.Text",
                "If no external permissions plugin is available, MysticNameTags falls back to native Hytale permissions.");

        commands.set("#PrefixLine0.Text", "PrefixesPlus detected: " + yesNo(prefixesPlus));
        commands.set("#PrefixLine1.Text", "Fallback prefix sources: HyperPerms ChatAPI -> LuckPerms meta");
        commands.set("#PrefixLine2.Text", "Prefix provider available: " + yesNo(anyPrefixProvider));

        commands.set("#EconomyLine0.Text", "Economy system enabled in settings: " + yesNo(economyEnabled));
        commands.set("#EconomyLine1.Text",
                "Economy mode: " + integrations.getEconomyMode().name()
                        + " | Physical coins configured: " + yesNo(physicalCoinsConfigured)
                        + " | Coin system ledger enabled: " + yesNo(useCoinSystem));
        commands.set("#EconomyLine2.Text",
                "Detected backends: EconomySystem=" + yesNo(economyPrimary)
                        + ", HyEssentialsX=" + yesNo(hyEssentialsX)
                        + ", EcoTale=" + yesNo(ecoTale)
                        + ", VaultUnlocked=" + yesNo(vault)
                        + ", EliteEssentials=" + yesNo(elite));
        commands.set("#EconomyLine3.Text", "CoinsAndMarkets physical backend: " + yesNo(coinsAndMarkets));
        commands.set("#EconomyLine4.Text", "Any economy available: " + yesNo(integrations.hasAnyEconomy()));

        commands.set("#PlaytimeLine0.Text", "Playtime provider: " + playtimeProviderName);
        commands.set("#PlaytimeLine1.Text", "Stat provider attached: " + yesNo(statProvider));
        commands.set("#PlaytimeLine2.Text", "Item requirement handler attached: " + yesNo(itemHandler));
        commands.set("#PlaytimeLine3.Text", "Configured playtime mode: " + settings.getPlaytimeProviderMode());

        commands.set("#PlaceholderLine0.Text", "WiFlow placeholders enabled: " + yesNo(wiflow));
        commands.set("#PlaceholderLine1.Text", "at.helpch PlaceholderAPI enabled: " + yesNo(helpch));
        commands.set("#PlaceholderLine2.Text", "Placeholder resolution order: WiFlow -> at.helpch");

        commands.set("#NameplateLine0.Text",
                "EndlessLeveling nameplate system attached: " + yesNo(endlessNameplate));
        commands.set("#NameplateLine1.Text",
                "EndlessLeveling stat bridge support is used for keys beginning with 'endlessleveling.'");
        commands.set("#NameplateLine2.Text",
                "If no extension is attached, standard tag + player name rendering remains active.");

        commands.set("#NotesLine0.Text",
                "Permission priority: LuckPerms -> HyperPerms -> PermissionsPlus -> Native");
        commands.set("#NotesLine1.Text",
                "Prefix priority: PrefixesPlus -> HyperPerms -> LuckPerms");
        commands.set("#NotesLine2.Text",
                "Economy priority depends on settings and detected backends.");
        commands.set("#NotesLine3.Text",
                "Physical coin mode uses CoinsAndMarkets inventory/coin handling instead of ledger balance withdrawal.");
        commands.set("#NotesLine4.Text",
                "This page is informational only; use Debug Snapshot for a log-oriented integration dump.");
    }

    private String buildStorageLabel(LanguageManager lang, Settings settings, StorageBackend backend) {
        return switch (backend) {
            case FILE -> lang.tr("dashboard.storage_file", Map.of());
            case SQLITE -> lang.tr("dashboard.storage_sqlite", Map.of("file", settings.getSqliteFile()));
            case MYSQL -> lang.tr("dashboard.storage_mysql", Map.of(
                    "host", settings.getMysqlHost(),
                    "port", String.valueOf(settings.getMysqlPort()),
                    "database", settings.getMysqlDatabase()
            ));
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
                "percent", String.format(Locale.ROOT, "%.1f", rs.cpuPercent),
                "cores", String.valueOf(rs.availableProcessors)
        ))
                : lang.tr("dashboard.cpu_na");

        commands.set("#CpuLabel.Text", cpuText);
        commands.set("#UptimeLabel.Text", lang.tr("dashboard.uptime_label", Map.of(
                "uptime", formatUptime(rs.uptimeMillis)
        )));
    }

    private void sendLinkToChatOrFallback(@Nonnull String title,
                                          @Nonnull String label,
                                          @Nonnull String url) {
        String plainMessage = label + ": " + url;

        if (trySendChatLine(plainMessage)) {
            MysticNotificationUtil.send(
                    playerRef.getPacketHandler(),
                    title,
                    "Link sent to chat.",
                    NotificationStyle.Success
            );
            return;
        }

        MysticNotificationUtil.send(
                playerRef.getPacketHandler(),
                title,
                plainMessage,
                NotificationStyle.Default
        );
    }

    private boolean trySendChatLine(@Nonnull String message) {
        Object packetHandler = null;
        try {
            packetHandler = playerRef.getPacketHandler();
        } catch (Throwable ignored) {
        }

        if (invokeSingleStringMethod(packetHandler, "sendChatMessage", message)) return true;
        if (invokeSingleStringMethod(packetHandler, "sendSystemMessage", message)) return true;
        if (invokeSingleStringMethod(packetHandler, "sendMessage", message)) return true;
        if (invokeSingleStringMethod(packetHandler, "sendRawChatMessage", message)) return true;

        if (invokeSingleStringMethod(playerRef, "sendChatMessage", message)) return true;
        if (invokeSingleStringMethod(playerRef, "sendSystemMessage", message)) return true;
        if (invokeSingleStringMethod(playerRef, "sendMessage", message)) return true;

        return false;
    }

    private boolean invokeSingleStringMethod(@Nullable Object target,
                                             @Nonnull String methodName,
                                             @Nonnull String value) {
        if (target == null) return false;
        try {
            Method m = target.getClass().getMethod(methodName, String.class);
            m.invoke(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
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

        public UIEventData() {
        }

        public String getAction() {
            return action;
        }
    }
}