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
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.nameplate.GlyphNameplateManager;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;
import com.mystichorizons.mysticnametags.nameplate.NameplateTextResolver;
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
    private final Map<UUID, PlayerTagData> playerData = new HashMap<>();

    // Cache of the last applied nameplate text (colored or plain)
    private final Map<UUID, String> lastNameplateText = new ConcurrentHashMap<>();

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

            case MYSQL: {
                String host = settings.getMysqlHost();
                int port = settings.getMysqlPort();
                String db = settings.getMysqlDatabase();
                String user = settings.getMysqlUser();
                String pass = settings.getMysqlPassword();

                String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + db +
                        "?useSSL=false&autoReconnect=true&characterEncoding=UTF-8";

                store = new SqlPlayerTagStore(jdbcUrl, user, pass, GSON);
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
        playerTagStore.save(uuid, data);
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

        PlayerTagData data = getOrLoad(uuid);

        if (data.owns(keyId)) {
            data.setEquipped(keyId);
            savePlayerData(uuid);
            clearCanUseCache(uuid);
            refreshIfOnline(uuid);
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
        String plainFallback = resolved.getPlain();

        boolean glyphEnabled = settings.isExperimentalGlyphNameplatesEnabled();
        String compareKey = glyphEnabled ? resolvedColored : plainFallback;

        String finalBaseName = baseName;
        String finalResolvedColored = resolvedColored;
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
                0
        ));
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
        REQUIREMENTS_NOT_MET,
        TRANSACTION_FAILED
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

        // Keep &#RRGGBB intact because this chat system supports that format
        return ColorFormatter.translateAlternateColorCodes('§', display);
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

    // ============================================================
    // Admin helpers
    // ============================================================

    public boolean adminGiveTag(@Nonnull UUID uuid,
                                @Nonnull String id,
                                boolean equip) {

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

        PlayerRef ref = onlinePlayers.get(uuid);
        World world = onlineWorlds.get(uuid);
        if (ref != null && world != null) {
            refreshNameplate(ref, world);
        }

        return true;
    }

    public boolean adminRemoveTag(@Nonnull UUID uuid,
                                  @Nonnull String id) {

        PlayerTagData data = getOrLoad(uuid);
        String keyId = id.toLowerCase(Locale.ROOT);

        boolean removed = data.getOwned().remove(keyId);
        if (!removed) {
            return false;
        }

        if (keyId.equalsIgnoreCase(data.getEquipped())) {
            data.setEquipped(null);
        }

        savePlayerData(uuid);
        clearCanUseCache(uuid);

        PlayerRef ref = onlinePlayers.get(uuid);
        World world = onlineWorlds.get(uuid);
        if (ref != null && world != null) {
            refreshNameplate(ref, world);
        }

        return true;
    }

    public boolean adminResetTags(@Nonnull UUID uuid) {
        PlayerTagData data = getOrLoad(uuid);
        if (data.getOwned().isEmpty() && data.getEquipped() == null) {
            return false;
        }

        data.getOwned().clear();
        data.setEquipped(null);

        savePlayerData(uuid);
        clearCanUseCache(uuid);

        try {
            playerTagStore.delete(uuid);
        } catch (Throwable ignored) {
        }

        PlayerRef ref = onlinePlayers.get(uuid);
        World world = onlineWorlds.get(uuid);
        if (ref != null && world != null) {
            refreshNameplate(ref, world);
        }

        return true;
    }

    public boolean adminResetTagsAndPermissions(@Nonnull UUID uuid) {
        boolean changed = adminResetTags(uuid);
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

    private void refreshIfOnline(@Nonnull UUID uuid) {
        PlayerRef ref = onlinePlayers.get(uuid);
        World world = onlineWorlds.get(uuid);
        if (ref != null && world != null) {
            try {
                refreshNameplate(ref, world);
            } catch (Throwable t) {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Failed to refresh nameplate after tag change for " + uuid);
            }
        }
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
                            attempt + 1
                    ));
                } else {
                    lastNameplateText.remove(uuid);
                }
                return;
            }

            String previous = lastNameplateText.get(uuid);
            if (previous != null && previous.equals(compareKey)) {
                if (!glyphEnabled || GlyphNameplateManager.get().hasLiveRender(uuid)) {
                    return;
                }
            }

            if (glyphEnabled) {
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
                        attempt + 1
                ));
                return;
            }

            lastNameplateText.remove(uuid);
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to refresh nameplate for %s", baseName);
        }
    }

    // ============================================================
    // Unified nameplate context building
    // ============================================================

    @Nonnull
    private NameplateTextResolver.Context buildNameplateContext(@Nullable PlayerRef playerRef,
                                                                @Nullable String baseName,
                                                                @Nullable UUID uuid) {
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
            if (active != null && active.getDisplay() != null) {
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