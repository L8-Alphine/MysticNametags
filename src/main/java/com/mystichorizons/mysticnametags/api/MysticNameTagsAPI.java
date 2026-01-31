package com.mystichorizons.mysticnametags.api;

import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.mystichorizons.mysticnametags.integrations.IntegrationManager;
import com.mystichorizons.mysticnametags.nameplate.NameplateManager;
import com.mystichorizons.mysticnametags.tags.TagDefinition;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.tags.TagManager.TagPurchaseResult;
import com.mystichorizons.mysticnametags.util.ColorFormatter;
import com.mystichorizons.mysticnametags.util.MysticNotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Public entry-point for other plugins to integrate with MysticNameTags.
 *
 * All methods are static and safe to call from *any* plugin as long as
 * MysticNameTags is enabled and fully initialized.
 *
 * <p>Features exposed:</p>
 * <ul>
 *   <li>Query tags and player ownership</li>
 *   <li>Purchase / equip / toggle tags</li>
 *   <li>Formatted nameplate building (colored + plain)</li>
 *   <li>Rank prefix + economy information</li>
 *   <li>Placeholder expansion for simple text formats</li>
 *   <li>Notification helpers using MysticNameTags&apos; color parser</li>
 * </ul>
 */
public final class MysticNameTagsAPI {

    private MysticNameTagsAPI() {
    }

    // ---------------------------------------------------------------------
    // Core accessors
    // ---------------------------------------------------------------------

    private static TagManager mgr() {
        TagManager manager = TagManager.get();
        if (manager == null) {
            throw new IllegalStateException("MysticNameTags TagManager is not initialized yet");
        }
        return manager;
    }

    private static IntegrationManager integrations() {
        return mgr().getIntegrations();
    }

    // ---------------------------------------------------------------------
    // Basic tag helpers (backwards compatible with your existing API)
    // ---------------------------------------------------------------------

    /**
     * Returns the raw tag display for the player (with color codes),
     * or {@code null} if they have no tag equipped.
     *
     * This is the same behavior as your original method – kept for
     * binary/source compatibility.
     */
    @Nullable
    public static String getActiveTagDisplay(@Nullable UUID uuid) {
        if (uuid == null) return null;
        TagDefinition def = mgr().getEquipped(uuid);
        return def != null ? def.getDisplay() : null;
    }

    /**
     * Returns the active tag definition for this player, or null if none.
     */
    @Nullable
    public static TagDefinition getActiveTag(@Nullable UUID uuid) {
        if (uuid == null) return null;
        return mgr().getEquipped(uuid);
    }

    /**
     * Returns the active tag as a {@link TagView} snapshot (colored + plain),
     * or null if no tag is equipped.
     */
    @Nullable
    public static TagView getActiveTagView(@Nullable UUID uuid) {
        TagDefinition def = getActiveTag(uuid);
        return (def != null) ? TagView.from(def) : null;
    }

    // ---------------------------------------------------------------------
    // Tag registry / lookup
    // ---------------------------------------------------------------------

    /**
     * Returns an immutable view of all registered tags.
     * Safe to iterate, but do not modify.
     */
    @Nonnull
    public static Collection<TagDefinition> getAllTagDefinitions() {
        return mgr().getAllTags();
    }

    /**
     * Returns a list of {@link TagView} snapshots for all registered tags.
     */
    @Nonnull
    public static List<TagView> getAllTags() {
        return mgr().getAllTags()
                .stream()
                .map(TagView::from)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Get a single tag by id (case-insensitive), or null.
     */
    @Nullable
    public static TagDefinition getTagDefinition(@Nullable String id) {
        if (id == null) return null;
        return mgr().getTag(id);
    }

    /**
     * Get a single tag as a {@link TagView} snapshot, or null.
     */
    @Nullable
    public static TagView getTag(@Nullable String id) {
        TagDefinition def = getTagDefinition(id);
        return (def != null) ? TagView.from(def) : null;
    }

    /**
     * Returns true if this player has permanent ownership of the tag id
     * (based on their JSON playerdata), regardless of whether they have
     * it currently equipped.
     */
    public static boolean ownsTag(@Nullable UUID uuid, @Nullable String id) {
        if (uuid == null || id == null) return false;
        return mgr().ownsTag(uuid, id);
    }

    // ---------------------------------------------------------------------
    // Tag usage, purchasing, and equipping
    // ---------------------------------------------------------------------

    /**
     * Returns true if the player is allowed to use the given tag:
     * <ul>
     *   <li>They own it in their tag JSON, OR</li>
     *   <li>They satisfy the permission logic (full/permissive gate)</li>
     * </ul>
     * This matches the same logic used by the Tags UI.
     */
    public static boolean canUseTag(@Nonnull PlayerRef ref,
                                    @Nullable UUID uuid,
                                    @Nonnull TagDefinition def) {
        return mgr().canUseTag(ref, uuid, def);
    }

    /**
     * Equip a tag the player already owns. This does not attempt to purchase it.
     *
     * @return true if the tag is now equipped, false otherwise.
     */
    public static boolean equipOwnedTag(@Nonnull UUID uuid, @Nonnull String id) {
        return mgr().equipTag(uuid, id);
    }

    /**
     * Toggle a tag for the player:
     * <ul>
     *   <li>If currently equipped → unequips</li>
     *   <li>Otherwise → attempts to purchase (if needed) and equip</li>
     * </ul>
     *
     * The result is mapped to a high-level {@link TagActionStatus} enum
     * suitable for other plugins to react on.
     */
    @Nonnull
    public static TagActionStatus toggleTag(@Nonnull PlayerRef ref,
                                            @Nonnull UUID uuid,
                                            @Nonnull String id) {
        TagPurchaseResult internal = mgr().toggleTag(ref, uuid, id);
        return TagActionStatus.fromInternal(internal);
    }

    /**
     * Purchase (if needed) and equip the given tag, mirroring the behavior
     * used by the Tags UI button. This may charge the player via the
     * configured economy.
     */
    @Nonnull
    public static TagActionStatus purchaseAndEquip(@Nonnull PlayerRef ref,
                                                   @Nonnull UUID uuid,
                                                   @Nonnull String id) {
        TagPurchaseResult internal = mgr().purchaseAndEquip(ref, uuid, id);
        return TagActionStatus.fromInternal(internal);
    }

    // ---------------------------------------------------------------------
    // Nameplate building & refresh
    // ---------------------------------------------------------------------

    /**
     * Builds the full COLORED string "[Rank] Name [Tag]" for chat / UI usage.
     * This uses the same formatting as your Settings-based pipeline.
     */
    @Nonnull
    public static String getColoredFullNameplate(@Nonnull PlayerRef ref) {
        return mgr().getColoredFullNameplate(ref);
    }

    /**
     * Builds the full COLORED string "[Rank] Name [Tag]" for the given UUID
     * and base name (used when you don't have a PlayerRef handy).
     */
    @Nonnull
    public static String getColoredFullNameplate(@Nonnull UUID uuid,
                                                 @Nonnull String baseName) {
        return mgr().getColoredFullNameplate(uuid, baseName);
    }

    /**
     * Builds the full PLAIN string "[Rank] Name [Tag]" with all formatting
     * stripped, suitable for scoreboard or external systems that can&apos;t
     * handle color codes.
     */
    @Nonnull
    public static String getPlainFullNameplate(@Nonnull UUID uuid,
                                               @Nonnull String baseName) {
        return mgr().getPlainFullNameplate(uuid, baseName);
    }

    /**
     * Forces an immediate nameplate refresh for a player in a specific world,
     * using the *plain* nameplate builder and applying it via NameplateManager.
     *
     * This is the same method your internal systems use when ranks/tags change.
     */
    public static void refreshNameplate(@Nonnull PlayerRef ref,
                                        @Nonnull World world) {
        mgr().refreshNameplate(ref, world);
    }

    /**
     * Returns the last known plain nameplate for a player, if cached,
     * otherwise builds a fresh one from current state.
     */
    @Nonnull
    public static String getOrBuildPlainNameplate(@Nonnull PlayerRef ref,
                                                  @Nonnull World world) {
        UUID uuid = ref.getUuid();
        // we don't expose the cache map directly; just reuse existing logic
        mgr().refreshNameplate(ref, world); // ensures cache is up to date
        return mgr().getPlainFullNameplate(uuid, ref.getUsername());
    }

    // ---------------------------------------------------------------------
    // Rank prefix & economy helpers
    // ---------------------------------------------------------------------

    /**
     * Returns the best available rank prefix for a player, or null.
     *
     * Order of resolution:
     * <ul>
     *   <li>PrefixesPlus (if installed)</li>
     *   <li>LuckPerms meta prefix (if installed)</li>
     *   <li>null otherwise</li>
     * </ul>
     *
     * <p><b>Note:</b> The method name is kept as {@code getLuckPermsPrefix}
     * for backwards compatibility, but the actual source may be PrefixesPlus
     * or LuckPerms depending on what is available.</p>
     */
    @Nullable
    public static String getLuckPermsPrefix(@Nonnull UUID uuid) {
        return integrations().getPrimaryPrefix(uuid);
    }

    /**
     * Returns the rank prefix stripped of all color codes, or null.
     * This uses {@link #getLuckPermsPrefix(UUID)} under the hood.
     */
    @Nullable
    public static String getLuckPermsPrefixPlain(@Nonnull UUID uuid) {
        String prefix = getLuckPermsPrefix(uuid);
        if (prefix == null) return null;
        String stripped = ColorFormatter.stripFormatting(prefix);
        return stripped != null ? stripped.trim() : null;
    }

    /**
     * Returns true if ANY supported economy backend is available.
     */
    public static boolean hasAnyEconomy() {
        return integrations().hasAnyEconomy();
    }

    /**
     * Returns the numeric balance from the highest priority economy backend.
     */
    public static double getBalance(@Nonnull UUID uuid) {
        return integrations().getBalance(uuid);
    }

    /**
     * Returns true if the player has at least the given amount according
     * to the highest priority backend.
     */
    public static boolean hasBalance(@Nonnull UUID uuid, double amount) {
        return integrations().hasBalance(uuid, amount);
    }

    /**
     * Attempts to withdraw the given amount via the highest priority backend.
     *
     * Returns true if the withdrawal appears to succeed, false otherwise.
     */
    public static boolean withdraw(@Nonnull UUID uuid, double amount) {
        return integrations().withdraw(uuid, amount);
    }

    // ---------------------------------------------------------------------
    // Placeholder helpers
    // ---------------------------------------------------------------------

    /**
     * Simple placeholder expansion for text containing %mystic_tag%.
     * Keeps your original behavior for compatibility.
     */
    public static String applyTagPlaceholder(String text,
                                             @Nullable PlayerRef playerRef,
                                             @Nullable UUID uuid) {
        if (text == null || !text.contains("%mystic_tag%")) {
            return text;
        }

        String tag = getActiveTagDisplay(uuid);
        if (tag == null) tag = "";

        return text.replace("%mystic_tag%", tag);
    }

    /**
     * Expanded placeholder expansion. Supports:
     *
     * <ul>
     *   <li>%mystic_tag%          – colored tag display</li>
     *   <li>%mystic_tag_plain%    – plain tag text</li>
     *   <li>%mystic_full%         – colored "[Rank] Name [Tag]"</li>
     *   <li>%mystic_full_plain%   – plain "[Rank] Name [Tag]"</li>
     *   <li>%mystic_rank%         – colored rank prefix (PrefixesPlus/LuckPerms)</li>
     *   <li>%mystic_rank_plain%   – plain rank prefix</li>
     *   <li>%mystic_balance%      – numeric balance</li>
     * </ul>
     *
     * Any placeholder without available data is replaced with an empty string.
     */
    public static String applyAllPlaceholders(@Nonnull String text,
                                              @Nullable PlayerRef ref,
                                              @Nullable UUID uuid) {
        if (text.isEmpty()) {
            return text;
        }

        UUID useUuid = uuid;
        String name = null;

        if (ref != null) {
            useUuid = ref.getUuid();
            name = ref.getUsername();
        }

        if (useUuid == null) {
            // no player context – just strip our custom placeholders
            return text
                    .replace("%mystic_tag%", "")
                    .replace("%mystic_tag_plain%", "")
                    .replace("%mystic_full%", "")
                    .replace("%mystic_full_plain%", "")
                    .replace("%mystic_rank%", "")
                    .replace("%mystic_rank_plain%", "")
                    .replace("%mystic_balance%", "");
        }

        // Tag
        String rawTag = getActiveTagDisplay(useUuid);
        String coloredTag = (rawTag != null) ? ColorFormatter.colorize(rawTag) : "";
        String plainTag = (rawTag != null)
                ? Optional.ofNullable(ColorFormatter.stripFormatting(rawTag)).orElse("")
                : "";

        // Rank prefix
        String rankColored = Optional.ofNullable(getLuckPermsPrefix(useUuid)).orElse("");
        String rankPlain = Optional.ofNullable(getLuckPermsPrefixPlain(useUuid)).orElse("");

        // Full nameplate
        if (name == null && ref != null) {
            name = ref.getUsername();
        }
        if (name == null) {
            name = "Unknown";
        }

        String fullColored = mgr().getColoredFullNameplate(useUuid, name);
        String fullPlain   = mgr().getPlainFullNameplate(useUuid, name);

        // Balance
        double balance = getBalance(useUuid);

        return text
                .replace("%mystic_tag%", coloredTag)
                .replace("%mystic_tag_plain%", plainTag)
                .replace("%mystic_rank%", rankColored)
                .replace("%mystic_rank_plain%", rankPlain)
                .replace("%mystic_full%", fullColored)
                .replace("%mystic_full_plain%", fullPlain)
                .replace("%mystic_balance%", String.valueOf(balance));
    }

    // ---------------------------------------------------------------------
    // Notification helpers
    // ---------------------------------------------------------------------

    /**
     * Sends a colored notification using the same color parsing as MysticNameTags.
     *
     * Shorthand for {@link MysticNotificationUtil#send(PacketHandler, String, String, NotificationStyle)}.
     */
    public static void sendNotification(PacketHandler handler,
                                        String title,
                                        @Nullable String body,
                                        NotificationStyle style) {
        MysticNotificationUtil.send(handler, title, body, style);
    }

    /**
     * Builds a {@link Message} from a colorized string using MysticNameTags&apos;
     * color pipeline (&#hex, &a..&f, etc.).
     *
     * Handy if other plugins want to build rich Messages for their own UIs.
     */
    @Nonnull
    public static Message toMessage(@Nonnull String text) {
        return ColorFormatter.toMessage(text);
    }

    // ---------------------------------------------------------------------
    // Misc helpers
    // ---------------------------------------------------------------------

    /**
     * Returns an immutable list of tags for a given page (0-based) and size,
     * mirroring the pagination used by the Tags UI.
     */
    @Nonnull
    public static List<TagView> getTagsPage(int page, int pageSize) {
        List<TagDefinition> defs = mgr().getTagsPage(page, pageSize);
        return defs.stream().map(TagView::from).collect(Collectors.toUnmodifiableList());
    }
}
