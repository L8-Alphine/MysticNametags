package com.mystichorizons.mysticnametags.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.api.events.MysticNameTagsEvent;
import com.mystichorizons.mysticnametags.api.events.MysticNameTagsEventBus;
import com.mystichorizons.mysticnametags.api.events.MysticNameTagsEventType;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.license.MysticNameTagsLicense;
import com.mystichorizons.mysticnametags.nameplate.GlyphNameplateManager;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;
import com.mystichorizons.mysticnametags.nameplate.NameplateTextResolver;
import com.mystichorizons.mysticnametags.nameplate.banner.BannerAssetManager;
import com.mystichorizons.mysticnametags.nameplate.banner.BannerInfo;
import com.mystichorizons.mysticnametags.network.NetworkSyncService;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.ConsoleCommandRunner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final long CAN_USE_CACHE_TTL_MS = 5000L;

    private volatile List<TagDefinition> tagList = Collections.emptyList();
    private final Map<String, TagDefinition> tags = new LinkedHashMap<>();
    private final PlayerTagStore playerTagStore;
    // Concurrent because the Redis sync subscriber invalidates entries from
    // its own thread while world threads are reading them.
    private final Map<UUID, PlayerTagData> playerData = new ConcurrentHashMap<>();

    // Cache of the last applied nameplate text (colored or plain)
    private final Map<UUID, String> lastNameplateText = new ConcurrentHashMap<>();

    /** Tag ids already warned about for a missing banner file; keeps the log to one line each. */
    private final Set<String> loggedMissingBanners = ConcurrentHashMap.newKeySet();

    private final Map<UUID, PlayerRef> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, World> onlineWorlds = new ConcurrentHashMap<>();

    private final IntegrationManager integrations;

    // Cache of "canUseTag" decisions per player + tag id (lowercase).
    // Avoids repeated permission checks on large tag sets.
    private final Map<UUID, Map<String, CanUseCacheEntry>> canUseCache = new ConcurrentHashMap<>();

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

        // -------- Storage backend selection --------
        Settings settings = Settings.get();
        StorageBackend backend = StorageBackend.fromString(settings.getStorageBackendRaw());

        PlayerTagStore store;

        switch (backend) {
            case SQLITE: {
                File sqliteFile = new File(dataFolder, settings.getSqliteFile());
                String jdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();

                store = new SqlPlayerTagStore(
                        jdbcUrl,
                        "",
                        "",
                        GSON
                );

                store.migrateFromFolder(playerDataFolder, GSON);
                break;
            }

            case MYSQL:
            case MARIADB: {
                String host = settings.getMysqlHost();
                int port = settings.getMysqlPort();
                String db = settings.getMysqlDatabase();
                String user = settings.getMysqlUser();
                String pass = settings.getMysqlPassword();

                String jdbcUrl = backend.mySqlJdbcUrl(host, port, db);

                store = new SqlPlayerTagStore(jdbcUrl, user, pass, GSON);
                store.migrateFromFolder(playerDataFolder, GSON);
                break;
            }

            case REDIS: {
                store = new RedisPlayerTagStore(GSON);
                store.migrateFromFolder(playerDataFolder, GSON);
                break;
            }

            case FILE:
            default: {
                store = new FilePlayerTagStore(playerDataFolder, GSON);
                break;
            }
        }

        this.playerTagStore = store;
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

                Type listType = new TypeToken<List<TagDefinition>>() {}.getType();
                list = GSON.fromJson(reader, listType);
            }

            int rawCount = (list != null) ? list.size() : 0;
            int skippedNull = 0;
            int skippedNoId = 0;
            int overwrittenDupes = 0;

            boolean upgradedCategories = upgradeCategoriesIfNeeded(list);
            boolean upgradedStats = upgradeStatRequirementsIfNeeded(list);

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

            tagList = List.copyOf(tags.values());

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

            if ((upgradedCategories || upgradedStats) && list != null) {
                saveConfig(list);
            }

        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load tags.json");
        }
    }

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
                def.setCategory(DEFAULT_CATEGORY);
                changed = true;
            }
        }

        if (changed) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Auto-updated tags.json: missing categories set to '" + DEFAULT_CATEGORY + "'.");
        }

        return changed;
    }

    private boolean upgradeStatRequirementsIfNeeded(@Nullable List<TagDefinition> list) {
        if (list == null || list.isEmpty()) {
            return false;
        }

        boolean changed = false;

        for (TagDefinition def : list) {
            if (def == null) {
                continue;
            }

            List<TagDefinition.StatRequirement> current = def.getRequiredStats();
            boolean hasNewFormat =
                    current != null &&
                            !current.isEmpty() &&
                            (def.getRequiredStatKey() == null || def.getRequiredStatKey().isBlank());

            if (hasNewFormat) {
                continue;
            }

            String legacyKey = def.getRequiredStatKey();
            Integer legacyMin = def.getRequiredStatValue();

            if (legacyKey == null || legacyKey.isBlank() || legacyMin == null || legacyMin <= 0) {
                continue;
            }

            TagDefinition.StatRequirement migrated = new TagDefinition.StatRequirement();
            migrated.key = legacyKey.trim();
            migrated.min = legacyMin;

            def.setRequiredStats(List.of(migrated));
            def.clearLegacyStatRequirement();

            changed = true;

            LOGGER.at(Level.INFO).log(
                    "[MysticNameTags] Auto-upgraded tag '" + def.getId() +
                            "' from legacy requiredStatKey/requiredStatValue to requiredStats."
            );
        }

        if (changed) {
            LOGGER.at(Level.INFO)
                    .log("[MysticNameTags] Auto-updated tags.json: legacy stat requirements migrated to requiredStats.");
        }

        return changed;
    }

    private static final class CanUseCacheEntry {
        private final boolean value;
        private final long timestamp;

        private CanUseCacheEntry(boolean value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        private boolean isExpired(long now) {
            return now - timestamp >= CAN_USE_CACHE_TTL_MS;
        }
    }

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

    private void rebuildTagIndexesFromMap() {
        tagList = List.copyOf(tags.values());

        Set<String> catSet = new LinkedHashSet<>();
        for (TagDefinition def : tags.values()) {
            if (def == null) continue;
            String cat = def.getCategory();
            if (cat == null) continue;
            cat = cat.trim();
            if (!cat.isEmpty()) {
                catSet.add(cat);
            }
        }
        categories = List.copyOf(catSet);
        clearCanUseCache();
    }

    private void saveCurrentTagConfig() {
        saveConfig(new ArrayList<>(tags.values()));
    }

    private static void publishEvent(@Nonnull MysticNameTagsEventType type,
                                     @Nullable String actor,
                                     @Nullable UUID playerUuid,
                                     @Nullable String playerName,
                                     @Nullable String tagId,
                                     @Nullable String result,
                                     @Nullable Map<String, String> details) {
        MysticNameTagsEventBus.publish(new MysticNameTagsEvent(
                type,
                actor,
                playerUuid,
                playerName,
                tagId,
                result,
                details
        ));
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

        instance.loadConfig();
        instance.clearCanUseCache();
        instance.refreshAllOnlineNameplates();

        LOGGER.at(Level.INFO).log("[MysticNameTags] tags.json reload complete.");
    }

    /** Redraws every online nameplate without re-reading tags.json, e.g. after a license change. */
    public static void refreshAllNameplates() {
        TagManager current = instance;
        if (current == null) {
            return;
        }
        current.refreshAllOnlineNameplates();
    }

    // ------------- Player data -------------

    @Nonnull
    private PlayerTagData getOrLoad(@Nonnull UUID uuid) {
        return playerData.computeIfAbsent(uuid, this::loadPlayerData);
    }

    private PlayerTagData loadPlayerData(UUID uuid) {
        return playerTagStore.load(uuid);
    }

    private void savePlayerData(UUID uuid) {
        PlayerTagData data = playerData.get(uuid);
        if (data == null) return;

        if (data.isLoadFailed()) {
            // We never got a clean read of this record, so writing it back would
            // replace real ownership with an empty set. Drop the cached copy and
            // let the next access retry the load instead.
            LOGGER.at(Level.WARNING).log("[MysticNameTags] Not saving tag data for " + uuid
                    + ": it was never loaded successfully, so the change was discarded"
                    + " rather than overwriting stored data.");
            playerData.remove(uuid);
            clearCanUseCache(uuid);
            return;
        }

        playerTagStore.save(uuid, data);
        publishPlayerDataChanged(uuid);
    }

    /**
     * Tells the rest of the network that this player's stored record changed.
     * No-op unless redisSyncEnabled is on.
     */
    private void publishPlayerDataChanged(@Nonnull UUID uuid) {
        NetworkSyncService sync = NetworkSyncService.get();
        if (sync != null) {
            sync.publishPlayerDataChanged(uuid);
        }
    }

    /**
     * Re-reads this player's tag data from storage as they connect.
     *
     * On a network this is what carries a tag across servers: the copy cached
     * from their last visit here is dropped, and the shared MYSQL/REDIS record
     * written by whichever server they were last on is read fresh. A tag they
     * unlocked and equipped elsewhere is therefore already unlocked and
     * equipped by the time their nameplate is first drawn here.
     */
    public void onPlayerJoin(@Nonnull UUID uuid) {
        playerData.remove(uuid);
        clearCanUseCache(uuid);
        getOrLoad(uuid);
    }

    /**
     * Drops the cached copy on disconnect. Every mutation already writes
     * through to storage, so there is nothing to flush; keeping the copy would
     * only let this server later serve data another server has since changed.
     */
    public void onPlayerQuit(@Nonnull UUID uuid) {
        playerData.remove(uuid);
    }

    /**
     * Another server changed this player's tag data. Drop our cached copy so
     * the next read comes from shared storage, and repaint the nameplate if
     * the player happens to be on this server right now.
     *
     * Called on the Redis subscriber thread.
     */
    public void handleRemotePlayerDataChanged(@Nonnull UUID uuid) {
        playerData.remove(uuid);
        clearCanUseCache(uuid);

        if (!onlinePlayers.containsKey(uuid)) {
            return;
        }

        // Warm the cache here rather than on a world thread; the reload is a
        // storage round trip. forceRefreshNameplate marshals the ECS work.
        getOrLoad(uuid);
        forceRefreshIfOnline(uuid);
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

    public boolean isOwnNameplateVisible(@Nonnull UUID uuid) {
        return getOrLoad(uuid).isOwnNameplateVisible();
    }

    public boolean setOwnNameplateVisible(@Nonnull UUID uuid, boolean visible) {
        PlayerTagData data = getOrLoad(uuid);
        data.setOwnNameplateVisible(visible);
        savePlayerData(uuid);
        return visible;
    }

    public boolean toggleOwnNameplateVisible(@Nonnull UUID uuid) {
        PlayerTagData data = getOrLoad(uuid);
        boolean visible = !data.isOwnNameplateVisible();
        data.setOwnNameplateVisible(visible);
        savePlayerData(uuid);
        return visible;
    }

    @Nullable
    public TagDefinition getTag(String id) {
        if (id == null) return null;
        return tags.get(id.toLowerCase(Locale.ROOT));
    }

    @Nonnull
    public TagEditResult upsertSimpleTag(@Nullable String id,
                                         @Nullable String display,
                                         @Nullable String description,
                                         @Nullable String category,
                                         @Nullable String priceRaw,
                                         @Nullable String permission,
                                         @Nullable String actor) {
        String keyId = normalizeTagId(id);
        if (keyId == null || !keyId.matches("[a-z0-9_:\\-.]{1,64}")) {
            return TagEditResult.of(TagEditStatus.INVALID_ID, null,
                    "Tag ids must be 1-64 characters using letters, numbers, _, -, ., or :");
        }

        double price = 0.0D;
        if (priceRaw != null && !priceRaw.isBlank()) {
            try {
                price = Double.parseDouble(priceRaw.trim());
            } catch (NumberFormatException ignored) {
                return TagEditResult.of(TagEditStatus.INVALID_PRICE, null, "Price must be a number.");
            }
            if (price < 0.0D) {
                return TagEditResult.of(TagEditStatus.INVALID_PRICE, null, "Price cannot be negative.");
            }
        }

        boolean created = !tags.containsKey(keyId);
        TagDefinition def = created ? new TagDefinition() : tags.get(keyId);
        if (def == null) {
            return TagEditResult.of(TagEditStatus.FAILED, null, "Could not load or create tag definition.");
        }

        def.id = keyId;
        def.display = cleanOrDefault(display, "&7[" + prettifyIdForConfig(keyId) + "]");
        def.description = cleanOrDefault(description, "&7Created in-game.");
        def.category = cleanOrDefault(category, DEFAULT_CATEGORY);
        def.price = price;
        def.purchasable = price > 0.0D;
        def.permission = cleanNullable(permission);

        tags.put(keyId, def);
        rebuildTagIndexesFromMap();
        saveCurrentTagConfig();
        refreshAllOnlineNameplates();

        TagEditStatus status = created ? TagEditStatus.CREATED : TagEditStatus.UPDATED;
        TagAuditLogger.log(created ? "tag_editor_create" : "tag_editor_update",
                actor,
                null,
                null,
                keyId,
                status.name().toLowerCase(Locale.ROOT),
                Map.of("category", def.getCategory(), "price", price));
        publishEvent(created ? MysticNameTagsEventType.TAG_CREATED : MysticNameTagsEventType.TAG_UPDATED,
                actor,
                null,
                null,
                keyId,
                status.name(),
                Map.of("category", def.getCategory(), "price", String.valueOf(price)));

        return TagEditResult.of(status, def, null);
    }

    @Nonnull
    public TagEditResult deleteTagDefinition(@Nullable String id,
                                             @Nullable String actor) {
        String keyId = normalizeTagId(id);
        if (keyId == null) {
            return TagEditResult.of(TagEditStatus.INVALID_ID, null, "Tag id is required.");
        }

        TagDefinition removed = tags.remove(keyId);
        if (removed == null) {
            return TagEditResult.of(TagEditStatus.NOT_FOUND, null, "Tag not found.");
        }

        for (Map.Entry<UUID, PlayerTagData> entry : playerData.entrySet()) {
            PlayerTagData data = entry.getValue();
            if (data == null) continue;
            boolean changed = data.getOwned().remove(keyId);
            changed = data.removeFavorite(keyId) || changed;
            changed = data.getLoadouts().entrySet().removeIf(e -> keyId.equalsIgnoreCase(e.getValue())) || changed;
            if (keyId.equalsIgnoreCase(data.getEquipped())) {
                data.setEquipped(null);
                changed = true;
            }
            if (changed) {
                savePlayerData(entry.getKey());
            }
        }

        rebuildTagIndexesFromMap();
        saveCurrentTagConfig();
        refreshAllOnlineNameplates();

        TagAuditLogger.log("tag_editor_delete", actor, null, null, keyId, "deleted", null);
        publishEvent(MysticNameTagsEventType.TAG_DELETED,
                actor,
                null,
                null,
                keyId,
                "deleted",
                null);

        return TagEditResult.of(TagEditStatus.DELETED, removed, null);
    }

    public boolean ownsTag(@Nullable UUID uuid, @Nullable String id) {
        if (uuid == null || id == null) {
            return false;
        }
        return getOrLoad(uuid).owns(id.toLowerCase(Locale.ROOT));
    }

    /**
     * True when the autoUnlockPermissionTags setting treats this tag as
     * unlocked for the player: the tag is not paid, declares a permission
     * node, and the player currently holds that permission. Because the
     * check is live, losing the permission revokes access again.
     */
    public boolean isAutoUnlockedByPermission(@Nullable PlayerRef playerRef,
                                              @Nullable TagDefinition def) {
        if (playerRef == null || def == null) {
            return false;
        }
        if (!Settings.get().isAutoUnlockPermissionTagsEnabled()) {
            return false;
        }
        if (def.isPurchasable() && def.getPrice() > 0.0D) {
            return false;
        }

        String perm = def.getPermission();
        if (perm == null || perm.isEmpty()) {
            return false;
        }

        try {
            return integrations.hasPermission(playerRef, perm);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Persistent ownership OR permission-based auto-unlock (see
     * {@link #isAutoUnlockedByPermission}).
     */
    public boolean effectivelyOwnsTag(@Nullable PlayerRef playerRef,
                                      @Nullable UUID uuid,
                                      @Nullable TagDefinition def) {
        if (def == null || def.getId() == null) {
            return false;
        }
        if (uuid != null && ownsTag(uuid, def.getId())) {
            return true;
        }
        return isAutoUnlockedByPermission(playerRef, def);
    }

    @Nonnull
    public Set<String> getFavoriteTags(@Nonnull UUID uuid) {
        PlayerTagData data = getOrLoad(uuid);
        data.clearUnavailableFavorites();
        return Set.copyOf(data.getFavorites());
    }

    public FavoriteResult setFavorite(@Nonnull UUID uuid,
                                      @Nonnull String id,
                                      boolean favorite,
                                      @Nullable String actor) {
        String keyId = normalizeTagId(id);
        if (keyId == null || getTag(keyId) == null) {
            return FavoriteResult.NOT_FOUND;
        }

        PlayerTagData data = getOrLoad(uuid);
        if (!data.owns(keyId)) {
            return FavoriteResult.NOT_OWNED;
        }

        boolean changed = favorite ? data.addFavorite(keyId) : data.removeFavorite(keyId);
        savePlayerData(uuid);

        TagAuditLogger.log(favorite ? "favorite_add" : "favorite_remove",
                actor,
                uuid,
                null,
                keyId,
                changed ? "changed" : "unchanged",
                null);
        publishEvent(favorite ? MysticNameTagsEventType.TAG_FAVORITE_ADDED : MysticNameTagsEventType.TAG_FAVORITE_REMOVED,
                actor,
                uuid,
                null,
                keyId,
                changed ? "changed" : "unchanged",
                null);

        return favorite ? FavoriteResult.ADDED : FavoriteResult.REMOVED;
    }

    public FavoriteResult toggleFavorite(@Nonnull UUID uuid,
                                         @Nonnull String id,
                                         @Nullable String actor) {
        String keyId = normalizeTagId(id);
        if (keyId == null || getTag(keyId) == null) {
            return FavoriteResult.NOT_FOUND;
        }

        PlayerTagData data = getOrLoad(uuid);
        if (!data.owns(keyId)) {
            return FavoriteResult.NOT_OWNED;
        }

        boolean added;
        if (data.isFavorite(keyId)) {
            data.removeFavorite(keyId);
            added = false;
        } else {
            data.addFavorite(keyId);
            added = true;
        }
        savePlayerData(uuid);

        TagAuditLogger.log(added ? "favorite_add" : "favorite_remove",
                actor,
                uuid,
                null,
                keyId,
                "changed",
                null);
        publishEvent(added ? MysticNameTagsEventType.TAG_FAVORITE_ADDED : MysticNameTagsEventType.TAG_FAVORITE_REMOVED,
                actor,
                uuid,
                null,
                keyId,
                "changed",
                null);

        return added ? FavoriteResult.ADDED : FavoriteResult.REMOVED;
    }

    public TagPurchaseResult equipRandomTag(@Nonnull PlayerRef playerRef,
                                            @Nonnull UUID uuid,
                                            boolean favoritesOnly) {
        PlayerTagData data = getOrLoad(uuid);
        List<String> candidates = new ArrayList<>();
        Set<String> source = favoritesOnly ? data.getFavorites() : data.getOwned();

        for (String id : source) {
            if (id == null || id.isBlank()) continue;
            TagDefinition def = getTag(id);
            if (def == null) continue;
            if (!data.owns(id)) continue;
            if (!canUseTag(playerRef, uuid, def)) continue;
            candidates.add(id.toLowerCase(Locale.ROOT));
        }

        if (candidates.isEmpty()) {
            return TagPurchaseResult.NOT_FOUND;
        }

        String selected = candidates.get(new Random().nextInt(candidates.size()));
        TagPurchaseResult result = purchaseAndEquip(playerRef, uuid, selected);
        TagAuditLogger.log("random_equip",
                playerRef.getUsername(),
                uuid,
                playerRef.getUsername(),
                selected,
                result.name(),
                Map.of("favoritesOnly", favoritesOnly));
        return result;
    }

    @Nonnull
    public Map<String, String> getLoadouts(@Nonnull UUID uuid) {
        return Map.copyOf(getOrLoad(uuid).getLoadouts());
    }

    public LoadoutResult saveLoadout(@Nonnull UUID uuid,
                                     @Nonnull String name,
                                     @Nullable String actor) {
        String normalizedName = normalizeLoadoutName(name);
        if (normalizedName == null) {
            return LoadoutResult.INVALID_NAME;
        }

        PlayerTagData data = getOrLoad(uuid);
        String equipped = data.getEquipped();
        if (equipped == null || equipped.isBlank() || !data.owns(equipped)) {
            return LoadoutResult.NO_EQUIPPED_TAG;
        }

        data.putLoadout(normalizedName, equipped.toLowerCase(Locale.ROOT));
        savePlayerData(uuid);
        TagAuditLogger.log("loadout_save", actor, uuid, null, equipped, "saved",
                Map.of("loadout", normalizedName));
        publishEvent(MysticNameTagsEventType.LOADOUT_SAVED,
                actor,
                uuid,
                null,
                equipped,
                "saved",
                Map.of("loadout", normalizedName));
        return LoadoutResult.SAVED;
    }

    public LoadoutResult saveLoadoutTag(@Nonnull UUID uuid,
                                        @Nonnull String name,
                                        @Nonnull String tagId,
                                        @Nullable String actor) {
        String normalizedName = normalizeLoadoutName(name);
        if (normalizedName == null) {
            return LoadoutResult.INVALID_NAME;
        }

        String keyId = normalizeTagId(tagId);
        if (keyId == null || getTag(keyId) == null) {
            return LoadoutResult.NOT_FOUND;
        }

        PlayerTagData data = getOrLoad(uuid);
        if (!data.owns(keyId)) {
            return LoadoutResult.NO_EQUIPPED_TAG;
        }

        data.putLoadout(normalizedName, keyId);
        savePlayerData(uuid);
        TagAuditLogger.log("loadout_save", actor, uuid, null, keyId, "saved",
                Map.of("loadout", normalizedName, "source", "ui_selected_tag"));
        publishEvent(MysticNameTagsEventType.LOADOUT_SAVED,
                actor,
                uuid,
                null,
                keyId,
                "saved",
                Map.of("loadout", normalizedName, "source", "ui_selected_tag"));
        return LoadoutResult.SAVED;
    }

    public LoadoutEquipResult equipLoadout(@Nonnull PlayerRef playerRef,
                                           @Nonnull UUID uuid,
                                           @Nonnull String name) {
        String normalizedName = normalizeLoadoutName(name);
        if (normalizedName == null) {
            return new LoadoutEquipResult(LoadoutResult.INVALID_NAME, TagPurchaseResult.NOT_FOUND, null);
        }

        PlayerTagData data = getOrLoad(uuid);
        String tagId = data.getLoadouts().get(normalizedName);
        if (tagId == null || tagId.isBlank()) {
            return new LoadoutEquipResult(LoadoutResult.NOT_FOUND, TagPurchaseResult.NOT_FOUND, null);
        }

        TagPurchaseResult result = purchaseAndEquip(playerRef, uuid, tagId);
        TagAuditLogger.log("loadout_equip",
                playerRef.getUsername(),
                uuid,
                playerRef.getUsername(),
                tagId,
                result.name(),
                Map.of("loadout", normalizedName));
        publishEvent(MysticNameTagsEventType.LOADOUT_EQUIPPED,
                playerRef.getUsername(),
                uuid,
                playerRef.getUsername(),
                tagId,
                result.name(),
                Map.of("loadout", normalizedName));
        return new LoadoutEquipResult(LoadoutResult.EQUIPPED, result, tagId);
    }

    public LoadoutResult deleteLoadout(@Nonnull UUID uuid,
                                       @Nonnull String name,
                                       @Nullable String actor) {
        String normalizedName = normalizeLoadoutName(name);
        if (normalizedName == null) {
            return LoadoutResult.INVALID_NAME;
        }

        PlayerTagData data = getOrLoad(uuid);
        boolean removed = data.removeLoadout(normalizedName);
        if (!removed) {
            return LoadoutResult.NOT_FOUND;
        }

        savePlayerData(uuid);
        TagAuditLogger.log("loadout_delete", actor, uuid, null, null, "deleted",
                Map.of("loadout", normalizedName));
        publishEvent(MysticNameTagsEventType.LOADOUT_DELETED,
                actor,
                uuid,
                null,
                null,
                "deleted",
                Map.of("loadout", normalizedName));
        return LoadoutResult.DELETED;
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
        clearCanUseCache(uuid);
        forceRefreshIfOnline(uuid);
        return true;
    }

    public boolean canUseTag(@Nonnull PlayerRef playerRef,
                             @Nullable UUID uuid,
                             @Nonnull TagDefinition def) {

        String rawId = def.getId();
        if (rawId == null || rawId.isEmpty()) {
            return false;
        }

        String keyId = rawId.toLowerCase(Locale.ROOT);

        if (uuid != null) {
            long now = System.currentTimeMillis();

            Map<String, CanUseCacheEntry> perPlayer =
                    canUseCache.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());

            CanUseCacheEntry cached = perPlayer.get(keyId);
            if (cached != null && !cached.isExpired(now)) {
                return cached.value;
            }

            boolean result = internalCanUseTagUnchecked(playerRef, uuid, def, keyId);
            perPlayer.put(keyId, new CanUseCacheEntry(result, now));
            return result;
        }

        return internalCanUseTagUnchecked(playerRef, uuid, def, keyId);
    }

    private boolean internalCanUseTagUnchecked(@Nonnull PlayerRef playerRef,
                                               @Nullable UUID uuid,
                                               @Nonnull TagDefinition def,
                                               @Nonnull String normalizedId) {

        if (!def.isCurrentlyAvailable() && (uuid == null || !ownsTag(uuid, normalizedId))) {
            return false;
        }

        boolean fullGate = Settings.get().isFullPermissionGateEnabled();
        boolean permissionGate = Settings.get().isPermissionGateEnabled();
        String perm = def.getPermission();

        boolean hasPerm = false;
        if (perm != null && !perm.isEmpty()) {
            try {
                hasPerm = integrations.hasPermission(playerRef, perm);
            } catch (Throwable ignored) {
                hasPerm = false;
            }
        }

        if (fullGate && perm != null && !perm.isEmpty() && !hasPerm) {
            return false;
        }

        if (uuid == null) {
            if (perm != null && !perm.isEmpty()) {
                if (permissionGate || fullGate) {
                    return hasPerm;
                }
                return hasPerm;
            }
            return meetsRequirementsForPreview(playerRef, def);
        }

        PlayerTagData data = getOrLoad(uuid);
        boolean owns = data.owns(normalizedId);

        if (permissionGate && perm != null && !perm.isEmpty() && !hasPerm) {
            return false;
        }

        if (!permissionGate) {
            if (perm != null && !perm.isEmpty()) {
                if (!owns && !hasPerm) {
                    return false;
                }
            } else if (!owns) {
                return false;
            }
        } else {
            if (perm == null || perm.isEmpty()) {
                if (!owns) {
                    return false;
                }
            }
        }

        return meetsRequirements(uuid, playerRef, def);
    }

    private boolean meetsRequirements(@Nonnull UUID uuid,
                                      @Nonnull PlayerRef playerRef,
                                      @Nonnull TagDefinition def) {

        List<String> requiredTags = def.getRequiredOwnedTags();
        if (!requiredTags.isEmpty()) {
            PlayerTagData data = getOrLoad(uuid);
            for (String req : requiredTags) {
                if (req == null || req.isBlank()) continue;
                if (!data.owns(req.toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
        }

        Integer reqMinutes = def.getRequiredPlaytimeMinutes();
        if (reqMinutes != null && reqMinutes > 0) {
            Integer playtime = integrations.getPlaytimeMinutes(uuid);
            if (playtime == null || playtime < reqMinutes) {
                return false;
            }
        }

        List<TagDefinition.StatRequirement> statReqs = def.getRequiredStats();
        if (!statReqs.isEmpty()) {
            for (TagDefinition.StatRequirement req : statReqs) {
                if (req == null || !req.isValid()) {
                    return false;
                }

                Integer current;
                try {
                    current = integrations.getStatValue(uuid, req.getKey());
                } catch (Throwable t) {
                    current = null;
                }

                Integer min = req.getMin();
                if (current == null || min == null || current < min) {
                    return false;
                }
            }
        }

        List<TagDefinition.PlaceholderRequirement> phReqs = def.getPlaceholderRequirements();
        if (phReqs != null && !phReqs.isEmpty()) {
            for (TagDefinition.PlaceholderRequirement req : phReqs) {
                if (req == null) continue;

                String placeholder = req.getPlaceholder();
                String op = req.getOperator();
                String expected = req.getValue();

                if (placeholder == null || op == null || expected == null) {
                    return false;
                }

                String actual = integrations.resolvePlaceholderRequirement(playerRef, placeholder, op, expected);
                if (!evaluatePlaceholderCondition(actual, op, expected)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean meetsRequirementsForPreview(@Nonnull PlayerRef playerRef,
                                                @Nonnull TagDefinition def) {

        List<TagDefinition.PlaceholderRequirement> phReqs = def.getPlaceholderRequirements();
        if (phReqs != null && !phReqs.isEmpty()) {
            for (TagDefinition.PlaceholderRequirement req : phReqs) {
                if (req == null) continue;

                String placeholder = req.getPlaceholder();
                String op = req.getOperator();
                String expected = req.getValue();

                if (placeholder == null || op == null || expected == null) {
                    return false;
                }

                String actual = integrations.resolvePlaceholderRequirement(playerRef, placeholder, op, expected);
                if (!evaluatePlaceholderCondition(actual, op, expected)) {
                    return false;
                }
            }
        }

        return true;
    }

    private TagPurchaseResult checkRequirements(@Nonnull UUID uuid,
                                                @Nonnull PlayerRef playerRef,
                                                @Nonnull TagDefinition def) {

        if (!meetsRequirements(uuid, playerRef, def)) {
            return TagPurchaseResult.REQUIREMENTS_NOT_MET;
        }

        if (def.hasItemRequirements()) {
            try {
                if (!integrations.hasItems(playerRef, def.getRequiredItems())) {
                    return TagPurchaseResult.REQUIREMENTS_NOT_MET;
                }
            } catch (Throwable t) {
                return TagPurchaseResult.REQUIREMENTS_NOT_MET;
            }
        }

        return null;
    }

    private boolean evaluatePlaceholderCondition(@Nullable String actual,
                                                 @Nonnull String operator,
                                                 @Nonnull String expected) {
        if (actual == null) {
            return false;
        }

        String op = operator.trim();
        String exp = expected.trim();
        String a = actual.trim();

        if (op.equalsIgnoreCase("true")) {
            return "true".equalsIgnoreCase(a);
        }
        if (op.equalsIgnoreCase("false")) {
            return "false".equalsIgnoreCase(a);
        }

        boolean actualIsBool = "true".equalsIgnoreCase(a) || "false".equalsIgnoreCase(a);
        boolean expectedIsBool = "true".equalsIgnoreCase(exp) || "false".equalsIgnoreCase(exp);

        if (actualIsBool && expectedIsBool) {
            boolean actualBool = Boolean.parseBoolean(a);
            boolean expectedBool = Boolean.parseBoolean(exp);

            switch (op) {
                case "==": return actualBool == expectedBool;
                case "!=": return actualBool != expectedBool;
                default: break;
            }
        }

        Double actualNum = tryParseDouble(a);
        Double expNum = tryParseDouble(exp);

        if (actualNum != null && expNum != null) {
            switch (op) {
                case "==": return Double.compare(actualNum, expNum) == 0;
                case "!=": return Double.compare(actualNum, expNum) != 0;
                case ">": return actualNum > expNum;
                case ">=": return actualNum >= expNum;
                case "<": return actualNum < expNum;
                case "<=": return actualNum <= expNum;
                default: break;
            }
        }

        switch (op) {
            case "==": return a.equalsIgnoreCase(exp);
            case "!=": return !a.equalsIgnoreCase(exp);
            case "contains": return a.toLowerCase(Locale.ROOT).contains(exp.toLowerCase(Locale.ROOT));
            default: return false;
        }
    }

    @Nullable
    private static Double tryParseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public TagPurchaseResult toggleTag(@Nonnull PlayerRef playerRef,
                                       @Nonnull UUID uuid,
                                       @Nonnull String id) {
        TagDefinition def = getTag(id);
        if (def == null) {
            return TagPurchaseResult.NOT_FOUND;
        }

        String rawId = def.getId();
        if (rawId == null || rawId.isEmpty()) {
            return TagPurchaseResult.NOT_FOUND;
        }
        String keyId = rawId.toLowerCase(Locale.ROOT);

        PlayerTagData data = getOrLoad(uuid);

        String equipped = data.getEquipped();
        if (equipped != null && equipped.equalsIgnoreCase(keyId)) {
            data.setEquipped(null);
            savePlayerData(uuid);
            clearCanUseCache(uuid);
            refreshIfOnline(uuid);
            TagAuditLogger.log("player_unequip",
                    playerRef.getUsername(),
                    uuid,
                    playerRef.getUsername(),
                    keyId,
                    TagPurchaseResult.UNEQUIPPED.name(),
                    null);
            publishEvent(MysticNameTagsEventType.TAG_UNEQUIPPED,
                    playerRef.getUsername(),
                    uuid,
                    playerRef.getUsername(),
                    keyId,
                    TagPurchaseResult.UNEQUIPPED.name(),
                    null);
            return TagPurchaseResult.UNEQUIPPED;
        }

        return purchaseAndEquip(playerRef, uuid, keyId);
    }

    public TagPurchaseResult purchaseAndEquip(@Nonnull PlayerRef playerRef,
                                              @Nonnull UUID uuid,
                                              @Nonnull String id) {
        TagDefinition def = getTag(id);
        if (def == null) {
            return TagPurchaseResult.NOT_FOUND;
        }

        String rawId = def.getId();
        if (rawId == null || rawId.isEmpty()) {
            return TagPurchaseResult.NOT_FOUND;
        }
        String keyId = rawId.toLowerCase(Locale.ROOT);

        PlayerTagData data = getOrLoad(uuid);

        if (!data.owns(keyId) && !def.isCurrentlyAvailable()) {
            return TagPurchaseResult.UNAVAILABLE;
        }

        boolean fullGate = Settings.get().isFullPermissionGateEnabled();
        boolean permissionGate = Settings.get().isPermissionGateEnabled();
        String perm = def.getPermission();

        if ((fullGate || permissionGate) && perm != null && !perm.isEmpty()) {
            try {
                if (!integrations.hasPermission(playerRef, perm)) {
                    return TagPurchaseResult.NO_PERMISSION;
                }
            } catch (Throwable ignored) {
                return TagPurchaseResult.NO_PERMISSION;
            }
        }

        TagPurchaseResult reqFail = checkRequirements(uuid, playerRef, def);
        if (reqFail != null) {
            return reqFail;
        }

        // Permission-based auto-unlock equips directly WITHOUT persisting
        // ownership, so removing the permission revokes the tag again.
        if (data.owns(keyId) || isAutoUnlockedByPermission(playerRef, def)) {
            data.setEquipped(keyId);
            savePlayerData(uuid);
            clearCanUseCache(uuid);
            refreshIfOnline(uuid);
            TagAuditLogger.log("player_equip",
                    playerRef.getUsername(),
                    uuid,
                    playerRef.getUsername(),
                    keyId,
                    TagPurchaseResult.EQUIPPED_ALREADY_OWNED.name(),
                    null);
            publishEvent(MysticNameTagsEventType.TAG_EQUIPPED,
                    playerRef.getUsername(),
                    uuid,
                    playerRef.getUsername(),
                    keyId,
                    TagPurchaseResult.EQUIPPED_ALREADY_OWNED.name(),
                    null);
            return TagPurchaseResult.EQUIPPED_ALREADY_OWNED;
        }

        if (!def.isPurchasable() || def.getPrice() <= 0) {
            if (!consumeItemsIfNeeded(playerRef, def)) {
                return TagPurchaseResult.TRANSACTION_FAILED;
            }

            data.addOwned(keyId);
            data.setEquipped(keyId);
            savePlayerData(uuid);

            runOnFirstUnlockCommands(def, playerRef);

            if (!Settings.get().isFullPermissionGateEnabled() && !Settings.get().isPermissionGateEnabled()) {
                maybeGrantPermission(uuid, perm);
            }

            clearCanUseCache(uuid);
            refreshIfOnline(uuid);
            TagAuditLogger.log("player_unlock",
                    playerRef.getUsername(),
                    uuid,
                    playerRef.getUsername(),
                    keyId,
                    TagPurchaseResult.UNLOCKED_FREE.name(),
                    Map.of("price", 0));
            publishEvent(MysticNameTagsEventType.TAG_UNLOCKED,
                    playerRef.getUsername(),
                    uuid,
                    playerRef.getUsername(),
                    keyId,
                    TagPurchaseResult.UNLOCKED_FREE.name(),
                    Map.of("price", "0"));
            return TagPurchaseResult.UNLOCKED_FREE;
        }

        if (!integrations.hasAnyEconomy()) {
            return TagPurchaseResult.NO_ECONOMY;
        }

        if (!integrations.hasBalance(playerRef, uuid, def.getPrice())) {
            return TagPurchaseResult.NOT_ENOUGH_MONEY;
        }

        if (!integrations.withdraw(playerRef, uuid, def.getPrice())) {
            return TagPurchaseResult.TRANSACTION_FAILED;
        }

        if (!consumeItemsIfNeeded(playerRef, def)) {
            return TagPurchaseResult.TRANSACTION_FAILED;
        }

        data.addOwned(keyId);
        data.setEquipped(keyId);
        savePlayerData(uuid);

        runOnFirstUnlockCommands(def, playerRef);

        if (!Settings.get().isFullPermissionGateEnabled() && !Settings.get().isPermissionGateEnabled()) {
            maybeGrantPermission(uuid, perm);
        }

        clearCanUseCache(uuid);
        refreshIfOnline(uuid);
        TagAuditLogger.log("player_purchase",
                playerRef.getUsername(),
                uuid,
                playerRef.getUsername(),
                keyId,
                TagPurchaseResult.UNLOCKED_PAID.name(),
                Map.of("price", def.getPrice()));
        publishEvent(MysticNameTagsEventType.TAG_PURCHASED,
                playerRef.getUsername(),
                uuid,
                playerRef.getUsername(),
                keyId,
                TagPurchaseResult.UNLOCKED_PAID.name(),
                Map.of("price", String.valueOf(def.getPrice())));
        return TagPurchaseResult.UNLOCKED_PAID;
    }

    /**
     * Build the final colored nameplate text for previews/chat/UI.
     */
    public String buildNameplate(@Nonnull PlayerRef playerRef,
                                 @Nonnull String baseName,
                                 @Nullable UUID uuid) {
        NameplateTextResolver.Context ctx = buildNameplateContext(playerRef, baseName, uuid);
        return NameplateTextResolver.resolve(ctx).getColored();
    }

    public String getColoredFullNameplate(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        String baseName = playerRef.getUsername();
        NameplateTextResolver.Context ctx = buildNameplateContext(playerRef, baseName, uuid);
        return NameplateTextResolver.resolve(ctx).getColored();
    }

    public String getColoredFullNameplate(UUID uuid, String baseName) {
        PlayerRef ref = onlinePlayers.get(uuid);
        NameplateTextResolver.Context ctx = buildNameplateContext(ref, baseName, uuid);
        return NameplateTextResolver.resolve(ctx).getColored();
    }

    public String getPlainFullNameplate(UUID uuid, String baseName) {
        PlayerRef ref = onlinePlayers.get(uuid);
        NameplateTextResolver.Context ctx = buildNameplateContext(ref, baseName, uuid);
        return NameplateTextResolver.resolve(ctx).getPlain();
    }

    public String buildPlainNameplate(@Nonnull PlayerRef playerRef,
                                      @Nonnull String baseName,
                                      @Nullable UUID uuid) {
        NameplateTextResolver.Context ctx = buildNameplateContext(playerRef, baseName, uuid);
        return NameplateTextResolver.resolve(ctx).getPlain();
    }

    public void refreshNameplate(@Nonnull PlayerRef playerRef,
                                 @Nonnull World world) {

        UUID uuid = playerRef.getUuid();
        String baseName = playerRef.getUsername();
        if (baseName == null || baseName.isBlank()) {
            baseName = "Player";
        }

        Settings settings = Settings.get();

        if (!settings.isNameplatesEnabled()) {
            String fallbackName = baseName;

            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    Ref<EntityStore> ref = playerRef.getReference();
                    if (ref == null || !ref.isValid()) {
                        lastNameplateText.remove(uuid);
                        return;
                    }

                    NameplateManager.get().restore(uuid, store, ref, fallbackName);
                    GlyphNameplateManager.get().remove(uuid, world, store);
                    lastNameplateText.remove(uuid);
                } catch (Throwable ignored) {
                    lastNameplateText.remove(uuid);
                }
            });

            return;
        }

        NameplateTextResolver.Context ctx = buildNameplateContext(playerRef, baseName, uuid);
        NameplateTextResolver.ResolvedNameplateText resolved = NameplateTextResolver.resolve(ctx);

        String resolvedColored = resolved.getColored();
        String resolvedGlyphColored = resolved.getGlyphColored();
        String plainFallback = resolved.getNativePlain();

        boolean glyphEnabled = settings.isExperimentalGlyphNameplatesEnabled();
        EquippedBanner banner = glyphEnabled ? resolveActiveBanner(uuid) : null;
        String compareKey = bannerCompareKey(banner, glyphEnabled ? resolvedGlyphColored : plainFallback);

        String finalBaseName = baseName;
        String finalResolvedColored = banner != null
                ? bannerFormatText(playerRef, baseName, uuid, settings)
                : (glyphEnabled ? resolvedGlyphColored : resolvedColored);
        String finalPlainFallback = plainFallback;

        world.execute(() -> applyNameplateNow(
                playerRef,
                world,
                uuid,
                compareKey,
                finalBaseName,
                finalResolvedColored,
                finalPlainFallback,
                glyphEnabled,
                banner,
                0
        ));
    }

    /**
     * Folds the active banner into the nameplate change-detection key so swapping between a banner
     * tag and a text tag — or between two banners — always triggers a rebuild.
     */
    /**
     * The player's full nameplate format with the {@code {tag}} token swapped for a marker, so the
     * banner renders on that exact line and every other line of the format still renders as text.
     *
     * <p>Returns empty when {@code bannerKeepNameLine} is off, which makes the banner the whole
     * nameplate.</p>
     */
    @Nonnull
    private String bannerFormatText(@Nonnull PlayerRef playerRef,
                                    @Nonnull String baseName,
                                    @Nullable UUID uuid,
                                    @Nonnull Settings settings) {
        if (!settings.isBannerKeepNameLine()) {
            return "";
        }

        NameplateTextResolver.Context ctx = buildNameplateContext(
                playerRef, baseName, uuid, GlyphNameplateManager.BANNER_SLOT_TOKEN);
        return NameplateTextResolver.resolve(ctx).getGlyphColored();
    }

    @Nonnull
    private static String bannerCompareKey(@Nullable EquippedBanner banner, @Nonnull String textKey) {
        if (banner == null) {
            return textKey;
        }
        return "[[banner]]:" + banner.info().renderKey() + "@" + banner.scale() + "|" + textKey;
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
        UNAVAILABLE,
        REQUIREMENTS_NOT_MET,
        TRANSACTION_FAILED
    }

    public enum FavoriteResult {
        ADDED,
        REMOVED,
        NOT_FOUND,
        NOT_OWNED
    }

    public enum LoadoutResult {
        SAVED,
        EQUIPPED,
        DELETED,
        NOT_FOUND,
        INVALID_NAME,
        NO_EQUIPPED_TAG
    }

    public static final class LoadoutEquipResult {
        private final LoadoutResult loadoutResult;
        private final TagPurchaseResult tagResult;
        private final String tagId;

        private LoadoutEquipResult(@Nonnull LoadoutResult loadoutResult,
                                   @Nonnull TagPurchaseResult tagResult,
                                   @Nullable String tagId) {
            this.loadoutResult = loadoutResult;
            this.tagResult = tagResult;
            this.tagId = tagId;
        }

        @Nonnull
        public LoadoutResult getLoadoutResult() {
            return loadoutResult;
        }

        @Nonnull
        public TagPurchaseResult getTagResult() {
            return tagResult;
        }

        @Nullable
        public String getTagId() {
            return tagId;
        }
    }

    public enum TagPackImportMode {
        APPEND,
        UPSERT,
        REPLACE;

        @Nonnull
        public static TagPackImportMode from(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return APPEND;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "upsert", "merge", "update" -> UPSERT;
                case "replace", "overwrite" -> REPLACE;
                default -> APPEND;
            };
        }
    }

    public enum TagEditStatus {
        CREATED,
        UPDATED,
        DELETED,
        NOT_FOUND,
        INVALID_ID,
        INVALID_PRICE,
        FAILED
    }

    public static final class TagEditResult {
        private final TagEditStatus status;
        private final TagDefinition tag;
        private final String message;

        private TagEditResult(@Nonnull TagEditStatus status,
                              @Nullable TagDefinition tag,
                              @Nullable String message) {
            this.status = status;
            this.tag = tag;
            this.message = message;
        }

        @Nonnull
        public static TagEditResult of(@Nonnull TagEditStatus status,
                                       @Nullable TagDefinition tag,
                                       @Nullable String message) {
            return new TagEditResult(status, tag, message);
        }

        @Nonnull
        public TagEditStatus getStatus() {
            return status;
        }

        @Nullable
        public TagDefinition getTag() {
            return tag;
        }

        @Nullable
        public String getMessage() {
            return message;
        }

        public boolean isSuccess() {
            return status == TagEditStatus.CREATED
                    || status == TagEditStatus.UPDATED
                    || status == TagEditStatus.DELETED;
        }
    }

    public static final class TagPackImportResult {
        private final boolean success;
        private final File file;
        private final int added;
        private final int replaced;
        private final int skipped;
        private final String error;

        private TagPackImportResult(boolean success,
                                    @Nullable File file,
                                    int added,
                                    int replaced,
                                    int skipped,
                                    @Nullable String error) {
            this.success = success;
            this.file = file;
            this.added = added;
            this.replaced = replaced;
            this.skipped = skipped;
            this.error = error;
        }

        @Nonnull
        private static TagPackImportResult invalid(@Nonnull String error) {
            return new TagPackImportResult(false, null, 0, 0, 0, error);
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public File getFile() {
            return file;
        }

        public int getAdded() {
            return added;
        }

        public int getReplaced() {
            return replaced;
        }

        public int getSkipped() {
            return skipped;
        }

        @Nullable
        public String getError() {
            return error;
        }
    }

    public static final class TagPackExportResult {
        private final boolean success;
        private final File file;
        private final int exported;
        private final String error;

        private TagPackExportResult(boolean success,
                                    @Nullable File file,
                                    int exported,
                                    @Nullable String error) {
            this.success = success;
            this.file = file;
            this.exported = exported;
            this.error = error;
        }

        @Nonnull
        private static TagPackExportResult invalid(@Nonnull String error) {
            return new TagPackExportResult(false, null, 0, error);
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public File getFile() {
            return file;
        }

        public int getExported() {
            return exported;
        }

        @Nullable
        public String getError() {
            return error;
        }
    }

    public IntegrationManager getIntegrations() {
        return integrations;
    }

    public void forgetNameplate(@Nonnull UUID uuid) {
        lastNameplateText.remove(uuid);
    }

    // ---- Online tracking ----

    public void trackOnlinePlayer(@Nonnull PlayerRef ref, @Nonnull World world) {
        UUID uuid = ref.getUuid();
        onlinePlayers.put(uuid, ref);
        onlineWorlds.put(uuid, world);
    }

    @Nonnull
    public Set<UUID> getTrackedOnlinePlayerIds() {
        return new HashSet<>(onlinePlayers.keySet());
    }

    public void untrackOnlinePlayer(@Nonnull UUID uuid) {
        onlinePlayers.remove(uuid);
        onlineWorlds.remove(uuid);
        forgetNameplate(uuid);
        clearCanUseCache(uuid);

        NameplateManager.get().forget(uuid);

        // Do not call GlyphNameplateManager.forget(uuid) here.
        // Disconnect flow should remove glyphs first on the world thread.
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
        return getLegacyActiveTag(uuid);
    }

    public String getLegacyActiveTag(@Nonnull UUID uuid) {
        TagDefinition def = resolveActiveOrDefaultTag(uuid);
        if (def == null) {
            return "";
        }

        String display = def.getDisplay();
        if (display == null || display.isEmpty()) {
            return "";
        }

        // Expand hex for chat/placeholder consumers so legacy parsers do not
        // partially consume compact hex like "&#4f5c63" as "&4" + literal text.
        return ColorFormatter.colorizeForChat(display);
    }

    public String getMiniMessageActiveTag(@Nonnull UUID uuid) {
        TagDefinition def = resolveActiveOrDefaultTag(uuid);
        if (def == null) {
            return "";
        }

        String display = def.getDisplay();
        if (display == null || display.isEmpty()) {
            return "";
        }

        return ColorFormatter.toMiniMessage(display);
    }

    public String getNameplateActiveTag(@Nonnull UUID uuid) {
        TagDefinition def = resolveActiveOrDefaultTag(uuid);
        if (def == null) {
            return "";
        }

        String display = def.getDisplay();
        if (display == null || display.isEmpty()) {
            return "";
        }

        return ColorFormatter.colorizeForNameplate(display);
    }

    public String getPlainActiveTag(@Nonnull UUID uuid) {
        TagDefinition def = resolveActiveOrDefaultTag(uuid);
        if (def == null) {
            return "";
        }
        return ColorFormatter.stripFormatting(def.getDisplay());
    }

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
        int end = Math.min(start + pageSize, total);

        return tagList.subList(start, end);
    }

    private void maybeGrantPermission(@Nonnull UUID uuid, @Nullable String perm) {
        if (perm == null || perm.isEmpty()) {
            return;
        }
        try {
            integrations.grantPermission(uuid, perm);
        } catch (Throwable ignored) {
        }
    }

    private boolean consumeItemsIfNeeded(@Nonnull PlayerRef playerRef,
                                         @Nonnull TagDefinition def) {

        List<TagDefinition.ItemRequirement> itemReqs = def.getRequiredItems();
        if (itemReqs.isEmpty()) {
            return true;
        }

        try {
            return integrations.consumeItems(playerRef, itemReqs);
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to consume items for tag purchase: " + def.getId());
            return false;
        }
    }

    @Nonnull
    public TagPackImportResult importTagPack(@Nonnull String requestedPack,
                                             @Nonnull TagPackImportMode mode,
                                             @Nullable String actor) {
        String packName = requestedPack.trim();
        if (packName.isEmpty()
                || packName.contains("..")
                || packName.contains("/")
                || packName.contains("\\")) {
            return TagPackImportResult.invalid("Pack name must be a file inside the tagpacks folder.");
        }

        if (!packName.endsWith(".json")) {
            packName += ".json";
        }

        File dataFolder = MysticNameTagsPlugin.getInstance().getDataDirectory().toFile();
        File packFolder = new File(dataFolder, "tagpacks");
        packFolder.mkdirs();

        Path root = packFolder.toPath().toAbsolutePath().normalize();
        Path packPath = root.resolve(packName).normalize();
        if (!packPath.startsWith(root)) {
            return TagPackImportResult.invalid("Pack path must stay inside the tagpacks folder.");
        }

        File packFile = packPath.toFile();
        if (!packFile.isFile()) {
            return TagPackImportResult.invalid("Pack not found: " + packFile.getAbsolutePath());
        }

        try {
            List<TagDefinition> pack = readTagList(packFile);
            if (pack.isEmpty()) {
                return TagPackImportResult.invalid("Pack contains no tags.");
            }

            List<TagDefinition> existing = readTagList(configFile);
            Map<String, TagDefinition> merged = new LinkedHashMap<>();

            if (mode != TagPackImportMode.REPLACE) {
                for (TagDefinition def : existing) {
                    String key = normalizeDefinitionId(def);
                    if (key != null) {
                        merged.put(key, def);
                    }
                }
            }

            int added = 0;
            int replaced = 0;
            int skipped = 0;

            for (TagDefinition def : pack) {
                String key = normalizeDefinitionId(def);
                if (key == null) {
                    skipped++;
                    continue;
                }

                if (merged.containsKey(key)) {
                    if (mode == TagPackImportMode.APPEND) {
                        skipped++;
                        continue;
                    }
                    replaced++;
                } else {
                    added++;
                }

                merged.put(key, def);
            }

            List<TagDefinition> out = new ArrayList<>(merged.values());
            backupTagsFile();
            saveConfig(out);
            loadConfig();
            clearCanUseCache();
            refreshAllOnlineNameplates();

            TagAuditLogger.log("tagpack_import", actor, null, null, null, "imported",
                    Map.of(
                            "pack", packName,
                            "mode", mode.name().toLowerCase(Locale.ROOT),
                            "added", added,
                            "replaced", replaced,
                            "skipped", skipped
                    ));
            publishEvent(MysticNameTagsEventType.TAG_PACK_IMPORTED,
                    actor,
                    null,
                    null,
                    null,
                    "imported",
                    Map.of(
                            "pack", packName,
                            "mode", mode.name().toLowerCase(Locale.ROOT),
                            "added", String.valueOf(added),
                            "replaced", String.valueOf(replaced),
                            "skipped", String.valueOf(skipped)
                    ));

            return new TagPackImportResult(true, packFile, added, replaced, skipped, null);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to import tag pack " + packName);
            return TagPackImportResult.invalid(e.getMessage() == null ? "Import failed." : e.getMessage());
        }
    }

    /**
     * Export the currently loaded tag definitions to a JSON pack inside the
     * tagpacks folder. The written file is directly compatible with
     * {@link #importTagPack(String, TagPackImportMode, String)}.
     *
     * @param requestedPack file name inside the tagpacks folder (".json" is
     *                      appended when missing; path separators are rejected)
     * @param category      optional category filter; when non-blank only tags
     *                      of that category (case-insensitive) are exported
     * @param actor         name recorded in the audit log and API event
     */
    @Nonnull
    public TagPackExportResult exportTagPack(@Nonnull String requestedPack,
                                             @Nullable String category,
                                             @Nullable String actor) {
        String packName = requestedPack.trim();
        if (packName.isEmpty()
                || packName.contains("..")
                || packName.contains("/")
                || packName.contains("\\")) {
            return TagPackExportResult.invalid("Pack name must be a file inside the tagpacks folder.");
        }

        if (!packName.endsWith(".json")) {
            packName += ".json";
        }

        File dataFolder = MysticNameTagsPlugin.getInstance().getDataDirectory().toFile();
        File packFolder = new File(dataFolder, "tagpacks");
        packFolder.mkdirs();

        Path root = packFolder.toPath().toAbsolutePath().normalize();
        Path packPath = root.resolve(packName).normalize();
        if (!packPath.startsWith(root)) {
            return TagPackExportResult.invalid("Pack path must stay inside the tagpacks folder.");
        }

        String categoryFilter = (category == null || category.isBlank()) ? null : category.trim();

        List<TagDefinition> out = new ArrayList<>();
        for (TagDefinition def : tagList) {
            if (def == null) continue;
            if (categoryFilter != null) {
                String defCat = def.getCategory();
                if (defCat == null || !defCat.equalsIgnoreCase(categoryFilter)) continue;
            }
            out.add(def);
        }

        if (out.isEmpty()) {
            return TagPackExportResult.invalid(categoryFilter == null
                    ? "There are no loaded tags to export."
                    : "No tags found in category: " + categoryFilter);
        }

        File packFile = packPath.toFile();
        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(packFile), StandardCharsets.UTF_8)) {
            GSON.toJson(out, writer);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to export tag pack " + packName);
            return TagPackExportResult.invalid(e.getMessage() == null ? "Export failed." : e.getMessage());
        }

        TagAuditLogger.log("tagpack_export", actor, null, null, null, "exported",
                Map.of(
                        "pack", packName,
                        "category", categoryFilter == null ? "all" : categoryFilter,
                        "count", out.size()
                ));
        publishEvent(MysticNameTagsEventType.TAG_PACK_EXPORTED,
                actor,
                null,
                null,
                null,
                "exported",
                Map.of(
                        "pack", packName,
                        "category", categoryFilter == null ? "all" : categoryFilter,
                        "count", String.valueOf(out.size())
                ));

        return new TagPackExportResult(true, packFile, out.size(), null);
    }

    @Nonnull
    private List<TagDefinition> readTagList(@Nonnull File file) throws Exception {
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (InputStreamReader reader =
                     new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<TagDefinition>>() {}.getType();
            List<TagDefinition> list = GSON.fromJson(reader, listType);
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        }
    }

    private void backupTagsFile() throws Exception {
        if (configFile == null || !configFile.exists()) {
            return;
        }
        File backupFolder = new File(configFile.getParentFile(), "backups");
        backupFolder.mkdirs();
        File backup = new File(backupFolder, "tags-" + System.currentTimeMillis() + ".json");
        Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Nullable
    private static String normalizeDefinitionId(@Nullable TagDefinition def) {
        if (def == null || def.getId() == null || def.getId().isBlank()) {
            return null;
        }
        return def.getId().trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static String normalizeTagId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static String cleanNullable(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Nonnull
    private static String cleanOrDefault(@Nullable String value,
                                         @Nonnull String fallback) {
        String cleaned = cleanNullable(value);
        return cleaned == null ? fallback : cleaned;
    }

    @Nonnull
    private static String prettifyIdForConfig(@Nonnull String id) {
        String[] parts = id.replace(':', '_').replace('.', '_').replace('-', '_').split("_+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.length() == 0 ? id : out.toString();
    }

    @Nullable
    private static String normalizeLoadoutName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{1,24}")) {
            return null;
        }
        return normalized;
    }

    // ============================================================
    // Admin helpers
    // ============================================================

    public boolean adminGiveTag(@Nonnull UUID uuid,
                                @Nonnull String id,
                                boolean equip) {
        return adminGiveTag(uuid, id, equip, "admin");
    }

    public boolean adminGiveTag(@Nonnull UUID uuid,
                                @Nonnull String id,
                                boolean equip,
                                @Nullable String actor) {

        TagDefinition def = getTag(id);
        if (def == null) {
            return false;
        }

        String keyId = def.getId().toLowerCase(Locale.ROOT);

        PlayerTagData data = getOrLoad(uuid);
        data.addOwned(keyId);

        if (equip) {
            data.setEquipped(keyId);
        }

        savePlayerData(uuid);
        clearCanUseCache(uuid);

        forceRefreshIfOnline(uuid);

        TagAuditLogger.log("admin_give", actor, uuid, null, keyId, "granted",
                Map.of("equip", equip));
        publishEvent(MysticNameTagsEventType.PLAYER_TAG_GRANTED,
                actor,
                uuid,
                null,
                keyId,
                "granted",
                Map.of("equip", String.valueOf(equip)));

        return true;
    }

    public boolean adminRemoveTag(@Nonnull UUID uuid,
                                  @Nonnull String id) {
        return adminRemoveTag(uuid, id, "admin");
    }

    public boolean adminRemoveTag(@Nonnull UUID uuid,
                                  @Nonnull String id,
                                  @Nullable String actor) {

        PlayerTagData data = getOrLoad(uuid);
        String keyId = id.toLowerCase(Locale.ROOT);

        boolean removed = data.getOwned().remove(keyId);
        if (!removed) {
            return false;
        }

        data.removeFavorite(keyId);
        data.getLoadouts().entrySet().removeIf(entry -> keyId.equalsIgnoreCase(entry.getValue()));

        if (keyId.equalsIgnoreCase(data.getEquipped())) {
            data.setEquipped(null);
        }

        savePlayerData(uuid);
        clearCanUseCache(uuid);

        forceRefreshIfOnline(uuid);

        TagAuditLogger.log("admin_remove", actor, uuid, null, keyId, "removed", null);
        publishEvent(MysticNameTagsEventType.PLAYER_TAG_REMOVED,
                actor,
                uuid,
                null,
                keyId,
                "removed",
                null);

        return true;
    }

    public boolean adminResetTags(@Nonnull UUID uuid) {
        return adminResetTags(uuid, "admin");
    }

    public boolean adminResetTags(@Nonnull UUID uuid, @Nullable String actor) {
        PlayerTagData data = getOrLoad(uuid);
        if (data.getOwned().isEmpty()
                && data.getFavorites().isEmpty()
                && data.getLoadouts().isEmpty()
                && data.getEquipped() == null) {
            return false;
        }

        data.getOwned().clear();
        data.getFavorites().clear();
        data.getLoadouts().clear();
        data.setEquipped(null);

        savePlayerData(uuid);
        clearCanUseCache(uuid);

        try {
            playerTagStore.delete(uuid);
        } catch (Throwable ignored) {
        }
        publishPlayerDataChanged(uuid);

        forceRefreshIfOnline(uuid);

        TagAuditLogger.log("admin_reset", actor, uuid, null, null, "reset", null);
        publishEvent(MysticNameTagsEventType.PLAYER_TAGS_RESET,
                actor,
                uuid,
                null,
                null,
                "reset",
                null);

        return true;
    }

    public boolean adminResetTagsAndPermissions(@Nonnull UUID uuid) {
        return adminResetTagsAndPermissions(uuid, "admin");
    }

    public boolean adminResetTagsAndPermissions(@Nonnull UUID uuid, @Nullable String actor) {
        boolean changed = adminResetTags(uuid, actor);
        if (!changed) {
            return false;
        }

        for (TagDefinition def : tags.values()) {
            String perm = def.getPermission();
            if (perm == null || perm.isEmpty()) {
                continue;
            }
            try {
                integrations.revokePermission(uuid, perm);
            } catch (Throwable ignored) {
            }
        }

        try {
            playerTagStore.delete(uuid);
        } catch (Throwable ignored) {
        }
        publishPlayerDataChanged(uuid);

        TagAuditLogger.log("admin_reset_permissions", actor, uuid, null, null, "reset", null);

        return true;
    }

    @Nullable
    public TagDefinition resolveActiveOrDefaultTag(@Nonnull UUID uuid) {
        TagDefinition equipped = getEquipped(uuid);
        if (equipped != null) return equipped;

        Settings s = Settings.get();
        if (!s.isDefaultTagEnabled()) return null;

        String id = s.getDefaultTagId();
        if (id == null || id.trim().isEmpty()) return null;

        return getTag(id.trim());
    }

    /**
     * Banner art for the tag a player is currently showing, or {@code null} if that tag is
     * text-only, banners are disabled, or the configured image is missing.
     */
    @Nullable
    public EquippedBanner resolveActiveBanner(@Nonnull UUID uuid) {
        if (!Settings.get().isBannersEnabled()) {
            return null;
        }

        // Licensed feature. Unlicensed servers keep the tag, it just renders as text.
        if (!MysticNameTagsLicense.bannersLicensed()) {
            return null;
        }

        TagDefinition active = resolveActiveOrDefaultTag(uuid);
        if (active == null || !active.hasBanner()) {
            return null;
        }

        BannerAssetManager banners = BannerAssetManager.get();
        if (banners == null) {
            return null;
        }

        BannerInfo info = banners.find(active.getBanner());
        if (info == null) {
            if (loggedMissingBanners.add(active.getId())) {
                LOGGER.at(Level.WARNING).log("[MysticNameTags] Tag '" + active.getId()
                        + "' references banner '" + active.getBanner()
                        + "' but no matching PNG was found in the images folder. Using its text display.");
            }
            return null;
        }

        return new EquippedBanner(info, active.getBannerScale());
    }

    /** A resolved banner plus the per-tag size multiplier to render it at. */
    public record EquippedBanner(@Nonnull BannerInfo info, double scale) {
    }

    private void runOnFirstUnlockCommands(@Nonnull TagDefinition def, @Nonnull PlayerRef playerRef) {
        List<String> cmds = def.getOnUnlockCommands();
        if (cmds.isEmpty()) return;

        UUID uuid = playerRef.getUuid();
        World world = onlineWorlds.get(uuid);

        Runnable task = () -> {
            String playerName = playerRef.getUsername();

            for (String raw : cmds) {
                if (raw == null || raw.isBlank()) continue;
                String cmd = raw.replace("<player>", playerName);
                cmd = ColorFormatter.translateAlternateColorCodes('§', cmd);
                ConsoleCommandRunner.dispatchConsole(cmd);
            }
        };

        if (world != null) {
            world.execute(task);
        } else {
            task.run();
        }
    }

    private void forceRefreshIfOnline(@Nonnull UUID uuid) {
        PlayerRef ref = onlinePlayers.get(uuid);
        World world = onlineWorlds.get(uuid);
        if (ref != null && world != null) {
            try {
                forceRefreshNameplate(ref, world);
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Failed to force refresh nameplate after change for " + uuid);
            }
        }
    }

    public void onExternalNameplateDataChanged(@Nonnull UUID uuid) {
        clearCanUseCache(uuid);
        forgetNameplate(uuid);

        try {
            integrations.invalidateEndlessLevelingNameplate(uuid);
        } catch (Throwable ignored) {
        }

        PlayerRef ref = onlinePlayers.get(uuid);
        World world = onlineWorlds.get(uuid);
        if (ref != null && world != null) {
            refreshNameplate(ref, world);
        }
    }

    private void refreshIfOnline(@Nonnull UUID uuid) {
        forceRefreshIfOnline(uuid);
    }

    @Nonnull
    public List<TagDefinition> getOwnedTags(@Nonnull UUID uuid) {
        PlayerTagData data = getOrLoad(uuid);
        if (data == null) {
            return Collections.emptyList();
        }

        List<TagDefinition> owned = new ArrayList<>();
        for (TagDefinition def : tags.values()) {
            if (def == null) continue;
            String id = def.getId();
            if (id == null || id.isBlank()) continue;

            if (data.owns(id.toLowerCase(Locale.ROOT))) {
                owned.add(def);
            }
        }
        return owned;
    }

    private void applyNameplateNow(@Nonnull PlayerRef playerRef,
                                   @Nonnull World world,
                                   @Nonnull UUID uuid,
                                   @Nonnull String compareKey,
                                   @Nonnull String baseName,
                                   @Nonnull String resolvedColored,
                                   @Nonnull String plainFallback,
                                   boolean glyphEnabled,
                                   @Nullable EquippedBanner banner,
                                   int attempt) {
        try {
            EntityStore entityStore = world.getEntityStore();
            Store<EntityStore> store = entityStore.getStore();

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                if (attempt < 3) {
                    world.execute(() -> applyNameplateNow(
                            playerRef,
                            world,
                            uuid,
                            compareKey,
                            baseName,
                            resolvedColored,
                            plainFallback,
                            glyphEnabled,
                            banner,
                            attempt + 1
                    ));
                } else {
                    lastNameplateText.remove(uuid);
                }
                return;
            }

            String previous = lastNameplateText.get(uuid);
            if (previous != null && previous.equals(compareKey)) {
                if (!glyphEnabled) {
                    return;
                }

                if (GlyphNameplateManager.get().hasLiveRender(uuid)) {
                    GlyphNameplateManager.get().followOnly(world, store, ref, uuid);
                    return;
                }
            }

            if (banner != null && glyphEnabled) {
                NameplateManager.get().apply(uuid, store, ref, " ");
                GlyphNameplateManager.get().applyBanner(
                        uuid, world, store, ref, banner.info(), banner.scale(), resolvedColored);
            } else if (glyphEnabled) {
                NameplateManager.get().apply(uuid, store, ref, " ");
                GlyphNameplateManager.get().apply(uuid, world, store, ref, resolvedColored);
            } else {
                NameplateManager.get().apply(uuid, store, ref, plainFallback);
                GlyphNameplateManager.get().remove(uuid, world, store);
            }

            lastNameplateText.put(uuid, compareKey);

            if (Settings.get().isEndlessLevelingNameplatesEnabled()) {
                integrations.invalidateEndlessLevelingNameplate(uuid);
            }

        } catch (Throwable e) {
            if (attempt < 3) {
                world.execute(() -> applyNameplateNow(
                        playerRef,
                        world,
                        uuid,
                        compareKey,
                        baseName,
                        resolvedColored,
                        plainFallback,
                        glyphEnabled,
                        banner,
                        attempt + 1
                ));
                return;
            }

            lastNameplateText.remove(uuid);
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to refresh nameplate for %s", baseName);
        }
    }

    public void forceRefreshNameplate(@Nonnull PlayerRef playerRef,
                                      @Nonnull World world) {

        UUID uuid = playerRef.getUuid();
        String baseName = playerRef.getUsername();
        if (baseName == null || baseName.isBlank()) {
            baseName = "Player";
        }

        lastNameplateText.remove(uuid);

        Settings settings = Settings.get();

        NameplateTextResolver.Context ctx = buildNameplateContext(playerRef, baseName, uuid);
        NameplateTextResolver.ResolvedNameplateText resolved = NameplateTextResolver.resolve(ctx);

        String resolvedColored = resolved.getColored();
        String resolvedGlyphColored = resolved.getGlyphColored();
        String plainFallback = resolved.getNativePlain();

        boolean glyphEnabled = settings.isExperimentalGlyphNameplatesEnabled();
        EquippedBanner banner = glyphEnabled ? resolveActiveBanner(uuid) : null;
        String compareKey = bannerCompareKey(banner, glyphEnabled ? resolvedGlyphColored : plainFallback);

        String finalBaseName = baseName;
        String finalResolvedColored = banner != null
                ? bannerFormatText(playerRef, baseName, uuid, settings)
                : (glyphEnabled ? resolvedGlyphColored : resolvedColored);
        String finalPlainFallback = plainFallback;

        world.execute(() -> forceApplyNameplateNow(
                playerRef,
                world,
                uuid,
                compareKey,
                finalBaseName,
                finalResolvedColored,
                finalPlainFallback,
                glyphEnabled,
                banner,
                0
        ));
    }

    private void forceApplyNameplateNow(@Nonnull PlayerRef playerRef,
                                        @Nonnull World world,
                                        @Nonnull UUID uuid,
                                        @Nonnull String compareKey,
                                        @Nonnull String baseName,
                                        @Nonnull String resolvedColored,
                                        @Nonnull String plainFallback,
                                        boolean glyphEnabled,
                                        @Nullable EquippedBanner banner,
                                        int attempt) {
        try {
            EntityStore entityStore = world.getEntityStore();
            Store<EntityStore> store = entityStore.getStore();

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                if (attempt < 3) {
                    world.execute(() -> forceApplyNameplateNow(
                            playerRef,
                            world,
                            uuid,
                            compareKey,
                            baseName,
                            resolvedColored,
                            plainFallback,
                            glyphEnabled,
                            banner,
                            attempt + 1
                    ));
                } else {
                    lastNameplateText.remove(uuid);
                }
                return;
            }

            if (banner != null && glyphEnabled) {
                NameplateManager.get().apply(uuid, store, ref, " ");
                GlyphNameplateManager.get().applyBanner(
                        uuid, world, store, ref, banner.info(), banner.scale(), resolvedColored);
            } else if (glyphEnabled) {
                NameplateManager.get().apply(uuid, store, ref, " ");
                GlyphNameplateManager.get().apply(uuid, world, store, ref, resolvedColored);
            } else {
                GlyphNameplateManager.get().remove(uuid, world, store);
                NameplateManager.get().apply(uuid, store, ref, plainFallback);
            }

            lastNameplateText.put(uuid, compareKey);

            if (Settings.get().isEndlessLevelingNameplatesEnabled()) {
                integrations.invalidateEndlessLevelingNameplate(uuid);
            }

        } catch (Throwable e) {
            if (attempt < 3) {
                world.execute(() -> forceApplyNameplateNow(
                        playerRef,
                        world,
                        uuid,
                        compareKey,
                        baseName,
                        resolvedColored,
                        plainFallback,
                        glyphEnabled,
                        banner,
                        attempt + 1
                ));
                return;
            }

            lastNameplateText.remove(uuid);
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to force refresh nameplate for %s", baseName);
        }
    }

    // ============================================================
    // Unified nameplate context building
    // ============================================================

    @Nonnull
    private NameplateTextResolver.Context buildNameplateContext(@Nullable PlayerRef playerRef,
                                                                @Nullable String baseName,
                                                                @Nullable UUID uuid) {
        return buildNameplateContext(playerRef, baseName, uuid, (String) null);
    }

    /**
     * @param tagOverride replaces the {@code {tag}} token when non-null. Used to drop a banner
     *                    marker into the format so the renderer knows which line the artwork
     *                    belongs on, instead of printing the tag's text display there.
     */
    @Nonnull
    private NameplateTextResolver.Context buildNameplateContext(@Nullable PlayerRef playerRef,
                                                                @Nullable String baseName,
                                                                @Nullable UUID uuid,
                                                                @Nullable String tagOverride) {
        String safeName = (baseName == null || baseName.isBlank()) ? "Player" : baseName;

        String rank = "";
        String tag = "";
        String endlessLevel = "";
        String endlessPrestige = "";
        String endlessRace = "";
        String endlessPrimaryClass = "";
        String endlessSecondaryClass = "";
        String rpgLevel = "";
        String ecoquestsRank = "";

        if (uuid != null) {
            String prefix = integrations.getPrimaryPrefix(uuid);
            rank = prefix == null ? "" : prefix;

            TagDefinition active = resolveActiveOrDefaultTag(uuid);
            if (tagOverride != null) {
                tag = tagOverride;
            } else if (active != null && active.getDisplay() != null) {
                tag = active.getDisplay();
            }
        }

        if (playerRef != null) {
            endlessLevel = resolveEndlessLevel(playerRef);
            endlessPrestige = resolveEndlessPrestige(playerRef);
            endlessRace = resolveEndlessRace(playerRef);
            endlessPrimaryClass = resolveEndlessPrimaryClass(playerRef);
            endlessSecondaryClass = resolveEndlessSecondaryClass(playerRef);
            rpgLevel = resolveRpgLevel(playerRef);
            ecoquestsRank = resolveEcoQuestsRank(playerRef);
        }

        return NameplateTextResolver.Context.builder()
                .playerRef(playerRef)
                .playerUuid(uuid)
                .rank(rank)
                .name(safeName)
                .tag(tag)
                .endlessLevel(endlessLevel)
                .endlessPrestige(endlessPrestige)
                .endlessRace(endlessRace)
                .endlessPrimaryClass(endlessPrimaryClass)
                .endlessSecondaryClass(endlessSecondaryClass)
                .rpgLevel(rpgLevel)
                .ecoquestsRank(ecoquestsRank)
                .build();
    }

    @Nonnull
    private String resolveEndlessLevel(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return "";
        }
        return integrations.getEndlessLevel(uuid);
    }

    @Nonnull
    private String resolveEndlessPrestige(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return "";
        }
        return integrations.getEndlessPrestige(uuid);
    }

    @Nonnull
    private String resolveEndlessRace(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return "";
        }
        return integrations.getEndlessRace(uuid);
    }

    @Nonnull
    private String resolveEndlessPrimaryClass(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return "";
        }
        return integrations.getEndlessPrimaryClass(uuid);
    }

    @Nonnull
    private String resolveEndlessSecondaryClass(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return "";
        }
        return integrations.getEndlessSecondaryClass(uuid);
    }

    @Nonnull
    private String resolveRpgLevel(@Nonnull PlayerRef playerRef) {
        return integrations.getRpgLevel(playerRef);
    }

    @Nonnull
    private String resolveEcoQuestsRank(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            return "E";
        }
        return integrations.getEcoQuestsRank(uuid);
    }
}
