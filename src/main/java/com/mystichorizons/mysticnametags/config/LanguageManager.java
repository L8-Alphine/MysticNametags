package com.mystichorizons.mysticnametags.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import org.jetbrains.annotations.Nullable;

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
    private static final Type MAP_STRING_OBJECT = new TypeToken<Map<String, Object>>() {}.getType();

    private static LanguageManager INSTANCE;

    private final File langRoot;

    private String activeLocale = "en_US";
    private Map<String, String> messages = Collections.emptyMap();
    private Map<String, String> fallbackEn = Collections.emptyMap();
    private Map<String, Object> howItWorksPanel = Collections.emptyMap();

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

    public synchronized void reload() {
        this.activeLocale = Settings.get().getLanguage();

        Map<String, String> defaults = buildDefaultKeyset();

        ensureMessagesUpToDate("en_US", defaults, true);
        this.fallbackEn = loadMessagesToMap("en_US");

        ensureLanguageFileExists(activeLocale);
        ensureMessagesUpToDate(activeLocale, defaults, false);

        ensureHowItWorksPanelExists("en_US");
        ensureHowItWorksPanelExists(activeLocale);
        this.howItWorksPanel = loadHowItWorksPanel(activeLocale);

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
        if (v != null) return ColorFormatter.colorize(v);

        String enV = fallbackEn.get(key);
        if (enV != null) return ColorFormatter.colorize(enV);

        return ColorFormatter.colorize(key);
    }

    public String tr(String key, Map<String, String> vars) {
        String base = tr(key);
        if (vars == null || vars.isEmpty()) return base;

        String out = base;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }

        return ColorFormatter.colorize(out);
    }

    public String trUi(String key) {
        return ColorFormatter.stripFormatting(trRaw(key));
    }

    public String trUi(String key, Map<String, String> vars) {
        return ColorFormatter.stripFormatting(trRaw(key, vars));
    }

    public String trRaw(String key) {
        if (key == null) return "";

        String v = messages.get(key);
        if (v != null) return v;

        String enV = fallbackEn.get(key);
        if (enV != null) return enV;

        return key;
    }

    public String trRaw(String key, Map<String, String> vars) {
        String base = trRaw(key);
        if (vars == null || vars.isEmpty()) return base;

        String out = base;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
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

    private void ensureMessagesUpToDate(String locale, Map<String, String> defaults, boolean logChanges) {
        if (locale == null || locale.isBlank()) return;

        File dir = new File(langRoot, locale);
        dir.mkdirs();

        File file = new File(dir, "messages.json");

        Map<String, String> current = new LinkedHashMap<>();
        if (file.exists()) {
            current.putAll(loadMessagesToMap(locale));
        } else if (!"en_US".equalsIgnoreCase(locale)) {
            current.put("_comment", "Copy keys from en_US/messages.json and translate values.");
        }

        int added = 0;
        for (Map.Entry<String, String> e : defaults.entrySet()) {
            if (!current.containsKey(e.getKey())) {
                current.put(e.getKey(), e.getValue());
                added++;
            }
        }

        if (!file.exists() || added > 0) {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(current, w);
            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e)
                        .log("[MysticNameTags] Failed to write language file: " + file.getPath());
                return;
            }

            if (logChanges && added > 0) {
                LOGGER.at(Level.INFO)
                        .log("[MysticNameTags] Updated " + locale + "/messages.json (added " + added + " missing keys).");
            }
        }
    }

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

    @SuppressWarnings("unchecked")
    public List<String> getHowItWorksPanelLines() {
        Object root = howItWorksPanel.get("howitworks");
        if (!(root instanceof Map<?, ?> map)) {
            return Collections.emptyList();
        }

        Object content = map.get("content");
        if (!(content instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o == null) continue;
            String s = String.valueOf(o).trim();
            out.add(ColorFormatter.stripFormatting(s));
        }
        return out;
    }

    public String getHowItWorksPanelTitle() {
        Object root = howItWorksPanel.get("howitworks");
        if (!(root instanceof Map<?, ?> map)) {
            return ColorFormatter.stripFormatting(tr("ui.tags.howitworks_title"));
        }

        Object title = map.get("title");
        if (title == null) {
            return ColorFormatter.stripFormatting(tr("ui.tags.howitworks_title"));
        }

        return ColorFormatter.stripFormatting(String.valueOf(title));
    }

    private Map<String, Object> loadHowItWorksPanel(String locale) {
        File file = new File(new File(langRoot, locale), "howitworkspanel.json");
        if (!file.exists()) return Collections.emptyMap();

        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Map<String, Object> map = GSON.fromJson(r, MAP_STRING_OBJECT);
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load howitworkspanel.json for locale " + locale);
            return Collections.emptyMap();
        }
    }

    private void ensureHowItWorksPanelExists(String locale) {
        if (locale == null || locale.isBlank()) return;

        File dir = new File(langRoot, locale);
        dir.mkdirs();

        File file = new File(dir, "howitworkspanel.json");
        if (file.exists()) return;

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "How It Works");
        body.put("content", List.of(
                "Select a tag from the list to inspect its preview, status, price, and requirements.",
                "The preview section shows how the selected tag will appear when active.",
                "The progress bar reflects how much of the tag unlock path is complete."
        ));
        root.put("howitworks", body);

        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to create " + locale + "/howitworkspanel.json");
        }
    }

    private Map<String, String> buildDefaultKeyset() {
        Map<String, String> defaults = new LinkedHashMap<>();

        defaults.put("plugin.title", "MysticNameTags");
        defaults.put("tags.unknown_result", "Unknown result: {result}");

        // Core tags UI
        defaults.put("ui.tags.title", "TAG SELECTION");
        defaults.put("ui.tags.player_section", "PLAYER");
        defaults.put("ui.tags.filters_section", "FILTERS");
        defaults.put("ui.tags.list_section", "AVAILABLE TAGS");
        defaults.put("ui.tags.details_section", "DETAILS");
        defaults.put("ui.tags.progress_section", "PROGRESS");
        defaults.put("ui.tags.requirements_title", "REQUIREMENTS");
        defaults.put("ui.tags.howitworks_title", "HOW IT WORKS");
        defaults.put("ui.tags.footer_close_hint", "Press ESC to close");
        defaults.put("ui.tags.current_tag_none", "None");

        defaults.put("ui.tags.label_player", "Player:");
        defaults.put("ui.tags.label_balance", "Balance:");
        defaults.put("ui.tags.label_current_tag", "Current Tag:");
        defaults.put("ui.tags.label_category", "Category:");
        defaults.put("ui.tags.label_price", "Price");
        defaults.put("ui.tags.label_status", "Status");
        defaults.put("ui.tags.label_preview", "Preview");

        defaults.put("ui.tags.balance_cash", "Cash: {amount}");
        defaults.put("ui.tags.balance_coins", "Balance: {amount}");
        defaults.put("ui.tags.balance_na", "Balance: N/A");

        defaults.put("ui.tags.search_filter_prefix", "Search tags... ({filter})");
        defaults.put("ui.tags.search_placeholder", "Search tags...");
        defaults.put("ui.tags.page_none_defined", "No tags are defined.");
        defaults.put("ui.tags.page_none_for_filter", "No tags found for \"{filter}\".");
        defaults.put("ui.tags.page_filter_suffix", "(filter: {filter})");
        defaults.put("ui.tags.page_label", "Page {page}/{pages}");

        defaults.put("ui.tags.category_all", "All");
        defaults.put("ui.tags.category_none", "None");

        defaults.put("ui.tags.price_cash", "{price} Cash");
        defaults.put("ui.tags.price_coins", "{price} Coins");
        defaults.put("ui.tags.price_free", "Free");
        defaults.put("ui.tags.price_economy_disabled", "{price} (economy disabled)");

        defaults.put("ui.tags.req_purchase_title", "Purchase required");
        defaults.put("ui.tags.req_purchase_value_cash", "{price} Cash");
        defaults.put("ui.tags.req_purchase_value_coins", "{price} Coins");
        defaults.put("ui.tags.req_purchase_missing_econ_suffix", "(economy disabled)");

        defaults.put("ui.tags.button_no_access", "No Access");
        defaults.put("ui.tags.button_unequip", "Unequip");
        defaults.put("ui.tags.button_unlock", "Unlock");
        defaults.put("ui.tags.button_equip", "Equip");
        defaults.put("ui.tags.button_buy", "Buy");
        defaults.put("ui.tags.button_help", "?");

        defaults.put("ui.tags.badge_active", "ACTIVE");
        defaults.put("ui.tags.badge_locked", "LOCKED");
        defaults.put("ui.tags.badge_owned", "OWNED");
        defaults.put("ui.tags.badge_buy", "BUY");
        defaults.put("ui.tags.badge_free", "FREE");

        defaults.put("ui.tags.req_permission_title", "Permission");
        defaults.put("ui.tags.req_permission_gate_full", "Required to access this tag.");
        defaults.put("ui.tags.req_permission_gate_soft", "Required to unlock/equip this tag.");
        defaults.put("ui.tags.req_playtime_title", "Playtime Required");
        defaults.put("ui.tags.req_playtime_value", "{minutes} minutes");
        defaults.put("ui.tags.req_playtime_progress", "Playtime: {current} / {required} minutes");
        defaults.put("ui.tags.req_owned_title", "Owned Tags Required");
        defaults.put("ui.tags.req_owned_missing", "Own tag: {tag}");
        defaults.put("ui.tags.req_placeholders_title", "Placeholder Requirements");
        defaults.put("ui.tags.req_item_line", "Item: {amount}x {item}");
        defaults.put("ui.tags.req_placeholder_line", "{placeholder} {operator} {value}");
        defaults.put("ui.tags.req_stat_progress", "{stat}: {current} / {required}");

        defaults.put("ui.tags.status_locked_requirements", "Status: LOCKED (requirements not met)");
        defaults.put("ui.tags.status_locked_not_purchased", "Status: LOCKED (not purchased)");
        defaults.put("ui.tags.status_available", "Status: AVAILABLE");

        defaults.put("ui.tags.detail_status_active", "Active");
        defaults.put("ui.tags.detail_status_owned", "Owned");
        defaults.put("ui.tags.detail_status_purchasable", "Purchasable");
        defaults.put("ui.tags.detail_status_free", "Free");

        defaults.put("ui.tags.progress_percent", "{percent}% complete");
        defaults.put("ui.tags.progress_ready", "Ready to equip");
        defaults.put("ui.tags.button_wait", "Please Wait");

        defaults.put("ui.tags.howitworks.inspect", "Select a tag from the list to inspect its preview, status, price, and requirements.");
        defaults.put("ui.tags.howitworks.preview", "The preview section shows how the selected tag will appear when active.");
        defaults.put("ui.tags.howitworks.progress", "The progress bar reflects how much of the tag unlock path is complete.");
        defaults.put("ui.tags.howitworks.active", "This tag is currently active.");
        defaults.put("ui.tags.howitworks.unequip", "Press the button below to unequip it.");
        defaults.put("ui.tags.howitworks.owned", "You already own this tag.");
        defaults.put("ui.tags.howitworks.equip", "Press the button below to equip it.");
        defaults.put("ui.tags.howitworks.purchase_required", "This tag must be purchased before it can be equipped.");
        defaults.put("ui.tags.howitworks.purchase_requirements", "If requirements are also listed, those must be satisfied too.");
        defaults.put("ui.tags.howitworks.free_unlock", "This tag does not require a purchase.");
        defaults.put("ui.tags.howitworks.unlock_when_met", "Once all requirements are satisfied, it can be unlocked and equipped.");
        defaults.put("ui.tags.howitworks.check_requirements", "Check the requirements panel to see what is still missing.");
        defaults.put("ui.tags.howitworks.requirements_met", "All currently checked requirements are satisfied.");
        defaults.put("ui.tags.howitworks.category_line", "Category: {category}");
        defaults.put("ui.tags.howitworks.description_line", "Description: {description}");

        defaults.put("ui.common.apply", "APPLY");
        defaults.put("ui.common.clear", "CLEAR");
        defaults.put("ui.common.prev", "PREV");
        defaults.put("ui.common.next", "NEXT");
        defaults.put("ui.common.close", "CLOSE");

        defaults.put("ui.stats.custom.kills_total", "Total Kills");
        defaults.put("ui.stats.custom.deaths_total", "Total Deaths");
        defaults.put("ui.stats.custom.blocks_broken_total", "Blocks Broken");
        defaults.put("ui.stats.custom.blocks_placed_total", "Blocks Placed");
        defaults.put("ui.stats.custom.damage_dealt", "Damage Dealt");
        defaults.put("ui.stats.custom.damage_taken", "Damage Taken");
        defaults.put("ui.stats.endlessleveling.level", "Endless Level");
        defaults.put("ui.stats.endlessleveling.xp", "Endless XP");
        defaults.put("ui.stats.endlessleveling.skill_prefix", "Skill Level: {name}");
        defaults.put("ui.stats.prefix.kills", "Kills: {name}");
        defaults.put("ui.stats.prefix.mined", "Blocks Mined: {name}");
        defaults.put("ui.stats.prefix.placed", "Blocks Placed: {name}");

        // ---------------- OWNED TAGS UI ----------------
        defaults.put("ui.owned.title", "OWNED TAGS");
        defaults.put("ui.owned.subtitle", "View and equip tags you already own.");
        defaults.put("ui.owned.current_prefix", "Current:");
        defaults.put("ui.owned.section_title", "YOUR TAGS");
        defaults.put("ui.owned.header_tag", "Tag");
        defaults.put("ui.owned.header_action", "Action");
        defaults.put("ui.owned.footer_hint", "Click a tag to equip or unequip it.");
        defaults.put("ui.owned.search_placeholder", "Search owned tags...");
        defaults.put("ui.owned.search_filter_prefix", "Search owned tags... ({filter})");
        defaults.put("ui.owned.none", "You do not own any tags yet.");
        defaults.put("ui.owned.none_for_filter", "No owned tags found for \"{filter}\".");

        // ---------------- DASHBOARD UI ----------------
        defaults.put("ui.dashboard.sidebar_title", "MysticNameTags");
        defaults.put("ui.dashboard.sidebar_subtitle", "Dashboard");
        defaults.put("ui.dashboard.sidebar_sections", "Sections");
        defaults.put("ui.dashboard.sidebar_footer", "Use /mntag to open");
        defaults.put("ui.dashboard.title", "MysticNameTags Dashboard");
        defaults.put("ui.dashboard.close_hint", "Press ESC to close");

        defaults.put("ui.dashboard.tab_overview", "Overview");
        defaults.put("ui.dashboard.tab_integrations", "Integrations");
        defaults.put("ui.dashboard.tab_debug", "Debug");
        defaults.put("ui.dashboard.tab_support", "Support");
        defaults.put("ui.dashboard.tab_debug_support", "Debug & Support");

        defaults.put("ui.dashboard.hints_header", "Hints");
        defaults.put("ui.dashboard.placeholders_header", "Placeholders");
        defaults.put("ui.dashboard.quick_actions", "Quick Actions");

        defaults.put("ui.dashboard.action_open_tags", "Open Tag UI");
        defaults.put("ui.dashboard.action_clear_cache", "Clear Cache");
        defaults.put("ui.dashboard.action_refresh_nameplate", "Refresh Nameplate");
        defaults.put("ui.dashboard.action_debug_snapshot", "Debug Snapshot");

        defaults.put("ui.dashboard.button_refresh", "Refresh");
        defaults.put("ui.dashboard.button_reload", "Reload");
        defaults.put("ui.dashboard.button_mod_page", "Mod Page");
        defaults.put("ui.dashboard.button_bug_report", "Report Bug");

        defaults.put("ui.dashboard.overview.line0", "Use /tags to open the Tag Selector.");
        defaults.put("ui.dashboard.overview.line1", "Equip tags you own or purchase via your economy plugin.");
        defaults.put("ui.dashboard.overview.line2", "Nameplates refresh automatically when ranks or tags change.");
        defaults.put("ui.dashboard.overview.line3", "Use this dashboard to reload config, clear caches, and debug.");

        defaults.put("ui.dashboard.overview.hint0", "Add tags in tags.json, then click Reload to apply them.");
        defaults.put("ui.dashboard.overview.hint1", "Use /tagsadmin givetag <player> <id> to grant tags directly.");
        defaults.put("ui.dashboard.overview.hint2", "Use the Quick Actions box below for common admin tasks.");

        defaults.put("ui.dashboard.integrations.line0", "LuckPerms, HyperPerms, and PermissionsPlus can provide rank groups and permissions.");
        defaults.put("ui.dashboard.integrations.line1", "Economy priority is determined by your configured integration order.");
        defaults.put("ui.dashboard.integrations.line2", "Free tags may still require permission or stat checks.");
        defaults.put("ui.dashboard.integrations.line3", "The summary above shows which backends are currently detected.");

        defaults.put("ui.dashboard.integrations.hint0", "If no economy is detected, paid tag purchases cannot complete.");
        defaults.put("ui.dashboard.integrations.hint1", "Use Debug Snapshot to confirm which backend is active.");

        defaults.put("ui.dashboard.debug.line0", "Debug Snapshot prints full plugin state to console and logs.");
        defaults.put("ui.dashboard.debug.line1", "Snapshot includes active tag, integrations, storage, and version info.");
        defaults.put("ui.dashboard.debug.line2", "Attach that output when opening a bug report.");
        defaults.put("ui.dashboard.debug.line3", "Use /tagsadmin storage to see which backend is active.");
        defaults.put("ui.dashboard.debug.line4", "Use /tagsadmin debugstorage for deeper storage checks.");
        defaults.put("ui.dashboard.debug.line5", "Refresh Nameplate re-sends the current tag to your nameplate.");
        defaults.put("ui.dashboard.debug.line6", "Clear Cache forgets local tag and nameplate cache for your player.");

        defaults.put("ui.dashboard.support.line0", "Need help? Use the mod page, bug report button, or your support channels.");
        defaults.put("ui.dashboard.support.line1", "Attach debug snapshots and relevant server logs when reporting issues.");

        // ---------------- COMMAND / CHAT KEYS ----------------
        defaults.put("cmd.help.header", "&b=== MysticNameTags Commands ===");
        defaults.put("cmd.help.line.help", "&7/tags help &8- &fShow this help message");
        defaults.put("cmd.help.line.info", "&7/tags info &8- &fShow plugin information");
        defaults.put("cmd.help.line.reload", "&7/tags reload &8- &fReload configuration");
        defaults.put("cmd.help.line.ui", "&7/tags ui &8- &fOpen the dashboard UI");
        defaults.put("cmd.help.line.tags", "&7/tags tags &8- &fOpen the tag selection UI");
        defaults.put("cmd.help.footer", "&b========================");

        defaults.put("cmd.reload.no_permission", "&cYou do not have permission to use this command.");
        defaults.put("cmd.reload.not_loaded", "&cMysticNameTags is not loaded.");
        defaults.put("cmd.reload.start", "&7[&bMysticNameTags&7] &fReloading configuration and tags...");
        defaults.put("cmd.reload.success", "&7[&bMysticNameTags&7] &aReload complete!");
        defaults.put("cmd.reload.failed", "&7[&bMysticNameTags&7] &cReload failed. Check console.");

        defaults.put("cmd.ui.no_permission", "&cYou do not have permission to use this command.");
        defaults.put("cmd.ui.not_loaded", "&cMysticNameTags is not loaded.");
        defaults.put("cmd.ui.opening", "&7[&bMysticNameTags&7] &fOpening &bDashboard&f...");
        defaults.put("cmd.ui.opened", "&aDashboard opened. &fPress &7ESC&f to close.");
        defaults.put("cmd.ui.no_player_component", "&cError: &7Could not get Player component.");
        defaults.put("cmd.ui.open_error", "&cError opening dashboard: &7{error}");

        defaults.put("cmd.tags.no_player_component", "&cError: &7Could not get Player component.");
        defaults.put("cmd.tags.no_account_id", "&cError: &7Could not determine your account id.");
        defaults.put("cmd.tags.opening", "&7[&bMysticNameTags&7] &fOpening &bTag Selector&f...");
        defaults.put("cmd.tags.open_error", "&cError opening tag selector: &7{error}");

        defaults.put("cmd.tags.owned_disabled", "&cThe owned tags menu is disabled in settings.");
        defaults.put("cmd.tags.owned_opening", "&7[&bMysticNameTags&7] &fOpening &bOwned Tags&f...");
        defaults.put("cmd.tags.owned_open_error", "&cError opening owned tags selector: &7{error}");

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

        defaults.put("dashboard.version_label", "Version: {version}");
        defaults.put("dashboard.version_label_update", "Version: {version} (update available: {latest})");
        defaults.put("dashboard.version_label_ahead", "Version: {version} (ahead of CurseForge: {latest})");
        defaults.put("dashboard.integrations_prefix", "Integrations:");
        defaults.put("dashboard.integration_luckperms", "LuckPerms");
        defaults.put("dashboard.integration_luckperms_none", "LuckPerms (none)");
        defaults.put("dashboard.integrations_plus_permissionsplus", "+ PermissionsPlus");
        defaults.put("dashboard.integrations_economy_prefix", "| Economy:");
        defaults.put("dashboard.economy_primary", "EconomySystem (primary)");
        defaults.put("dashboard.economy_fallback_prefix", "(fallback: ");
        defaults.put("dashboard.economy_none", "none");
        defaults.put("dashboard.loaded_tags_label", "Loaded Tags: {count}");
        defaults.put("dashboard.storage_file", "Storage: File (playerdata/*.json)");
        defaults.put("dashboard.storage_sqlite", "Storage: SQLite ({file})");
        defaults.put("dashboard.storage_mysql", "Storage: MySQL {host}:{port}/{database}");
        defaults.put("dashboard.placeholders_prefix", "Placeholders:");
        defaults.put("dashboard.placeholders_none", "none");
        defaults.put("dashboard.cpu_label", "CPU (process): {percent}% ({cores} cores)");
        defaults.put("dashboard.cpu_na", "CPU (process): N/A");
        defaults.put("dashboard.ram_label", "RAM (JVM): {used} / {max} MB");
        defaults.put("dashboard.uptime_label", "Uptime: {uptime}");
        defaults.put("dashboard.welcome", "Welcome to MysticNameTags!");
        defaults.put("dashboard.update_title", "Update");
        defaults.put("dashboard.update_available", "A new version ({latest}) is available. You are running {current}. Visit {url} for details.");
        defaults.put("dashboard.devbuild_title", "Dev build");
        defaults.put("dashboard.devbuild_ahead", "You are running dev build {current}, which is ahead of CurseForge ({latest}).");
        defaults.put("dashboard.refreshed_toast", "Dashboard data refreshed.");
        defaults.put("dashboard.refreshed_status", "Status: dashboard refreshed.");
        defaults.put("dashboard.reloaded_toast", "Configuration and tags reloaded from disk.");
        defaults.put("dashboard.reloaded_status", "Status: configuration reloaded from disk.");
        defaults.put("dashboard.modpage_title_suffix", "mod page");
        defaults.put("dashboard.modpage_open", "Open the mod page in your browser: {url}");
        defaults.put("dashboard.bugreport_title_suffix", "bug report");
        defaults.put("dashboard.bugreport_open", "Open the issue tracker: {url}");
        defaults.put("dashboard.open_tags_failed", "Could not open the tag UI; player component was missing.");
        defaults.put("dashboard.opened_tags_toast", "Opened the tag selector UI.");
        defaults.put("dashboard.cleared_cache_toast", "Local tag and nameplate cache cleared.");
        defaults.put("dashboard.cleared_cache_status", "Status: local caches cleared.");
        defaults.put("dashboard.refresh_nameplate_world_missing", "You must be in a world to refresh your nameplate.");
        defaults.put("dashboard.refresh_nameplate_toast", "Nameplate refresh requested.");
        defaults.put("dashboard.refresh_nameplate_status", "Status: nameplate refresh requested.");
        defaults.put("dashboard.debug_snapshot_toast", "Debug snapshot printed to console/logs.");
        defaults.put("dashboard.debug_snapshot_status", "Status: debug snapshot generated.");
        defaults.put("dashboard.internal_error_toast", "An internal error occurred; see server console for details.");

        defaults.put("tags.not_found", "That tag no longer exists.");
        defaults.put("tags.no_permission", "You don't have access to that tag.");
        defaults.put("tags.unlocked_free", "Unlocked {tag} and equipped!");
        defaults.put("tags.unlocked_paid", "Purchased {tag} and equipped!");
        defaults.put("tags.equipped", "Equipped {tag}.");
        defaults.put("tags.unequipped", "Unequipped {tag}.");
        defaults.put("tags.no_economy", "Economy plugin is not configured.");
        defaults.put("tags.not_enough_money", "You cannot afford that tag.");
        defaults.put("tags.transaction_failed", "Transaction failed. Please try again.");
        defaults.put("tags.requirements_not_met", "You do not meet the requirements for that tag.");
        defaults.put("tags.equip_cooldown", "You must wait {seconds}s before equipping another tag.");

        return defaults;
    }
}