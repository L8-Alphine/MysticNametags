package com.mystichorizons.mysticnametags.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;

public class TagManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static TagManager instance;

    private final Map<String, TagDefinition> tags = new LinkedHashMap<>();
    private final Map<UUID, PlayerTagData> playerData = new HashMap<>();
    private final Map<UUID, String> lastPlainNameplate = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerRef> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, World>    onlineWorlds  = new ConcurrentHashMap<>();
    private final IntegrationManager integrations;

    private File configFile;
    private File playerDataFolder;

    public static void init(@Nonnull IntegrationManager integrations) {
        instance = new TagManager(integrations);
        instance.loadConfig();
    }

    public static TagManager get() {
        return instance;
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

            try (FileReader reader = new FileReader(configFile)) {
                Type listType = new TypeToken<List<TagDefinition>>() {}.getType();
                List<TagDefinition> list = GSON.fromJson(reader, listType);
                tags.clear();
                if (list != null) {
                    for (TagDefinition def : list) {
                        tags.put(def.getId().toLowerCase(Locale.ROOT), def);
                    }
                }
            }

            LOGGER.at(Level.INFO).log("[MysticNameTags] Loaded " + tags.size() + " tags.");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to load tags.json");
        }
    }

    private void saveDefaultConfig() {
        try (FileWriter writer = new FileWriter(configFile)) {
            List<TagDefinition> defaults = new ArrayList<>();

            TagDefinition mystic = new TagDefinitionBuilder()
                    .id("mystic")
                    .display("&#8A2BE2&l[Mystic]")
                    .description("&7A shimmering arcane title.")
                    .price(0)
                    .purchasable(false)
                    .permission("mysticnametags.tag.mystic")
                    .build();

            TagDefinition dragon = new TagDefinitionBuilder()
                    .id("dragon")
                    .display("&#FFAA00&l[Dragon]")
                    .description("&7Forged in Avalon Realms fire.")
                    .price(5000)
                    .purchasable(true)
                    .permission("mysticnametags.tag.dragon")
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

        try (FileReader reader = new FileReader(file)) {
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
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e)
                    .log("[MysticNameTags] Failed to save tag data for " + uuid);
        }
    }

    // ------------- Public API -------------

    public Collection<TagDefinition> getAllTags() {
        return Collections.unmodifiableCollection(tags.values());
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
     */
    public boolean canUseTag(@Nonnull PlayerRef playerRef,
                             @Nullable UUID uuid,
                             @Nonnull TagDefinition def) {

        // Owned in JSON data?
        if (uuid != null && def.getId() != null) {
            PlayerTagData data = getOrLoad(uuid);
            if (data.owns(def.getId().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        // Permission-based access?
        String perm = def.getPermission();
        if (perm != null && !perm.isEmpty()) {
            try {
                return integrations.hasPermission(playerRef, perm);
            } catch (Throwable ignored) {
                // If integrations fail for some reason, fall through to "false"
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

        // Permission gating (e.g. crate-only tags)
        if (def.getPermission() != null && !def.getPermission().isEmpty()) {
            if (!integrations.hasPermission(playerRef, def.getPermission())) {
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
        return TagPurchaseResult.UNLOCKED_PAID;
    }

    /**
     * Build the final colored nameplate text: [Rank] Name [Tag]
     */
    public String buildNameplate(@Nonnull PlayerRef playerRef,
                                 @Nonnull String baseName,
                                 @Nullable UUID uuid) {
        String rank = null;
        String tag  = null;

        if (uuid != null) {
            rank = integrations.getLuckPermsPrefix(uuid);
            TagDefinition active = getEquipped(uuid);
            if (active != null) {
                tag = active.getDisplay();
            }
        }

        // Full colored string for chat / UI preview, NOT for the Nameplate component
        String formatted = Settings.get().formatNameplate(rank, baseName, tag);
        return ColorFormatter.colorize(formatted);
    }

    /**
     * Build the final *plain* nameplate text for Nameplate component:
     * [Rank] Name [Tag], with ALL color codes stripped.
     *
     * Nameplate component does not support §-codes or hex, so we must
     * send plain text only.
     */
    public String buildPlainNameplate(@Nonnull PlayerRef playerRef,
                                      @Nonnull String baseName,
                                      @Nullable UUID uuid) {
        String rankPlain = null;
        String tagPlain  = null;

        if (uuid != null) {
            // LuckPerms prefix may contain & / § and hex – strip it
            String rank = integrations.getLuckPermsPrefix(uuid);
            if (rank != null && !rank.isEmpty()) {
                rankPlain = ColorFormatter.stripFormatting(rank).trim();
            }

            TagDefinition active = getEquipped(uuid);
            if (active != null) {
                tagPlain = ColorFormatter.stripFormatting(active.getDisplay()).trim();
            }
        }

        StringBuilder sb = new StringBuilder();

        if (rankPlain != null && !rankPlain.isEmpty()) {
            sb.append(rankPlain).append(" ");
        }

        sb.append(baseName);

        if (tagPlain != null && !tagPlain.isEmpty()) {
            sb.append(" ").append(tagPlain);
        }

        return sb.toString();
    }

    /**
     * Rebuild and apply the plain nameplate for this player if it changed.
     * Entry point for join, tag changes, rank changes, etc.
     */
    public void refreshNameplate(@Nonnull PlayerRef playerRef,
                                 @Nonnull World world) {
        UUID uuid = playerRef.getUuid();
        String baseName = playerRef.getUsername();

        // Build plain text (no color codes) off-thread.
        String plain = buildPlainNameplate(playerRef, baseName, uuid);

        // If nothing changed, don't spam the world thread.
        String previous = lastPlainNameplate.put(uuid, plain);
        if (previous != null && previous.equals(plain)) {
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

                NameplateManager.get().apply(uuid, store, ref, plain);
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
        lastPlainNameplate.remove(uuid);
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
        TagDefinition def = getEquipped(uuid);
        if (def == null) {
            return "";
        }
        // def.getDisplay() is like "&#8A2BE2&l[Mystic]"
        return ColorFormatter.colorize(def.getDisplay());
    }

    public String getPlainActiveTag(@Nonnull UUID uuid) {
        TagDefinition def = getEquipped(uuid);
        if (def == null) {
            return "";
        }
        return ColorFormatter.stripFormatting(def.getDisplay());
    }

    /**
     * Full colored format: [Rank] Name [Tag]
     * This is safe for chat / UI, not for Nameplate component.
     */
    public String getColoredFullNameplate(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        String baseName = playerRef.getUsername();
        return buildNameplate(playerRef, baseName, uuid); // already colorized
    }
    /**
     * Full colored “[Rank] Name [Tag]” string for chat / scoreboards (NOT Nameplate).
     */
    public String getColoredFullNameplate(UUID uuid, String baseName) {
        String rank = integrations.getLuckPermsPrefix(uuid);
        TagDefinition active = getEquipped(uuid);
        String tagDisplay = (active != null) ? active.getDisplay() : null;

        String formatted = Settings.get().formatNameplate(rank, baseName, tagDisplay);
        // Use your chat/UI variant – NOT the nameplate-safe one
        return ColorFormatter.colorize(formatted);
    }

    /**
     * Plain “[Rank] Name [Tag]” with all formatting stripped.
     */
    public String getPlainFullNameplate(UUID uuid, String baseName) {
        String colored = getColoredFullNameplate(uuid, baseName);
        return ColorFormatter.stripFormatting(colored).trim();
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
}
