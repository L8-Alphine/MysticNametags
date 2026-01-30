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
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

import javax.annotation.Nonnull;
import java.lang.management.ManagementFactory;
import java.util.UUID;
import java.util.logging.Level;

public class MysticNameTagsDashboardUI extends InteractiveCustomUIPage<MysticNameTagsDashboardUI.UIEventData> {

    private static final String CURSEFORGE_URL =
            "https://www.curseforge.com/hytale/mods/mysticnametags";
    private static final String BUGREPORT_URL =
            "https://github.com/L8-Alphine/MysticNametags/issues";
    private static final HytaleLogger LOGGER = MysticNameTagsPlugin.getInstance().getLogger();

    // Codec for decoding incoming UI event data
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

        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();

        // Load the dashboard UI layout
        commands.append("mysticnametags/Dashboard.ui");

        // Initial text
        commands.set("#StatusText.Text", "Welcome to MysticNameTags!");

        // Populate dynamic labels (version, integrations, tags, CPU/RAM)
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
                            "&bMysticNameTags &7(&fUpdate&7)",
                            "&eA new version is available: &f" + latest +
                                    " &7(you are on &f" + current + "&7). " +
                                    "&7Visit &f" + CURSEFORGE_URL + " &7for downloads.",
                            NotificationStyle.Default
                    );
                } else if (checker.isCurrentAheadOfLatest()) {
                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&bMysticNameTags &7(&fDev Build&7)",
                            "&7You are running &f" + current +
                                    "&7 which is &bahead &7of the latest CurseForge release (&f" +
                                    latest + "&7).",
                            NotificationStyle.Default
                    );
                }
            }
        }

        // Default tab selection
        applyTabSelection(commands, "overview");

        // Tab buttons
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#OverviewTabButton",
                EventData.of("Action", "tab_overview")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#IntegrationsTabButton",
                EventData.of("Action", "tab_integrations")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DebugTabButton",
                EventData.of("Action", "tab_debug")
        );

        // Main footer buttons
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RefreshButton",
                EventData.of("Action", "refresh")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ReloadButton",
                EventData.of("Action", "reload")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ModPageButton",
                EventData.of("Action", "open_mod_page")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#BugReportButton",
                EventData.of("Action", "report_bug")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "close")
        );

        // Quick Actions
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#OpenTagsButton",
                EventData.of("Action", "open_tag_ui")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearCacheButton",
                EventData.of("Action", "clear_cache")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RefreshNameplateButton",
                EventData.of("Action", "refresh_nameplate")
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DebugSnapshotButton",
                EventData.of("Action", "debug_snapshot")
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {

        if (data.action == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();

        switch (data.action) {
            case "refresh" -> {
                MysticNotificationUtil.send(
                        playerRef.getPacketHandler(),
                        "&bMysticNameTags",
                        "&7Dashboard refreshed.",
                        NotificationStyle.Success
                );

                UICommandBuilder update = new UICommandBuilder();
                update.set("#StatusText.Text", "Dashboard refreshed!");
                populateDynamicFields(update);
                sendUpdate(update, null, false);
            }

            case "reload" -> {
                TagManager.reload();

                MysticNotificationUtil.send(
                        playerRef.getPacketHandler(),
                        "&bMysticNameTags",
                        "&7Configuration reloaded from &ftags.json&7.",
                        NotificationStyle.Success
                );

                UICommandBuilder update = new UICommandBuilder();
                update.set("#StatusText.Text", "Config reloaded from disk.");
                populateDynamicFields(update);
                sendUpdate(update, null, false);
            }

            case "open_mod_page" -> {
                MysticNotificationUtil.send(
                        playerRef.getPacketHandler(),
                        "&bMysticNameTags Mod Page",
                        "&7Open: &f" + CURSEFORGE_URL,
                        NotificationStyle.Default
                );
            }

            case "report_bug" -> {
                MysticNotificationUtil.send(
                        playerRef.getPacketHandler(),
                        "&cMysticNameTags Bug Report",
                        "&7Report issues at: &f" + BUGREPORT_URL,
                        NotificationStyle.Default
                );
            }

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
                try {
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) {
                        MysticNotificationUtil.send(
                                playerRef.getPacketHandler(),
                                "&cMysticNameTags",
                                "&7Could not open Tag UI (no Player component).",
                                NotificationStyle.Warning
                        );
                        return;
                    }

                    MysticNameTagsTagsUI tagsPage = new MysticNameTagsTagsUI(playerRef, uuid);
                    player.getPageManager().openCustomPage(ref, store, tagsPage);

                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&bMysticNameTags",
                            "&7Opened Tag Selector UI.",
                            NotificationStyle.Success
                    );
                } catch (Exception e) {
                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&cMysticNameTags",
                            "&7Failed to open Tag UI: &f" + e.getMessage(),
                            NotificationStyle.Warning
                    );
                }
            }

            case "clear_cache" -> {
                TagManager manager = TagManager.get();

                manager.clearCanUseCache(uuid);
                manager.forgetNameplate(uuid);

                MysticNotificationUtil.send(
                        playerRef.getPacketHandler(),
                        "&bMysticNameTags",
                        "&7Cleared your tag + nameplate cache.",
                        NotificationStyle.Success
                );

                UICommandBuilder update = new UICommandBuilder();
                update.set("#StatusText.Text", "Local caches cleared.");
                populateDynamicFields(update);
                sendUpdate(update, null, false);
            }

            case "refresh_nameplate" -> {
                TagManager manager = TagManager.get();
                World world = manager.getOnlineWorld(uuid);

                if (world == null) {
                    MysticNotificationUtil.send(
                            playerRef.getPacketHandler(),
                            "&cMysticNameTags",
                            "&7Cannot refresh nameplate (world not tracked).",
                            NotificationStyle.Warning
                    );
                    return;
                }

                manager.refreshNameplate(playerRef, world);

                MysticNotificationUtil.send(
                        playerRef.getPacketHandler(),
                        "&bMysticNameTags",
                        "&7Nameplate refreshed.",
                        NotificationStyle.Success
                );

                UICommandBuilder update = new UICommandBuilder();
                update.set("#StatusText.Text", "Nameplate refreshed.");
                populateDynamicFields(update);
                sendUpdate(update, null, false);
            }

            case "debug_snapshot" -> {
                TagManager manager = TagManager.get();
                IntegrationManager integrations = manager.getIntegrations();

                String coloredTag = manager.getColoredActiveTag(uuid);
                String plainTag = manager.getPlainActiveTag(uuid);

                boolean lp = integrations.isLuckPermsAvailable();
                boolean econPrimary = integrations.isPrimaryEconomyAvailable();
                boolean econVault = integrations.isVaultAvailable();
                boolean econElite = integrations.isEliteEconomyAvailable();

                MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
                String version = plugin.getResolvedVersion();

                StringBuilder sb = new StringBuilder();
                sb.append("[MysticNameTags DEBUG] Player=")
                        .append(playerRef.getUsername())
                        .append(" (").append(uuid).append(")\n")
                        .append("  Version: ").append(version).append('\n')
                        .append("  Tags Loaded: ").append(manager.getTagCount()).append('\n')
                        .append("  Active Tag (colored): ").append(coloredTag).append('\n')
                        .append("  Active Tag (plain):   ").append(plainTag).append('\n')
                        .append("  LuckPerms: ").append(lp).append('\n')
                        .append("  Economy: primary=").append(econPrimary)
                        .append(", vault=").append(econVault)
                        .append(", elite=").append(econElite)
                        .append('\n');

                LOGGER.at(Level.INFO).log(sb.toString());

                MysticNotificationUtil.send(
                        playerRef.getPacketHandler(),
                        "&bMysticNameTags",
                        "&7Debug snapshot written to console/logs.",
                        NotificationStyle.Default
                );

                UICommandBuilder update = new UICommandBuilder();
                update.set("#StatusText.Text", "Debug snapshot logged.");
                sendUpdate(update, null, false);
            }

            case "close" -> close();

            default -> close();
        }
    }

    /**
     * Populate all dynamic dashboard fields (version, integrations, tags, CPU/RAM).
     * Used on initial build and on refresh/reload.
     */
    private void populateDynamicFields(@Nonnull UICommandBuilder commands) {
        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        UpdateChecker checker = plugin.getUpdateChecker();

        String version = plugin.getResolvedVersion();
        String versionLabel = "Version: " + version;

        if (checker != null && checker.hasVersionInfo()) {
            String latest = checker.getLatestVersion();
            if (checker.isUpdateAvailable()) {
                versionLabel = "Version: " + version + "  (update available: " + latest + ")";
            } else if (checker.isCurrentAheadOfLatest()) {
                versionLabel = "Version: " + version + "  (ahead of CurseForge: " + latest + ")";
            }
        }

        commands.set("#VersionLabel.Text", versionLabel);

        // Integrations summary
        TagManager tagManager = TagManager.get();
        IntegrationManager integrations = tagManager.getIntegrations();

        boolean lp = integrations.isLuckPermsAvailable();
        boolean econPrimary = integrations.isPrimaryEconomyAvailable();
        boolean econVault = integrations.isVaultAvailable();
        boolean econElite = integrations.isEliteEconomyAvailable();

        StringBuilder integrationsText = new StringBuilder("Integrations: ");

        integrationsText.append(lp ? "LuckPerms " : "LuckPerms (none) ");
        integrationsText.append("| Economy: ");

        if (econPrimary) {
            integrationsText.append("EconomySystem ");
            if (econVault || econElite) {
                integrationsText.append("(fallback: ");
                if (econVault) integrationsText.append("VaultUnlocked ");
                if (econElite) integrationsText.append("EliteEssentials ");
                integrationsText.append(")");
            }
        } else if (econVault || econElite) {
            if (econVault) integrationsText.append("VaultUnlocked ");
            if (econElite) integrationsText.append("EliteEssentials ");
        } else {
            integrationsText.append("none");
        }

        commands.set("#IntegrationsLabel.Text", integrationsText.toString().trim());

        int tagCount = tagManager.getTagCount();
        commands.set("#TagCountLabel.Text", "Loaded Tags: " + tagCount);

        populateResourceStats(commands);
    }

    private void populateResourceStats(@Nonnull UICommandBuilder commands) {
        // RAM (JVM)
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        long max = rt.maxMemory() / (1024L * 1024L);
        String ramText = "RAM (JVM): " + used + " / " + max + " MB";
        commands.set("#RamLabel.Text", ramText);

        // CPU (JVM) – best-effort, may not be available on all JVMs
        double cpuPercent = -1.0;
        try {
            java.lang.management.OperatingSystemMXBean osBean =
                    ManagementFactory.getOperatingSystemMXBean();
            try {
                var method = osBean.getClass().getMethod("getProcessCpuLoad");
                Object value = method.invoke(osBean);
                if (value instanceof Double d && d >= 0.0) {
                    cpuPercent = d * 100.0;
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }

        String cpuText = (cpuPercent >= 0.0)
                ? String.format("CPU (JVM): %.1f%%", cpuPercent)
                : "CPU (JVM): N/A";

        commands.set("#CpuLabel.Text", cpuText);
    }

    /**
     * Toggle which tab panel is visible.
     * tabKey: "overview", "integrations", "debug"
     */
    private void applyTabSelection(@Nonnull UICommandBuilder commands,
                                   @Nonnull String tabKey) {
        boolean overview     = "overview".equalsIgnoreCase(tabKey);
        boolean integrations = "integrations".equalsIgnoreCase(tabKey);
        boolean debug        = "debug".equalsIgnoreCase(tabKey);

        // Show/hide the tab content panels
        commands.set("#TabOverviewPanel.Visible", overview);
        commands.set("#TabIntegrationsPanel.Visible", integrations);
        commands.set("#TabDebugPanel.Visible", debug);
    }

    // -------- Event data --------

    public static class UIEventData {
        private String action;

        public UIEventData() {
        }

        public String getAction() {
            return action;
        }
    }
}
