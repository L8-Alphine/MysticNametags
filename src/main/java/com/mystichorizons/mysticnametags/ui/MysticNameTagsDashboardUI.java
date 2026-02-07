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
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.MysticLog;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;
import com.mystichorizons.mysticnametags.util.UpdateChecker;

import javax.annotation.Nonnull;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
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

        // Sidebar/tab buttons
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
        String action = data.action; // capture for logging

        try {
            switch (action) {

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
                            // log only because this is unexpected
                            MysticLog.warn("Dashboard open_tag_ui failed for "
                                    + playerRef.getUsername() + " – no Player component.");
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
                        // this is an actual “crash” of this action → log it
                        MysticLog.error("Dashboard open_tag_ui threw exception for "
                                + playerRef.getUsername(), e);
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
                        // optional: this is more of a state issue than a crash, so we can skip logging
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
                    String plainTag   = manager.getPlainActiveTag(uuid);

                    boolean lp          = integrations.isLuckPermsAvailable();
                    boolean permsPlus   = integrations.isPermissionsPlusActive();
                    boolean econPrimary = integrations.isPrimaryEconomyAvailable();
                    boolean econEcoTale = integrations.isEcoTaleAvailable();
                    boolean econVault   = integrations.isVaultAvailable();
                    boolean econElite   = integrations.isEliteEconomyAvailable();
                    // Placeholder integrations
                    Settings settings = Settings.get();
                    boolean wiFlowPlaceholders   = settings.isWiFlowPlaceholdersEnabled();
                    boolean helpchPlaceholders   = settings.isHelpchPlaceholderApiEnabled();

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
                            .append("  PermissionsPlus active: ").append(permsPlus).append('\n')
                            .append("  Economy: primary=").append(econPrimary)
                            .append(", ecoTale=").append(econEcoTale)
                            .append(", vault=").append(econVault)
                            .append(", elite=").append(econElite)
                            .append('\n');

                    // THIS is where we log when the admin hits Debug
                    MysticLog.debug(sb.toString());

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

        } catch (Throwable t) {
            // Failsafe: ANY unhandled exception in this handler gets logged once
            MysticLog.error("Unhandled exception in MysticNameTagsDashboardUI.handleDataEvent for "
                    + playerRef.getUsername() + " action=" + action, t);

            MysticNotificationUtil.send(
                    playerRef.getPacketHandler(),
                    "&cMysticNameTags",
                    "&7An internal error occurred while handling this action. " +
                            "Admins can check the MysticNameTags logs for details.",
                    NotificationStyle.Warning
            );
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

        boolean lp          = integrations.isLuckPermsAvailable();
        boolean permsPlus   = integrations.isPermissionsPlusActive();
        boolean econPrimary = integrations.isPrimaryEconomyAvailable();
        boolean econEcoTale = integrations.isEcoTaleAvailable();
        boolean econVault   = integrations.isVaultAvailable();
        boolean econElite   = integrations.isEliteEconomyAvailable();
        // Placeholder integrations
        Settings settings = Settings.get();
        boolean wiFlowPlaceholders   = settings.isWiFlowPlaceholdersEnabled();
        boolean helpchPlaceholders   = settings.isHelpchPlaceholderApiEnabled();

        StringBuilder integrationsText = new StringBuilder("Integrations: ");

        // Permissions stack
        if (lp) {
            integrationsText.append("LuckPerms");
        } else {
            integrationsText.append("LuckPerms (none)");
        }

        if (permsPlus) {
            integrationsText.append(" + PermissionsPlus");
        }

        integrationsText.append(" | Economy: ");

        if (econPrimary) {
            integrationsText.append("EconomySystem");
            if (econEcoTale || econVault || econElite) {
                integrationsText.append(" (fallback: ");
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
            integrationsText.append("none");
        }

        commands.set("#IntegrationsLabel.Text", integrationsText.toString().trim());

        int tagCount = tagManager.getTagCount();
        commands.set("#TagCountLabel.Text", "Loaded Tags: " + tagCount);

        // Placeholder integrations label in the Integrations tab
        StringBuilder placeholderText = new StringBuilder("Placeholders: ");

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
            placeholderText.append("none detected");
        }

        commands.set("#PlaceholderBackendsLabel.Text", placeholderText.toString());

        populateResourceStats(commands);
    }

    /**
     * Populates the CPU/RAM labels in the dashboard.
     * Note: these are *process/JVM-wide* stats (the server including MysticNameTags),
     * not per-plugin metrics – the JVM doesn't expose accurate per-plugin usage.
     */
    private void populateResourceStats(@Nonnull UICommandBuilder commands) {
        ResourceSnapshot rs = captureResourceSnapshot();

        String ramText = "RAM (JVM, includes MysticNameTags): "
                + rs.usedMb + " / " + rs.maxMb + " MB";
        commands.set("#RamLabel.Text", ramText);

        String cpuText = (rs.cpuPercent >= 0.0)
                ? String.format("CPU (process): %.1f%% of %d cores",
                rs.cpuPercent, rs.availableProcessors)
                : "CPU (process): N/A";

        commands.set("#CpuLabel.Text", cpuText);
    }

    /**
     * Toggle which tab panel is visible and which sidebar tab is "selected".
     * tabKey: "overview", "integrations", "debug"
     */
    private void applyTabSelection(@Nonnull UICommandBuilder commands,
                                   @Nonnull String tabKey) {
        boolean overview     = "overview".equalsIgnoreCase(tabKey);
        boolean integrations = "integrations".equalsIgnoreCase(tabKey);
        boolean debug        = "debug".equalsIgnoreCase(tabKey);

        // Tab content panels
        commands.set("#TabOverviewPanel.Visible", overview);
        commands.set("#TabIntegrationsPanel.Visible", integrations);
        commands.set("#TabDebugPanel.Visible", debug);

        // Sidebar buttons: show selected vs normal
        commands.set("#OverviewTabButtonSelected.Visible", overview);
        commands.set("#OverviewTabButton.Visible", !overview);

        commands.set("#IntegrationsTabButtonSelected.Visible", integrations);
        commands.set("#IntegrationsTabButton.Visible", !integrations);

        commands.set("#DebugTabButtonSelected.Visible", debug);
        commands.set("#DebugTabButton.Visible", !debug);
    }

    // -------- Resource snapshot helpers --------

    private static ResourceSnapshot captureResourceSnapshot() {
        ResourceSnapshot rs = new ResourceSnapshot();

        // Heap
        Runtime rt = Runtime.getRuntime();
        rs.usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        rs.maxMb = rt.maxMemory() / (1024L * 1024L);

        // CPU (process) – best-effort, may not be available on all JVMs
        double cpu = -1.0;
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            try {
                var method = osBean.getClass().getMethod("getProcessCpuLoad");
                Object value = method.invoke(osBean);
                if (value instanceof Double d && d >= 0.0) {
                    cpu = d * 100.0;
                }
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
