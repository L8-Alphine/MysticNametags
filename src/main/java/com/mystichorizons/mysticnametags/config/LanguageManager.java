package com.mystichorizons.mysticnametags.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public final class LanguageManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Type MAP_STRING_STRING = new TypeToken<Map<String, String>>() {}.getType();

    private static LanguageManager INSTANCE;

    private final File langRoot;

    private String activeLocale = "en_US";

    // Active locale messages
    private Map<String, String> messages = Collections.emptyMap();

    // Cached fallback messages (en_US)
    private Map<String, String> fallbackEn = Collections.emptyMap();

    public static void init() {
        INSTANCE = new LanguageManager();
        INSTANCE.reload();
    }

    public static LanguageManager get() {
        return INSTANCE;
    }

    private LanguageManager() {
        File data = MysticNameTagsPlugin.getInstance().getDataDirectory().toFile();
        this.langRoot = new File(data, "lang");
        this.langRoot.mkdirs();
    }

    /**
     * Reload active locale + ensure defaults exist + backfill missing keys.
     */
    public synchronized void reload() {
        this.activeLocale = Settings.get().getLanguage();

        // Build the authoritative default keyset (English)
        Map<String, String> defaults = buildDefaultKeyset();

        // 1) Ensure en_US exists and is up-to-date (adds missing keys, never overwrites)
        ensureMessagesUpToDate("en_US", defaults, true);

        // 2) Ensure UI overrides exist for en_US
        ensureUiOverrideExists("en_US", "mysticnametags/Dashboard.ui");
        ensureUiOverrideExists("en_US", "mysticnametags/Tags.ui");

        // 3) Load fallback cache once (now guaranteed complete)
        this.fallbackEn = loadMessagesToMap("en_US");

        // 4) Ensure active locale file exists
        ensureLanguageFileExists(activeLocale);

        // 5) Backfill missing keys for active locale from en_US defaults (never overwrites)
        //    For non-en locales, we still write English values for missing keys as a usable fallback.
        ensureMessagesUpToDate(activeLocale, defaults, false);

        // 6) Export UI overrides for active locale (so users can edit immediately)
        ensureUiOverrideExists(activeLocale, "mysticnametags/Dashboard.ui");
        ensureUiOverrideExists(activeLocale, "mysticnametags/Tags.ui");

        // 7) Load active locale
        this.messages = loadMessagesToMap(activeLocale);

        if (messages.isEmpty() && !"en_US".equalsIgnoreCase(activeLocale)) {
            LOGGER.at(Level.WARNING).log("[MysticNameTags] Locale " + activeLocale
                    + " has no messages; falling back to en_US.");
        }
    }

    public String getActiveLocale() {
        return activeLocale;
    }

    public String tr(String key) {
        if (key == null) return "";

        String v = messages.get(key);
        if (v != null) return v;

        String enV = fallbackEn.get(key);
        if (enV != null) return enV;

        return key; // show key if missing everywhere
    }

    public String tr(String key, Map<String, String> vars) {
        String base = tr(key);
        if (vars == null || vars.isEmpty()) return base;

        String out = base;
        for (var e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    /**
     * If a localized UI override exists on disk, return that path; otherwise return bundled asset path.
     *
     * bundledAssetPath example: "mysticnametags/Tags.ui"
     */
    public String resolveUi(String bundledAssetPath) {
        File localeDir = new File(langRoot, activeLocale);
        File localized = new File(new File(localeDir, "ui"), bundledAssetPath);

        if (localized.exists() && localized.isFile()) {
            return localized.getPath().replace('\\', '/');
        }

        return bundledAssetPath;
    }

    private Map<String, String> loadMessagesToMap(String locale) {
        File file = new File(new File(langRoot, locale), "messages.json");
        if (!file.exists()) return Collections.emptyMap();

        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Map<String, String> map = GSON.fromJson(r, MAP_STRING_STRING);
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load language file: " + file.getPath());
            return Collections.emptyMap();
        }
    }

    /**
     * Writes messages.json if missing, and ALWAYS backfills missing keys from defaults.
     * Never overwrites existing values.
     *
     * @param locale target locale folder, e.g. en_US
     * @param defaults authoritative keyset (English)
     * @param logChanges whether to info-log how many keys were added
     */
    private void ensureMessagesUpToDate(String locale, Map<String, String> defaults, boolean logChanges) {
        if (locale == null || locale.isBlank()) return;

        File dir = new File(langRoot, locale);
        dir.mkdirs();

        File file = new File(dir, "messages.json");

        // Load existing (if any)
        Map<String, String> current = new LinkedHashMap<>();
        if (file.exists()) {
            current.putAll(loadMessagesToMap(locale));
        } else {
            // Seed with a hint for non-en locales
            if (!"en_US".equalsIgnoreCase(locale)) {
                current.put("_comment", "Copy keys from en_US/messages.json and translate values.");
            }
        }

        // Backfill missing keys (preserve existing translations/customizations)
        int added = 0;
        for (Map.Entry<String, String> e : defaults.entrySet()) {
            String k = e.getKey();
            if (!current.containsKey(k)) {
                current.put(k, e.getValue());
                added++;
            }
        }

        // Write if file missing OR we added anything
        if (!file.exists() || added > 0) {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(current, w);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e)
                        .log("[MysticNameTags] Failed to write language file: " + file.getPath());
                return;
            }

            if (logChanges && added > 0) {
                LOGGER.at(Level.INFO).log("[MysticNameTags] Updated " + locale + "/messages.json (added " + added + " missing keys).");
            }
        }
    }

    /**
     * If a locale is selected but no messages.json exists, create a minimal one.
     * (Kept for clarity; ensureMessagesUpToDate will also create if missing.)
     */
    private void ensureLanguageFileExists(String locale) {
        if (locale == null || locale.isBlank()) return;

        File dir = new File(langRoot, locale);
        dir.mkdirs();

        File messages = new File(dir, "messages.json");
        if (messages.exists()) return;

        try (Writer w = new OutputStreamWriter(new FileOutputStream(messages), StandardCharsets.UTF_8)) {
            Map<String, String> hint = new LinkedHashMap<>();
            hint.put("_comment", "Copy keys from en_US/messages.json and translate values.");
            GSON.toJson(hint, w);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to create " + locale + "/messages.json");
        }
    }

    /**
     * Ensure a UI override exists at:
     *   lang/<locale>/ui/<bundledAssetPath>
     * If missing, copy from jar resources.
     */
    private void ensureUiOverrideExists(String locale, String bundledAssetPath) {
        if (locale == null || locale.isBlank()) return;
        if (bundledAssetPath == null || bundledAssetPath.isBlank()) return;

        File localeDir = new File(langRoot, locale);
        File uiRoot = new File(localeDir, "ui");
        File target = new File(uiRoot, bundledAssetPath);

        if (target.exists() && target.isFile()) return;

        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();

        try (InputStream in = MysticNameTagsPlugin.getInstance()
                .getClass()
                .getClassLoader()
                .getResourceAsStream(bundledAssetPath)) {

            if (in == null) {
                LOGGER.at(Level.WARNING).log("[MysticNameTags] Could not export UI override; resource not found: "
                        + bundledAssetPath);
                return;
            }

            try (OutputStream out = new FileOutputStream(target)) {
                in.transferTo(out);
            }

            LOGGER.at(Level.INFO).log("[MysticNameTags] Exported UI override: "
                    + target.getPath().replace('\\', '/'));

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to export UI override for " + bundledAssetPath
                            + " to locale " + locale);
        }
    }

    /**
     * Single source of truth for ALL shipped keys.
     * Add new keys here and they will be auto-added to existing installs on next reload.
     */
    private Map<String, String> buildDefaultKeyset() {
        Map<String, String> defaults = new LinkedHashMap<>();

        defaults.put("plugin.title", "MysticNameTags");
        defaults.put("tags.unknown_result", "Unknown result: {result}");

        // ---------------- UI STRING KEYS (seen in .ui files) ----------------
        // TAGS UI

        defaults.put("ui.tags.balance_cash", "Cash: {amount}");
        defaults.put("ui.tags.balance_coins", "Balance: {amount}");
        defaults.put("ui.tags.balance_na", "Balance: N/A");

        defaults.put("ui.tags.search_filter_prefix", "Search tags... ({filter})");
        defaults.put("ui.tags.page_none_defined", "No tags are defined.");
        defaults.put("ui.tags.page_none_for_filter", "No tags found for \"{filter}\".");
        defaults.put("ui.tags.page_filter_suffix", "(filter: {filter})");

        defaults.put("ui.tags.search_placeholder", "Search tags...");
        defaults.put("ui.tags.category_all", "All");
        defaults.put("ui.tags.page_label", "Page {page}/{pages}");

        defaults.put("ui.tags.price_cash", "{price} Cash");
        defaults.put("ui.tags.price_coins", "{price} Coins");
        defaults.put("ui.tags.price_free", "Free");
        defaults.put("ui.tags.price_economy_disabled", "{price} (economy disabled)");

        defaults.put("ui.tags.req_purchase_title", "Purchase required");
        defaults.put("ui.tags.req_purchase_value_cash", "{price} Cash");
        defaults.put("ui.tags.req_purchase_value_coins", "{price} Coins");

        defaults.put("ui.tags.button_no_access", "No Access");
        defaults.put("ui.tags.button_unequip", "Unequip");
        defaults.put("ui.tags.button_unlock", "Unlock");
        defaults.put("ui.tags.button_equip", "Equip");
        defaults.put("ui.tags.button_buy", "Buy");
        defaults.put("ui.tags.info_icon", "...");

        defaults.put("ui.tags.req_permission_title", "Requirements");
        defaults.put("ui.tags.req_playtime_title", "Playtime Required");
        defaults.put("ui.tags.req_playtime_value", "{minutes} minutes");
        defaults.put("ui.tags.req_owned_title", "Owned Tags Required");
        defaults.put("ui.tags.req_purchase_missing_econ_suffix", "(economy disabled)");

        defaults.put("ui.tags.status_locked_requirements", "Status: LOCKED (requirements not met)");
        defaults.put("ui.tags.status_available", "Status: AVAILABLE");
        defaults.put("ui.tags.status_locked_not_purchased", "Status: LOCKED (not purchased)");

        // ---------------- DASHBOARD UI (status bar / info boxes) ----------------

        // Version label and variants (used in populateDynamicFields)
        defaults.put("dashboard.version_label", "Version: {version}");
        defaults.put("dashboard.version_label_update",
                "Version: {version} (update available: {latest})");
        defaults.put("dashboard.version_label_ahead",
                "Version: {version} (ahead of CurseForge: {latest})");

        // Integrations summary line
        defaults.put("dashboard.integrations_prefix", "Integrations:");
        defaults.put("dashboard.integration_luckperms", "LuckPerms");
        defaults.put("dashboard.integration_luckperms_none", "LuckPerms (none)");
        defaults.put("dashboard.integrations_plus_permissionsplus", "+ PermissionsPlus");

        defaults.put("dashboard.integrations_economy_prefix", "| Economy:");
        defaults.put("dashboard.economy_primary", "EconomySystem (primary)");
        defaults.put("dashboard.economy_fallback_prefix", "(fallback: ");
        defaults.put("dashboard.economy_none", "none");

        // Loaded tags label (used with {count})
        defaults.put("dashboard.loaded_tags_label", "Loaded Tags: {count}");

        // Storage Keys
        defaults.put("dashboard.storage_file",
                "Storage: File (playerdata/*.json)");
        defaults.put("dashboard.storage_sqlite",
                "Storage: SQLite ({file})");
        defaults.put("dashboard.storage_mysql",
                "Storage: MySQL {host}:{port}/{database}");

        // Placeholders info line
        defaults.put("dashboard.placeholders_prefix", "Placeholders:");
        defaults.put("dashboard.placeholders_none", "none");

        // Resource stats (used in populateResourceStats)
        defaults.put("dashboard.cpu_label",
                "CPU (process): {percent}% ({cores} cores)");
        defaults.put("dashboard.cpu_na",
                "CPU (process): N/A");

        defaults.put("dashboard.ram_label",
                "RAM (JVM): {used} / {max} MB");

        defaults.put("dashboard.uptime_label",
                "Uptime: {uptime}");

        // ---------------- DASHBOARD NOTIFICATIONS / TOASTS ----------------

        // Initial status text in the dashboard header
        defaults.put("dashboard.welcome", "Welcome to MysticNameTags!");

        // Update checker notifications
        defaults.put("dashboard.update_title", "Update");
        defaults.put("dashboard.update_available",
                "A new version ({latest}) is available. You are running {current}. Visit {url} for details.");

        defaults.put("dashboard.devbuild_title", "Dev build");
        defaults.put("dashboard.devbuild_ahead",
                "You are running dev build {current}, which is ahead of CurseForge ({latest}).");

        // Refresh button
        defaults.put("dashboard.refreshed_toast",
                "Dashboard data refreshed.");
        defaults.put("dashboard.refreshed_status",
                "Status: dashboard refreshed.");

        // Reload button
        defaults.put("dashboard.reloaded_toast",
                "Configuration and tags reloaded from disk.");
        defaults.put("dashboard.reloaded_status",
                "Status: configuration reloaded from disk.");

        // Mod page button
        defaults.put("dashboard.modpage_title_suffix", "mod page");
        defaults.put("dashboard.modpage_open",
                "Open the mod page in your browser: {url}");

        // Bug report button
        defaults.put("dashboard.bugreport_title_suffix", "bug report");
        defaults.put("dashboard.bugreport_open",
                "Open the issue tracker: {url}");

        // Open Tags quick action
        defaults.put("dashboard.open_tags_failed",
                "Could not open the tag UI; player component was missing.");
        defaults.put("dashboard.opened_tags_toast",
                "Opened the tag selector UI.");

        // Clear cache quick action
        defaults.put("dashboard.cleared_cache_toast",
                "Local tag and nameplate cache cleared.");
        defaults.put("dashboard.cleared_cache_status",
                "Status: local caches cleared.");

        // Refresh nameplate quick action
        defaults.put("dashboard.refresh_nameplate_world_missing",
                "You must be in a world to refresh your nameplate.");
        defaults.put("dashboard.refresh_nameplate_toast",
                "Nameplate refresh requested.");
        defaults.put("dashboard.refresh_nameplate_status",
                "Status: nameplate refresh requested.");

        // Debug snapshot
        defaults.put("dashboard.debug_snapshot_toast",
                "Debug snapshot printed to console/logs.");
        defaults.put("dashboard.debug_snapshot_status",
                "Status: debug snapshot generated.");

        // Generic internal error
        defaults.put("dashboard.internal_error_toast",
                "An internal error occurred; see server console for details.");

        // ---------------- CHAT/NOTIFICATION STRINGS (used by other code) ----------------

        // Tags purchase results / notifications
        defaults.put("tags.not_found", "That tag no longer exists.");
        defaults.put("tags.no_permission", "You don't have access to that tag.");
        defaults.put("tags.unlocked_free", "Unlocked {tag} and equipped!");
        defaults.put("tags.unlocked_paid", "Purchased {tag} and equipped!");
        defaults.put("tags.equipped", "Equipped {tag}.");
        defaults.put("tags.unequipped", "Unequipped {tag}.");
        defaults.put("tags.no_economy", "Economy plugin is not configured.");
        defaults.put("tags.not_enough_money", "You cannot afford that tag.");
        defaults.put("tags.transaction_failed", "Transaction failed. Please try again.");

        // /tags help
        defaults.put("cmd.help.header", "&b=== MysticNameTags Commands ===");
        defaults.put("cmd.help.line.help", "&7/tags help &8- &fShow this help message");
        defaults.put("cmd.help.line.info", "&7/tags info &8- &fShow plugin information");
        defaults.put("cmd.help.line.reload", "&7/tags reload &8- &fReload configuration");
        defaults.put("cmd.help.line.ui", "&7/tags ui &8- &fOpen the dashboard UI");
        defaults.put("cmd.help.line.tags", "&7/tags tags &8- &fOpen the tag selection UI");
        defaults.put("cmd.help.footer", "&b========================");

        // /tags reload
        defaults.put("cmd.reload.no_permission", "&cYou do not have permission to use this command.");
        defaults.put("cmd.reload.not_loaded", "&cMysticNameTags is not loaded.");
        defaults.put("cmd.reload.start", "&7[&bMysticNameTags&7] &fReloading configuration and tags...");
        defaults.put("cmd.reload.success", "&7[&bMysticNameTags&7] &aReload complete!");
        defaults.put("cmd.reload.failed", "&7[&bMysticNameTags&7] &cReload failed. Check console.");

        // /tags ui
        defaults.put("cmd.ui.no_permission", "&cYou do not have permission to use this command.");
        defaults.put("cmd.ui.not_loaded", "&cMysticNameTags is not loaded.");
        defaults.put("cmd.ui.opening", "&7[&bMysticNameTags&7] &fOpening &bDashboard&f...");
        defaults.put("cmd.ui.opened", "&aDashboard opened. &fPress &7ESC&f to close.");
        defaults.put("cmd.ui.no_player_component", "&cError: &7Could not get Player component.");
        defaults.put("cmd.ui.open_error", "&cError opening dashboard: &7{error}");

        // /tags (tags UI open)
        defaults.put("cmd.tags.no_player_component", "&cError: &7Could not get Player component.");
        defaults.put("cmd.tags.no_account_id", "&cError: &7Could not determine your account id.");
        defaults.put("cmd.tags.opening", "&7[&bMysticNameTags&7] &fOpening &bTag Selector&f...");
        defaults.put("cmd.tags.open_error", "&cError opening tag selector: &7{error}");

        // /tags info
        defaults.put("cmd.info.not_loaded", "&cMysticNameTags plugin instance not available.");
        defaults.put("cmd.info.separator", "&7&m------------------------------");
        defaults.put("cmd.info.title", "&b{name} &7Plugin Info");
        defaults.put("cmd.info.name", "&7Name: &f{name}");
        defaults.put("cmd.info.version", "&7Version: &f{version}");
        defaults.put("cmd.info.author", "&7Author: &f{author}");
        defaults.put("cmd.info.status_running", "&7Status: &aRunning");

        defaults.put("cmd.info.integrations_prefix", "&7Integrations: ");
        defaults.put("cmd.info.luckperms_yes", "&aLuckPerms");
        defaults.put("cmd.info.luckperms_no", "&cLuckPerms (none)");
        defaults.put("cmd.info.permissionsplus_yes", "&7 + &bPermissionsPlus");

        defaults.put("cmd.info.economy_prefix", "&7 | Economy: ");
        defaults.put("cmd.info.economy_primary", "&aEconomySystem");
        defaults.put("cmd.info.economy_vault", "&bVaultUnlocked");
        defaults.put("cmd.info.economy_elite", "&dEliteEssentials");
        defaults.put("cmd.info.economy_none", "&cnone");
        defaults.put("cmd.info.economy_plus_sep", " &7+ ");
        defaults.put("cmd.info.economy_fallback_prefix", "&7 (fallback: ");
        defaults.put("cmd.info.economy_fallback_sep", "&7, ");
        defaults.put("cmd.info.economy_fallback_suffix", "&7)");

        defaults.put("cmd.info.update_unknown", "&7Update status: &8Unknown");
        defaults.put("cmd.info.update_ok", "&7Update status: &aUp to date (&b{current}&a)");
        defaults.put("cmd.info.update_available", "&7Update status: &eUpdate available &7→ &b{latest}");
        defaults.put("cmd.info.update_ahead", "&7Update status: &aRunning {current} &7(ahead of CurseForge: &b{latest}&7)");

        // Admin shared + specific
        defaults.put("cmd.admin.no_permission", "&cYou do not have permission to use &f{usage}&c.");
        defaults.put("cmd.admin.target_not_found", "&cCould not resolve target player.");

        defaults.put("cmd.admin.reset.none_to_reset", "&ePlayer &f{player}&e has no stored tags to reset.");
        defaults.put("cmd.admin.reset.success", "&aReset all tags for &b{player}&a.");
        defaults.put("cmd.admin.reset.success_with_perms", "&aReset all tags & revoked tag permissions for &b{player}&a.");

        defaults.put("cmd.admin.removetag.usage", "&cUsage: &f/tagsadmin removetag <player> <tagId>");
        defaults.put("cmd.admin.removetag.not_owned", "&cPlayer &f{player}&c does not have tag '&f{tagId}&c'.");
        defaults.put("cmd.admin.removetag.success", "&aRemoved tag '&f{tagId}&a' from &b{player}&a.");

        defaults.put("cmd.admin.open.not_in_world", "&cThat player is not currently in a world.");
        defaults.put("cmd.admin.open.opening", "&7[&bMysticNameTags&7] &fOpening &bTag Selector&f for &b{player}&f...");
        defaults.put("cmd.admin.open.no_player_component", "&cError: &7Could not get Player component for '&f{player}&7'.");
        defaults.put("cmd.admin.open.error", "&cError opening tag selector for '&f{player}&c': &7{error}");

        defaults.put("cmd.admin.givetag.usage", "&cUsage: &f/tagsadmin givetag <player> <tagId>");
        defaults.put("cmd.admin.givetag.unknown_tag", "&cUnknown tag id '&f{tagId}&c'.");
        defaults.put("cmd.admin.givetag.success", "&aGave tag '&f{tagId}&a' to &b{player}&a (equipped).");

        return defaults;
    }
}