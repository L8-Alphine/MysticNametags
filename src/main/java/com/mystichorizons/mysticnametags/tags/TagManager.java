package com.mystichorizons.mysticnametags.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.nameplate.NameplateTextResolver;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;

public class TagManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /**
     * Default category used when upgrading tags.json entries that
     * are missing a category (older plugin versions).
     */
    private static final String DEFAULT_CATEGORY = "General";

    private static TagManager instance;

    private volatile List<TagDefinition> tagList = Collections.emptyList();
    private final Map<String, TagDefinition> tags = new LinkedHashMap<>();
    private final Map<UUID, PlayerTagData> playerData = new HashMap<>();
    // Cache of the last applied nameplate text (colored or plain)
    private final Map<UUID, String> lastNameplateText = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRef> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, World>    onlineWorlds  = new ConcurrentHashMap<>();
    private final IntegrationManager integrations;

    // Cache of "canUseTag" decisions per player + tag id (lowercase).
    // Avoids repeated permission checks on large tag sets.
    private final Map<UUID, Map<String, Boolean>> canUseCache = new ConcurrentHashMap<>();

    private volatile List<String> categories = Collections.emptyList();

    // When true, the tags UI will still LIST tags that would normally be
    // hidden by Full Permission Gate, so staff can see/debug them.
    private volatile boolean showHiddenTagsForDebug = false;

    private File configFile;
    private File playerDataFolder;

    public static void init(@Nonnull IntegrationManager integrations) {
        instance = new TagManager(integrations);
        instance.loadConfig();
    }

    public static TagManager get() {
        return instance;
    }

    public List<String> getCategories() {
        return categories;
    }

    public boolean isShowHiddenTagsForDebug() {
        return showHiddenTagsForDebug;
    }

    public void setShowHiddenTagsForDebug(boolean showHiddenTagsForDebug) {
        this.showHiddenTagsForDebug = showHiddenTagsForDebug;
    }

    private TagManager(@Nonnull IntegrationManager integrations) {
        this.integrations = integrations;

        MysticNameTagsPlugin plugin = MysticNameTagsPlugin.getInstance();
        File dataFolder = plugin.getDataDirectory().toFile();

        this.playerDataFolder = new File(dataFolder, "playerdata");
        this.playerDataFolder.mkdirs();

        this.configFile = new File(dataFolder, "tags.json");
    }

    // ------------- Config -------------

    private void loadConfig() {
        try {
            if (!configFile.exists()) {
                saveDefaultConfig();
            }

            List<TagDefinition> list;
            try (InputStreamReader reader =
                         new InputStreamReader(
                                 new FileInputStream(configFile),
                                 StandardCharsets.UTF_8)) {

                Type listType = new TypeToken<List<TagDefinition>>(){}.getType();
                list = GSON.fromJson(reader, listType);
            }

            int rawCount         = (list != null) ? list.size() : 0;
            int skippedNull      = 0;
            int skippedNoId      = 0;
            int overwrittenDupes = 0;

            // ----- Auto-upgrade: ensure all tags have a category -----
            boolean upgradedCategories = upgradeCategoriesIfNeeded(list);

            tags.clear();

            if (list != null) {
                for (TagDefinition def : list) {
                    if (def == null) {
                        skippedNull++;
                        continue;
                    }
                    String id = def.getId();
                    if (id == null || id.trim().isEmpty()) {
                        skippedNoId++;
                        continue;
                    }

                    String key = id.toLowerCase(Locale.ROOT);
                    if (tags.containsKey(key)) {
                        overwrittenDupes++;
                        LOGGER.at(Level.FINE)
                                .log("[MysticNameTags] Duplicate tag id '" + key + "' – overwriting previous definition.");
                    }

                    tags.put(key, def);
                }
            }

            // Rebuild tagList
            tagList = List.copyOf(tags.values());

            // rebuild category list from current definitions
            Set<String> catSet = new LinkedHashSet<>();
            for (TagDefinition def : tags.values()) {
                String cat = def.getCategory();
                if (cat != null) {
                    cat = cat.trim();
                    if (!cat.isEmpty()) {
                        catSet.add(cat);
                    }
                }
            }
            categories = List.copyOf(catSet);

            LOGGER.at(Level.INFO).log("[MysticNameTags] Parsed " + rawCount + " entries from tags.json");
            if (skippedNull > 0) {
                LOGGER.at(Level.WARNING).log("[MysticNameTags] Skipped " + skippedNull + " null tag entries.");
            }
            if (skippedNoId > 0) {
                LOGGER.at(Level.WARNING).log("[MysticNameTags] Skipped " + skippedNoId + " entries with missing/empty id.");
            }
            if (overwrittenDupes > 0) {
                LOGGER.at(Level.WARNING).log("[MysticNameTags] " + overwrittenDupes + " entries overwrote an existing tag id.");
            }

            LOGGER.at(Level.INFO).log("[MysticNameTags] Loaded " + tags.size() + " unique tags.");
            LOGGER.at(Level.INFO).log("[MysticNameTags] Detected " + categories.size() + " categories: " + categories);

            // If we upgraded any entries (added default categories),
            // write the new structure back to disk so the file is permanently updated.
            if (upgradedCategories && list != null) {
                saveConfig(list);
            }

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load tags.json");
        }
    }

    /**
     * Auto-upgrade step: ensure every tag has a non-empty category.
     * This is mainly for older tags.json files created before categories existed.
     *
     * @param list the parsed tag list (may be null)
     * @return true if any tag was modified and the file should be saved back.
     */
    private boolean upgradeCategoriesIfNeeded(@Nullable List<TagDefinition> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }

        boolean changed = false;

        for (TagDefinition def : list) {
            if (def == null) {
                continue;
            }

            String cat = def.getCategory();
            if (cat == null || cat.trim().isEmpty()) {
                // Because TagDefinition lives in the same package, we can safely
                // access its package-private field or setter here.
                def.setCategory(DEFAULT_CATEGORY); // if you don't have a setter, use: def.category = DEFAULT_CATEGORY;
                changed = true;
            }
        }

        if (changed) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Auto-updated tags.json: missing categories set to '" + DEFAULT_CATEGORY + "'.");
        }

        return changed;
    }

    /**
     * Writes the given tag list back to tags.json using the shared GSON instance.
     */
    private void saveConfig(@Nonnull List<TagDefinition> list) {
        try (OutputStreamWriter writer =
                     new OutputStreamWriter(
                             new FileOutputStream(configFile),
                             StandardCharsets.UTF_8)) {

            GSON.toJson(list, writer);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to write upgraded tags.json");
        }
    }

    private void saveDefaultConfig() {
        try (OutputStreamWriter writer =
                     new OutputStreamWriter(
                             new FileOutputStream(configFile),
                             StandardCharsets.UTF_8)) {

            List<TagDefinition> defaults = new ArrayList<>();

            TagDefinition mystic = new TagDefinitionBuilder()
                    .id("mystic")
                    .display("&#8A2BE2&l[Mystic]")
                    .description("&7A shimmering arcane title.")
                    .price(0)
                    .purchasable(false)
                    .permission("mysticnametags.tag.mystic")
                    .category("Special")
                    .build();

            TagDefinition dragon = new TagDefinitionBuilder()
                    .id("dragon")
                    .display("&#FFAA00&l[Dragon]")
                    .description("&7Forged in Avalon Realms fire.")
                    .price(5000)
                    .purchasable(true)
                    .permission("mysticnametags.tag.dragon")
                    .category("Legendary")
                    .build();

            defaults.add(mystic);
            defaults.add(dragon);

            GSON.toJson(defaults, writer);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save default tags.json");
        }
    }

    public static void reload() {
        if (instance == null) {
            return;
        }

        LOGGER.at(Level.INFO).log("[MysticNameTags] Reloading tags.json...");

        // Re-read tags.json
        instance.loadConfig();

        // Clear "canUse" cache since permissions / tags may change
        instance.clearCanUseCache();

        // Optionally re-apply nameplates for all online players
        instance.refreshAllOnlineNameplates();

        LOGGER.at(Level.INFO).log("[MysticNameTags] tags.json reload complete.");
    }

    // ------------- Player data -------------

    @Nonnull
    private PlayerTagData getOrLoad(@Nonnull UUID uuid) {
        return playerData.computeIfAbsent(uuid, this::loadPlayerData);
    }

    private PlayerTagData loadPlayerData(UUID uuid) {
        File file = new File(playerDataFolder, uuid.toString() + ".json");
        if (!file.exists()) {
            return new PlayerTagData();
        }

        try (InputStreamReader reader =
                     new InputStreamReader(
                             new FileInputStream(file),
                             StandardCharsets.UTF_8)) {

            PlayerTagData data = GSON.fromJson(reader, PlayerTagData.class);
            return data != null ? data : new PlayerTagData();
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load tag data for " + uuid);
            return new PlayerTagData();
        }
    }

    private void savePlayerData(UUID uuid) {
        PlayerTagData data = playerData.get(uuid);
        if (data == null) return;

        File file = new File(playerDataFolder, uuid.toString() + ".json");
        try (OutputStreamWriter writer =
                     new OutputStreamWriter(
                             new FileOutputStream(file),
                             StandardCharsets.UTF_8)) {

            GSON.toJson(data, writer);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save tag data for " + uuid);
        }
    }

    // ------------- Public API -------------

    private void clearCanUseCache() {
        canUseCache.clear();
    }

    public void clearCanUseCache(UUID uuid) {
        if (uuid == null) return;
        canUseCache.remove(uuid);
    }

    public Collection<TagDefinition> getAllTags() {
        return Collections.unmodifiableCollection(tags.values());
    }

    public int getTagCount() {
        return tagList.size();
    }

    @Nullable
    public TagDefinition getTag(String id) {
        if (id == null) return null;
        return tags.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean ownsTag(@Nullable UUID uuid, @Nullable String id) {
        if (uuid == null || id == null) {
            return false;
        }
        return getOrLoad(uuid).owns(id.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public TagDefinition getEquipped(UUID uuid) {
        PlayerTagData data = getOrLoad(uuid);
        if (data.getEquipped() == null) return null;
        return getTag(data.getEquipped());
    }

    public boolean equipTag(UUID uuid, String id) {
        if (!ownsTag(uuid, id)) return false;

        PlayerTagData data = getOrLoad(uuid);
        data.setEquipped(id.toLowerCase(Locale.ROOT));
        savePlayerData(uuid);
        return true;
    }

    /**
     * Returns true if the player can use this tag:
     * - already owns it in their saved data, OR
     * - has the permission node configured on the tag.
     *
     * Results are cached per-player to keep UI snappy when many tags exist.
     */
    public boolean canUseTag(@Nonnull PlayerRef playerRef,
                             @Nullable UUID uuid,
                             @Nonnull TagDefinition def) {

        String rawId = def.getId();
        if (rawId == null || rawId.isEmpty()) {
            return false;
        }

        String keyId = rawId.toLowerCase(Locale.ROOT);

        // If we have a UUID, try to use cache
        if (uuid != null) {
            Map<String, Boolean> perPlayer =
                    canUseCache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());

            Boolean cached = perPlayer.get(keyId);
            if (cached != null) {
                return cached;
            }

            boolean result = internalCanUseTagUnchecked(playerRef, uuid, def, keyId);

            perPlayer.put(keyId, result);
            return result;
        }

        // No UUID → do a one-off check (can't cache safely).
        return internalCanUseTagUnchecked(playerRef, uuid, def, keyId);
    }

    /**
     * Actual logic for "canUseTag" without caching concerns.
     */
    private boolean internalCanUseTagUnchecked(@Nonnull PlayerRef playerRef,
                                               @Nullable UUID uuid,
                                               @Nonnull TagDefinition def,
                                               @Nonnull String normalizedId) {

        boolean fullGate = Settings.get().isFullPermissionGateEnabled();
        String perm = def.getPermission();

        // If full gate is enabled and a permission is defined,
        // the permission MUST be present for this tag – even if
        // the player "owns" it via JSON.
        if (fullGate && perm != null && !perm.isEmpty()) {
            try {
                if (!integrations.hasPermission(playerRef, perm)) {
                    return false;
                }
            } catch (Throwable ignored) {
                // If we can't check perms, treat as not granted
                return false;
            }
        }

        // Owned in JSON data?
        if (uuid != null) {
            PlayerTagData data = getOrLoad(uuid);
            if (data.owns(normalizedId)) {
                return true;
            }
        }

        // Permission-based access as an alternate path when the full gate
        // is disabled (or when no permission is defined).
        if (perm != null && !perm.isEmpty()) {
            try {
                return integrations.hasPermission(playerRef, perm);
            } catch (Throwable ignored) {
                // fall through
            }
        }

        return false;
    }

    /**
     * Toggle a tag:
     * - If currently equipped: unequip.
     * - Otherwise: purchase (if needed) and equip.
     *
     * UI / listeners are responsible for reacting to the result and
     * refreshing nameplates, etc.
     */
    public TagPurchaseResult toggleTag(@Nonnull PlayerRef playerRef,
                                       @Nonnull UUID uuid,
                                       @Nonnull String id) {
        TagDefinition def = getTag(id);
        if (def == null) {
            return TagPurchaseResult.NOT_FOUND;
        }

        PlayerTagData data = getOrLoad(uuid);

        // If this tag is already equipped -> unequip
        String equipped = data.getEquipped();
        if (equipped != null && equipped.equalsIgnoreCase(def.getId())) {
            data.setEquipped(null);
            savePlayerData(uuid);
            return TagPurchaseResult.UNEQUIPPED;
        }

        // Otherwise, delegate to purchase + equip
        return purchaseAndEquip(playerRef, uuid, id);
    }

    /**
     * Purchase a tag with economy (if configured) and equip it.
     */
    public TagPurchaseResult purchaseAndEquip(@Nonnull PlayerRef playerRef,
                                              @Nonnull UUID uuid,
                                              @Nonnull String id) {
        TagDefinition def = getTag(id);
        if (def == null) {
            return TagPurchaseResult.NOT_FOUND;
        }

        boolean fullGate = Settings.get().isFullPermissionGateEnabled();
        String perm = def.getPermission();

        // If full permission gate is enabled and this tag has a permission
        // node, require it up-front before ANY other logic (owning,
        // purchasing, etc.).
        if (fullGate && perm != null && !perm.isEmpty()) {
            try {
                if (!integrations.hasPermission(playerRef, perm)) {
                    return TagPurchaseResult.NO_PERMISSION;
                }
            } catch (Throwable ignored) {
                return TagPurchaseResult.NO_PERMISSION;
            }
        }

        PlayerTagData data = getOrLoad(uuid);

        if (data.owns(def.getId())) {
            data.setEquipped(def.getId());
            savePlayerData(uuid);
            return TagPurchaseResult.EQUIPPED_ALREADY_OWNED;
        }

        // Free / non-purchasable tags – no economy needed
        if (!def.isPurchasable() || def.getPrice() <= 0) {
            data.addOwned(def.getId());
            data.setEquipped(def.getId());
            savePlayerData(uuid);

            // grant permission if defined (for non-full-gate setups)
            maybeGrantPermission(uuid, perm);

            return TagPurchaseResult.UNLOCKED_FREE;
        }

        // Paid tag – requires some economy backend
        if (!integrations.hasAnyEconomy()) {
            return TagPurchaseResult.NO_ECONOMY;
        }

        if (!integrations.hasBalance(uuid, def.getPrice())) {
            return TagPurchaseResult.NOT_ENOUGH_MONEY;
        }

        if (!integrations.withdraw(uuid, def.getPrice())) {
            return TagPurchaseResult.TRANSACTION_FAILED;
        }

        data.addOwned(def.getId());
        data.setEquipped(def.getId());
        savePlayerData(uuid);

        // grant permission if defined (for non-full-gate setups)
        maybeGrantPermission(uuid, perm);

        return TagPurchaseResult.UNLOCKED_PAID;
    }

    /**
     * Build the final COLORED “[Rank] Name [Tag]” string
     * for chat / scoreboards / UI previews (NOT for Nameplate component).
     */
    public String buildNameplate(@Nonnull PlayerRef playerRef,
                                 @Nonnull String baseName,
                                 @Nullable UUID uuid) {
        String rank = null;
        String tag  = null;

        if (uuid != null) {
            // Use the unified prefix backend (PrefixesPlus -> LuckPerms -> none)
            rank = integrations.getPrimaryPrefix(uuid);
            TagDefinition active = resolveActiveOrDefaultTag(uuid);
            if (active != null) {
                tag = active.getDisplay(); // e.g. "&#8A2BE2&l[Mystic]"
            }
        }

        return NameplateTextResolver.build(playerRef, rank, baseName, tag);
    }

    /**
     * Convenience: full colored “[Rank] Name [Tag]” from PlayerRef.
     */
    public String getColoredFullNameplate(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        String baseName = playerRef.getUsername();
        return buildNameplate(playerRef, baseName, uuid);
    }

    /**
     * Convenience: full colored “[Rank] Name [Tag]” from UUID + name
     * (for places where we don't have a PlayerRef on hand).
     */
    public String getColoredFullNameplate(UUID uuid, String baseName) {
        PlayerRef ref = onlinePlayers.get(uuid);
        if (ref != null) {
            // NameplateTextResolver.build should already be returning something
            // suitable for chat/scoreboard (with &# codes, WiFlow/helpch applied, etc.)
            return buildNameplate(ref, baseName, uuid);
        }

        // Fallback path when we only have UUID + base name
        String rank = integrations.getPrimaryPrefix(uuid);
        TagDefinition active = getEquipped(uuid);
        String tagDisplay = (active != null) ? active.getDisplay() : null;

        String formatted = Settings.get().formatNameplate(rank, baseName, tagDisplay);
        // Again: keep &#RRGGBB, normalize only § → &
        return ColorFormatter.translateAlternateColorCodes('§', formatted);
    }

    /**
     * Plain “[Rank] Name [Tag]” with all formatting stripped.
     */
    public String getPlainFullNameplate(UUID uuid, String baseName) {
        String colored = getColoredFullNameplate(uuid, baseName);
        return ColorFormatter.stripFormatting(colored).trim();
    }

    /**
     * Build the final *plain* nameplate text for Nameplate component:
     * [Rank] Name [Tag], with ALL color codes stripped.
     */
    public String buildPlainNameplate(@Nonnull PlayerRef playerRef,
                                      @Nonnull String baseName,
                                      @Nullable UUID uuid) {

        String rank = null;
        String tag  = null;

        if (uuid != null) {
            rank = integrations.getPrimaryPrefix(uuid);
            TagDefinition active = resolveActiveOrDefaultTag(uuid);
            if (active != null) tag = active.getDisplay();
        }

        String built = NameplateTextResolver.build(playerRef, rank, baseName, tag);
        return ColorFormatter.stripFormatting(built).trim();
    }

    /**
     * Rebuild and apply the plain nameplate for this player if it changed.
     * Entry point for join, tag changes, rank changes, etc.
     */
    public void refreshNameplate(@Nonnull PlayerRef playerRef,
                                 @Nonnull World world) {
        UUID uuid = playerRef.getUuid();
        String baseName = playerRef.getUsername();

        // Build final nameplate text (rank/name/tag) using config format,
        // optional WiFlow + helpch placeholders, then colorization.
        String rank = null;
        String tag  = null;

        if (!Settings.get().isNameplatesEnabled()) {
            // nameplates disabled: restore vanilla text and stop
            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null || !ref.isValid()) return;

                    NameplateManager.get().restore(uuid, store, ref, baseName);
                } catch (Throwable ignored) {}
            });

            lastNameplateText.remove(uuid);
            return;
        }

        if (uuid != null) {
            // Unified prefix backend (PrefixesPlus -> LuckPerms -> none)
            rank = integrations.getPrimaryPrefix(uuid);

            TagDefinition active = getEquipped(uuid);
            if (active != null) {
                // Raw display string (may contain placeholders + color codes)
                tag = active.getDisplay();
            }
        }

        String built = NameplateTextResolver.build(playerRef, rank, baseName, tag);

        // Nameplate component: NO colors supported yet
        String finalText = ColorFormatter.stripFormatting(built).trim();

        // Cache / compare the PLAIN text (not colored)
        String previous = lastNameplateText.put(uuid, finalText);
        if (previous != null && previous.equals(finalText)) {
            return;
        }

        world.execute(() -> {
            try {
                EntityStore entityStore = world.getEntityStore();
                Store<EntityStore> store = entityStore.getStore();

                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    return;
                }

                NameplateManager.get().apply(uuid, store, ref, finalText);

                // IMPORTANT: if EndlessLeveling nameplates are enabled,
                // force its cache to refresh so [Lvl] doesn't "disappear" after /tags.
                if (Settings.get().isEndlessLevelingNameplatesEnabled()) {
                    integrations.invalidateEndlessLevelingNameplate(uuid);
                }

            } catch (Exception e) {
                LOGGER.at(Level.WARNING).withCause(e)
                        .log("[MysticNameTags] Failed to refresh nameplate for %s", baseName);
            }
        });
    }

    // ------------- Helper builder -------------

    private static class TagDefinitionBuilder {
        private final TagDefinition def = new TagDefinition();

        public TagDefinitionBuilder id(String id) {
            def.id = id;
            return this;
        }

        public TagDefinitionBuilder display(String s) {
            def.display = s;
            return this;
        }

        public TagDefinitionBuilder description(String s) {
            def.description = s;
            return this;
        }

        public TagDefinitionBuilder price(double p) {
            def.price = p;
            return this;
        }

        public TagDefinitionBuilder purchasable(boolean b) {
            def.purchasable = b;
            return this;
        }

        public TagDefinitionBuilder permission(String p) {
            def.permission = p;
            return this;
        }

        public TagDefinitionBuilder category(String c) {
            def.category = c;
            return this;
        }

        public TagDefinition build() {
            return def;
        }
    }

    public enum TagPurchaseResult {
        NOT_FOUND,
        NO_PERMISSION,
        UNLOCKED_FREE,
        UNLOCKED_PAID,
        EQUIPPED_ALREADY_OWNED,
        UNEQUIPPED,
        NO_ECONOMY,
        NOT_ENOUGH_MONEY,
        TRANSACTION_FAILED
    }

    /**
     * Expose integrations for UI controllers (e.g. to show balances).
     */
    public IntegrationManager getIntegrations() {
        return integrations;
    }

    /**
     * Clear cached nameplate when the player fully leaves.
     */
    public void forgetNameplate(@Nonnull UUID uuid) {
        lastNameplateText.remove(uuid);
    }

    // ---- Online tracking (for fast, low-cost refreshes) ----

    public void trackOnlinePlayer(@Nonnull PlayerRef ref, @Nonnull World world) {
        UUID uuid = ref.getUuid();
        onlinePlayers.put(uuid, ref);
        onlineWorlds.put(uuid, world);
    }

    public void untrackOnlinePlayer(@Nonnull UUID uuid) {
        onlinePlayers.remove(uuid);
        onlineWorlds.remove(uuid);
        forgetNameplate(uuid);
        clearCanUseCache(uuid);
        // Also clear the NameplateManager cache so restore() never sees stale values
        NameplateManager.get().forget(uuid);
    }

    @Nullable
    public PlayerRef getOnlinePlayer(@Nonnull UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    @Nullable
    public World getOnlineWorld(@Nonnull UUID uuid) {
        return onlineWorlds.get(uuid);
    }

    public String getColoredActiveTag(@Nonnull UUID uuid) {
        TagDefinition def = resolveActiveOrDefaultTag(uuid);
        if (def == null) {
            return "";
        }

        String display = def.getDisplay();
        if (display == null) {
            return "";
        }

        // For placeholders/chat: keep &#RRGGBB intact, just normalize § → &
        return ColorFormatter.translateAlternateColorCodes('§', display);
    }

    public String getPlainActiveTag(@Nonnull UUID uuid) {
        TagDefinition def = getEquipped(uuid);
        if (def == null) {
            return "";
        }
        return ColorFormatter.stripFormatting(def.getDisplay());
    }

    /**
     * Rebuild and apply nameplates for all currently tracked online players.
     * Called after /tags reload so new tag definitions + perms are reflected.
     */
    private void refreshAllOnlineNameplates() {
        if (onlinePlayers.isEmpty()) {
            return;
        }

        LOGGER.at(Level.INFO)
                .log("[MysticNameTags] Refreshing nameplates for " + onlinePlayers.size() + " online players...");

        for (Map.Entry<UUID, PlayerRef> entry : onlinePlayers.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerRef ref = entry.getValue();
            World world = onlineWorlds.get(uuid);

            if (ref == null || world == null) {
                continue;
            }

            try {
                refreshNameplate(ref, world);
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Failed to refresh nameplate during reload for " + uuid);
            }
        }
    }

    /**
     * Returns a view of tags for the given page (zero-based).
     * If page is out of range, it will be clamped.
     */
    public List<TagDefinition> getTagsPage(int page, int pageSize) {
        if (pageSize <= 0 || tagList.isEmpty()) {
            return Collections.emptyList();
        }

        int total = tagList.size();
        int totalPages = (int) Math.ceil(total / (double) pageSize);
        if (totalPages <= 0) {
            return Collections.emptyList();
        }

        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int start = safePage * pageSize;
        int end   = Math.min(start + pageSize, total);

        return tagList.subList(start, end); // cheap view, no copy
    }

    private void maybeGrantPermission(@Nonnull UUID uuid, @Nullable String perm) {
        if (perm == null || perm.isEmpty()) {
            return;
        }
        try {
            integrations.grantPermission(uuid, perm);
        } catch (Throwable ignored) {
            // we don't want permission failures to break purchases
        }
    }

    // ============================================================
    // Admin helpers – bypass economy & normal permission flow
    // ============================================================

    /**
     * Admin-only: grant a tag to a player, optionally equip it.
     * Skips economy and permission checks.
     *
     * @return true if the tag exists and was added (or already owned), false if tag doesn't exist.
     */
    public boolean adminGiveTag(@Nonnull UUID uuid,
                                @Nonnull String id,
                                boolean equip) {

        TagDefinition def = getTag(id);
        if (def == null) {
            return false; // unknown id
        }

        String keyId = def.getId().toLowerCase(Locale.ROOT);

        PlayerTagData data = getOrLoad(uuid);
        // Add to owned set
        data.addOwned(keyId);

        if (equip) {
            data.setEquipped(keyId);
        }

        savePlayerData(uuid);
        clearCanUseCache(uuid);

        // Refresh nameplate if they're online
        PlayerRef ref = onlinePlayers.get(uuid);
        World world   = onlineWorlds.get(uuid);
        if (ref != null && world != null) {
            refreshNameplate(ref, world);
        }

        return true;
    }

    /**
     * Admin-only: remove a specific tag from a player.
     *
     * @return true if the tag existed in their data and was removed.
     */
    public boolean adminRemoveTag(@Nonnull UUID uuid,
                                  @Nonnull String id) {

        PlayerTagData data = getOrLoad(uuid);
        String keyId = id.toLowerCase(Locale.ROOT);

        boolean removed = data.getOwned().remove(keyId);
        if (!removed) {
            return false;
        }

        // If it was equipped, unequip
        if (keyId.equalsIgnoreCase(data.getEquipped())) {
            data.setEquipped(null);
        }

        savePlayerData(uuid);
        clearCanUseCache(uuid);

        PlayerRef ref = onlinePlayers.get(uuid);
        World world   = onlineWorlds.get(uuid);
        if (ref != null && world != null) {
            refreshNameplate(ref, world);
        }

        return true;
    }

    /**
     * Admin-only: wipe all owned/equipped tags for a player.
     */
    public boolean adminResetTags(@Nonnull UUID uuid) {
        PlayerTagData data = getOrLoad(uuid);
        if (data.getOwned().isEmpty() && data.getEquipped() == null) {
            return false; // nothing to reset
        }

        // Clear MysticNameTags local data
        data.getOwned().clear();
        data.setEquipped(null);

        savePlayerData(uuid);
        clearCanUseCache(uuid);

        // Refresh nameplate if they're online
        PlayerRef ref = onlinePlayers.get(uuid);
        World world   = onlineWorlds.get(uuid);
        if (ref != null && world != null) {
            refreshNameplate(ref, world);
        }

        return true;
    }

    /**
     * Admin-only: wipe all owned/equipped tags for a player AND revoke any
     * tag permissions that MysticNameTags might have granted via the
     * active permission backend.
     *
     * NOTE: This only affects permissions that the backend *resolves* for
     * these nodes; it does not try to distinguish between "plugin-granted"
     * and "rank-granted" permissions.
     */
    public boolean adminResetTagsAndPermissions(@Nonnull UUID uuid) {
        // First do the normal data reset (owned + equipped + caches + nameplate)
        boolean changed = adminResetTags(uuid);
        if (!changed) {
            return false;
        }

        // Then try to revoke all tag permission nodes for this player.
        for (TagDefinition def : tags.values()) {
            String perm = def.getPermission();
            if (perm == null || perm.isEmpty()) {
                continue;
            }
            try {
                integrations.revokePermission(uuid, perm);
            } catch (Throwable ignored) {
                // We don't want a revoke failure to break the whole reset
            }
        }

        // No need to refresh nameplate again; adminResetTags already did it.
        return true;
    }

    @Nullable
    private TagDefinition resolveActiveOrDefaultTag(@Nonnull UUID uuid) {
        TagDefinition equipped = getEquipped(uuid);
        if (equipped != null) return equipped;

        Settings s = Settings.get();
        if (!s.isDefaultTagEnabled()) return null;

        String id = s.getDefaultTagId();
        if (id == null || id.trim().isEmpty()) return null;

        return getTag(id.trim());
    }
}
