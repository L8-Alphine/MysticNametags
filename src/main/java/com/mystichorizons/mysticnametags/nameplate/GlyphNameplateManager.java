package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.integrations.MysticVanishSupport;
import com.mystichorizons.mysticnametags.license.MysticNameTagsLicense;
import com.mystichorizons.mysticnametags.nameplate.banner.BannerInfo;
import com.mystichorizons.mysticnametags.nameplate.banner.BannerQuadModels;
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphAssets;
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphInfoCompat;
import com.mystichorizons.mysticnametags.nameplate.packet.PacketGlyphIdFactory;
import com.mystichorizons.mysticnametags.nameplate.packet.PacketGlyphSender;
import com.mystichorizons.mysticnametags.nameplate.packet.PacketGlyphState;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class GlyphNameplateManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final GlyphNameplateManager INSTANCE = new GlyphNameplateManager();

    private static final double ANCHOR_Y_OFFSET = 2.25d;

    /**
     * Substituted for {@code {tag}} when the equipped tag has a banner, so the renderer knows
     * which line of the player's nameplateFormat the artwork belongs on. Plain letters, so no
     * colorize or strip pass mangles it before it gets here.
     */
    public static final String BANNER_SLOT_TOKEN = "MNTBANNERSLOT";

    private static final float BILLBOARD_YAW_DIRTY_DEGREES = 0.75f;
    private static final float BILLBOARD_YAW_DIRTY_RADIANS = (float) Math.toRadians(BILLBOARD_YAW_DIRTY_DEGREES);
    private static final int POST_SPAWN_CORRECTION_UPDATES = 3;

    private static final float GLYPH_YAW_CORRECTION_DEGREES = 0f;
    private static final float GLYPH_YAW_CORRECTION_RADIANS = (float) Math.toRadians(GLYPH_YAW_CORRECTION_DEGREES);

    private static final double GLYPH_EXTRA_SPACING_PX = 4.0d;
    private static final double GLYPH_SOURCE_WIDTH_PX = 16.0d;
    private static final double GLYPH_RUN_SLOT_UNITS_PER_BLOCK = 64.0d;

    private final Map<UUID, RenderState> states = new ConcurrentHashMap<>();
    private final PacketGlyphState packetGlyphState = new PacketGlyphState();
    private final Set<String> loggedPacketSpawns = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> tintEffectIndexCache = new ConcurrentHashMap<>();
    private final Set<Integer> loggedMissingTintEffects = ConcurrentHashMap.newKeySet();

    private GlyphNameplateManager() {
    }

    public static GlyphNameplateManager get() {
        return INSTANCE;
    }

    private static boolean hasLiveRender(@Nullable RenderState state) {
        if (state == null) return false;
        return !state.lines.isEmpty();
    }

    private static List<String> splitLines(String text, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }

        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] raw = normalized.split("\n", -1);

        for (String line : raw) {
            out.add(line == null ? "" : line);
            if (out.size() >= Math.max(1, maxLines)) {
                break;
            }
        }

        if (out.isEmpty()) {
            out.add("");
        }

        return out;
    }

    private static String clampMultilineVisibleLength(String text, int maxLines, int maxVisiblePerLine) {
        if (text == null || text.isEmpty()) return "";
        text = ColorFormatter.colorizeForGlyphNameplate(text);

        List<String> lines = splitLines(text, maxLines);
        List<String> out = new ArrayList<>(lines.size());

        for (String line : lines) {
            out.add(clampSingleLineVisibleLength(line, maxVisiblePerLine));
        }

        return String.join("\n", out);
    }

    private static String clampSingleLineVisibleLength(String text, int maxVisible) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder out = new StringBuilder(text.length());
        int visible = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if ((c == '&' || c == '§') && i + 7 < text.length() && text.charAt(i + 1) == '#') {
                out.append(text, i, i + 8);
                i += 7;
                continue;
            }

            if ((c == '&' || c == '§') && i + 13 < text.length() && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X')) {
                out.append(text, i, i + 14);
                i += 13;
                continue;
            }

            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if ("0123456789abcdefABCDEFklmnorKLMNORxX".indexOf(code) >= 0) {
                    out.append(c).append(code);
                    i += 1;
                    continue;
                }
            }

            if (c == '<') {
                int end = text.indexOf('>', i);
                if (end > i) {
                    out.append(text, i, end + 1);
                    i = end;
                    continue;
                }
            }

            if (c == '\n' || c == '\r') {
                continue;
            }

            out.append(c);
            visible++;
            if (visible >= maxVisible) break;
        }

        return out.toString();
    }

    private static double getGlyphAdvance(double scale) {
        double glyphWidth = GlyphInfoCompat.CHAR_WIDTH * scale;
        double extraSpacing = (GLYPH_EXTRA_SPACING_PX / GLYPH_SOURCE_WIDTH_PX) * glyphWidth;
        return glyphWidth + extraSpacing;
    }

    private static float normalizeRadians(float yaw) {
        float twoPi = (float) (Math.PI * 2.0d);
        float out = yaw % twoPi;
        if (out < 0f) out += twoPi;
        return out;
    }

    private static float wrapSignedRadians(float yaw) {
        float twoPi = (float) (Math.PI * 2.0d);
        float out = (yaw + (float) Math.PI) % twoPi;
        if (out < 0f) out += twoPi;
        return out - (float) Math.PI;
    }

    private static float angleDeltaRadians(float a, float b) {
        return wrapSignedRadians(a - b);
    }

    private static float continuousYaw(float yaw, float previousYaw) {
        if (Float.isNaN(previousYaw)) {
            return yaw;
        }
        return previousYaw + angleDeltaRadians(yaw, previousYaw);
    }

    private static int viewerIdentity(@Nonnull Store<EntityStore> store,
                                      @Nonnull Ref<EntityStore> viewerRef) {
        try {
            NetworkId networkId = store.getComponent(viewerRef, NetworkId.getComponentType());
            if (networkId != null) {
                return networkId.getId();
            }
        } catch (Throwable ignored) {
        }

        return System.identityHashCode(viewerRef);
    }

    public void apply(@Nonnull UUID uuid,
                      @Nonnull World world,
                      @Nonnull Store<EntityStore> store,
                      @Nonnull Ref<EntityStore> playerRef,
                      @Nonnull String formattedText) {

        store.assertThread();

        Settings settings = Settings.get();
        if (!settings.isExperimentalGlyphNameplatesEnabled()) {
            remove(uuid, world, store);
            return;
        }

        String clamped = clampMultilineVisibleLength(
                formattedText,
                settings.getExperimentalGlyphMaxLines(),
                settings.getExperimentalGlyphMaxCharsPerLine()
        );
        String glyphFont = settings.getExperimentalGlyphFont();

        RenderState state = states.computeIfAbsent(uuid, RenderState::new);

        String previousWorldName = state.worldName;
        boolean worldChanged = previousWorldName != null && !Objects.equals(previousWorldName, world.getName());

        boolean needsRebuild =
                worldChanged
                        || !Objects.equals(state.lastText, clamped)
                        || !Objects.equals(state.lastGlyphFont, glyphFont)
                        || !hasLiveRender(state);

        if (needsRebuild) {
            boolean rebuilt = rebuild(world, store, playerRef, state, clamped, settings);
            if (!rebuilt) {
                state.lastText = null;
                state.lastGlyphFont = null;
                state.worldName = world.getName();
                return;
            }

            state.lastText = clamped;
            state.lastGlyphFont = glyphFont;
        }

        state.worldName = world.getName();
        follow(uuid, world, store, playerRef, state);
    }

    public void remove(@Nonnull UUID uuid,
                       @Nonnull World world,
                       @Nonnull Store<EntityStore> store) {
        store.assertThread();

        RenderState state = states.remove(uuid);
        if (state == null) return;

        despawnAll(store, state);
    }

    public void remove(@Nonnull UUID uuid, @Nonnull World world) {
        RenderState state = states.remove(uuid);
        if (state == null) return;

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            store.assertThread();
            despawnAll(store, state);
        });
    }

    public void removeSelfView(@Nonnull UUID uuid, @Nonnull World world) {
        removePacketGlyphsForViewer(uuid, world, uuid);
    }

    public void forget(@Nonnull UUID uuid) {
        states.remove(uuid);
        packetGlyphState.clearSubject(uuid);
    }

    /**
     * Disconnect-safe cleanup.  Called from the PlayerDisconnectEvent handler
     * which runs synchronously inside {@code Universe.removePlayer()} —
     * <em>before</em> {@code Player.remove()} is enqueued on the world thread.
     *
     * <p>The method clears render tracking immediately (so the follow-task
     * stops touching this player), sends packet despawns to surviving viewers,
     * and then enqueues lightweight anchor-entity removal on the world thread.
     * The disconnecting player's own connection is skipped because it is
     * already being torn down.</p>
     *
     * <p>The outer lambda is wrapped in {@code catch(Throwable)} so that an
     * unexpected {@code Error} cannot kill the world's ticking thread and
     * stall the subsequent {@code Player.remove()} task (which caused the
     * 5-second timeout seen in production).</p>
     */
    public void disconnectCleanup(@Nonnull UUID uuid, @Nonnull World world) {
        // 1. Pull state atomically — follow task will no longer see this player
        RenderState state = states.remove(uuid);

        // 2. Remove packet-only glyphs from everyone still watching this player.
        removePacketGlyphsForRemainingViewers(uuid);

        // 3. Clear packet-glyph bookkeeping (safe from any thread)
        packetGlyphState.clearSubject(uuid);

        if (state == null || state.lines.isEmpty()) {
            return;
        }

        // 4. Enqueue anchor entity removal on the world thread.
        //    This will sit in the queue BEFORE Player.remove(), which is fine —
        //    the anchor cleanup is lightweight and protected by catch(Throwable).
        if (world.isAlive()) {
            world.execute(() -> {
                try {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    for (LineRenderState line : state.lines) {
                        if (line == null) continue;
                        if (line.anchorRef != null && line.anchorRef.isValid()) {
                            removeEntity(store, line.anchorRef);
                            line.anchorRef = null;
                        }
                    }
                    state.lines.clear();
                } catch (Throwable t) {
                    LOGGER.at(Level.WARNING).withCause(t)
                            .log("[MysticNameTags] Anchor cleanup failed during disconnect for %s", uuid);
                }
            });
        }
    }

    private void removePacketGlyphsForRemainingViewers(@Nonnull UUID subjectUuid) {
        Map<Integer, PacketGlyphState.ViewerState> snapshot = packetGlyphState.snapshotViewers(subjectUuid);
        if (snapshot.isEmpty()) {
            return;
        }

        try {
            Universe universe = Universe.get();
            if (universe == null) {
                return;
            }

            for (World viewerWorld : universe.getWorlds().values()) {
                if (viewerWorld == null || !viewerWorld.isAlive()) {
                    continue;
                }

                for (PacketGlyphState.ViewerState viewerState : snapshot.values()) {
                    if (viewerState == null || viewerState.spawnedIds.isEmpty()) {
                        continue;
                    }
                    if (subjectUuid.equals(viewerState.viewerUuid)) {
                        continue;
                    }

                    PlayerRef viewer = findPlayerRef(viewerWorld, viewerState.viewerUuid);
                    if (viewer != null) {
                        PacketGlyphSender.removeGlyphs(viewer, viewerState.spawnedIds);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Packet glyph disconnect cleanup failed for %s", subjectUuid);
        }
    }

    public void followOnly(@Nonnull World world,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> playerRef,
                           @Nonnull UUID uuid) {
        store.assertThread();

        RenderState state = states.get(uuid);
        if (state == null) return;
        if (!hasLiveRender(state)) return;

        follow(uuid, world, store, playerRef, state);
    }

    /**
     * Re-arms glyph delivery for a client that has just become ready.
     *
     * <p>Nothing is despawned: the packets this discards were written to a client that was
     * not yet accepting entity spawns, so the entities do not exist on it. The next follow
     * tick resends them.</p>
     */
    public void onViewerReady(@Nonnull UUID viewerUuid) {
        packetGlyphState.forgetViewer(viewerUuid);
        String viewerKey = ":" + viewerUuid + ":";
        loggedPacketSpawns.removeIf(key -> key.contains(viewerKey));
    }

    public boolean hasState(@Nonnull UUID uuid) {
        RenderState state = states.get(uuid);
        return hasLiveRender(state);
    }

    public boolean hasLiveRender(@Nonnull UUID uuid) {
        RenderState state = states.get(uuid);
        return hasLiveRender(state);
    }

    public void clearAllInWorld(@Nonnull World world) {
        final String worldName = world.getName();

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            store.assertThread();

            for (UUID uuid : new ArrayList<>(states.keySet())) {
                RenderState state = states.get(uuid);
                if (state == null) continue;
                if (!Objects.equals(worldName, state.worldName)) continue;

                try {
                    remove(uuid, world, store);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private boolean rebuild(@Nonnull World world,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> playerRef,
                            @Nonnull RenderState state,
                            @Nonnull String text,
                            @Nonnull Settings settings) {

        despawnAll(store, state);
        state.lines.clear();
        state.packetGeneration++;

        TransformComponent playerTx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTx == null) {
            return false;
        }

        List<String> logicalLines = splitLines(text, settings.getExperimentalGlyphMaxLines());
        if (logicalLines.isEmpty()) {
            logicalLines = Collections.singletonList("");
        }

        double lineSpacing = settings.getExperimentalGlyphLineSpacing();
        String glyphFont = settings.getExperimentalGlyphFont();
        int hardCap = settings.getExperimentalGlyphMaxEntitiesPerPlayer();
        int spawnedCount = 0;
        boolean spawnAttemptedForVisibleGlyph = false;
        boolean spawnedAnyGlyph = false;

        double charAdvance = getGlyphAdvance(state.scale);

        for (int lineIndex = 0; lineIndex < logicalLines.size(); lineIndex++) {
            String lineText = logicalLines.get(lineIndex);
            if (lineText == null) {
                lineText = "";
            }

            LineRenderState lineState = new LineRenderState();
            lineState.text = lineText;
            lineState.glyphFont = glyphFont;
            lineState.yOffset = lineIndex * lineSpacing;

            LineBuildResult built = populateTextLine(
                    lineState,
                    lineText,
                    glyphFont,
                    settings,
                    charAdvance,
                    hardCap - spawnedCount
            );

            spawnedCount += built.glyphsAdded;
            spawnAttemptedForVisibleGlyph |= built.attemptedVisibleGlyph;
            spawnedAnyGlyph |= built.glyphsAdded > 0;

            rebuildLineRuns(lineState, state.scale);
            state.lines.add(lineState);

            if (spawnedCount >= hardCap) {
                break;
            }
        }

        if (state.lines.isEmpty()) {
            return false;
        }

        if (!spawnAttemptedForVisibleGlyph) {
            return true;
        }

        return spawnedAnyGlyph;
    }

    @Nonnull
    private static LineRenderState newBannerLine(@Nonnull BannerInfo banner,
                                                 @Nonnull BannerQuadModels.Rendered rendered,
                                                 @Nonnull String glyphFont,
                                                 double stateScale) {
        LineRenderState line = new LineRenderState();
        line.text = banner.name();
        line.glyphFont = glyphFont;
        line.bannerModel = rendered.model();
        line.scaleMultiplier = rendered.entityScale();
        rebuildLineRuns(line, stateScale);
        return line;
    }

    /** True when the line has something to render once color markup is stripped. */
    private static boolean hasVisibleText(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String plain = ColorFormatter.stripFormatting(text);
        return plain != null && !plain.isBlank();
    }

    /**
     * Turns one line of formatted text into glyph entities.
     *
     * @param remainingCap glyph budget left for this player across all lines
     */
    @Nonnull
    private LineBuildResult populateTextLine(@Nonnull LineRenderState lineState,
                                             @Nonnull String lineText,
                                             @Nonnull String glyphFont,
                                             @Nonnull Settings settings,
                                             double charAdvance,
                                             int remainingCap) {

        LineBuildResult result = new LineBuildResult();

        List<ColoredChar> chars = SimpleColorParser.parse(lineText);

        int visibleCount = 0;
        for (ColoredChar cc : chars) {
            if (cc.ch == '\n' || cc.ch == '\r') continue;
            visibleCount++;
        }

        int logicalIndex = 0;

        for (ColoredChar cc : chars) {
            char ch = cc.ch;
            if (ch == '\n' || ch == '\r') continue;

            double offset = ((visibleCount - 1) / 2.0d - logicalIndex) * charAdvance;
            logicalIndex++;

            if (ch == ' ') continue;
            if (result.glyphsAdded >= remainingCap) break;
            if (!GlyphInfoCompat.isSupported(ch)) continue;

            result.attemptedVisibleGlyph = true;

            lineState.glyphChars.add(ch);
            lineState.glyphTexturePaths.add(GlyphAssets.texturePath(ch, GlyphInfoCompat.getSafeIdLower(ch), glyphFont));
            lineState.glyphOffsets.add(offset);
            lineState.glyphTintEffectIndexes.add(resolveTintEffectIndex(scaleColor(cc.color, settings.getExperimentalGlyphTintStrength())));

            result.glyphsAdded++;
        }

        return result;
    }

    /**
     * Renders a tag's banner art above the player instead of text.
     *
     * <p>The banner is one entity riding the same follow/billboard machinery as glyphs, so it
     * costs a single model regardless of how wide the art is.</p>
     *
     * @param nameLineText formatted nameplate text, rendered under the banner only when
     *                     {@code bannerKeepNameLine} is enabled
     */
    public void applyBanner(@Nonnull UUID uuid,
                            @Nonnull World world,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> playerRef,
                            @Nonnull BannerInfo banner,
                            double bannerScale,
                            @Nullable String nameLineText) {

        store.assertThread();

        Settings settings = Settings.get();

        // Callers already gate on TagManager.resolveActiveBanner, which checks both of these. This
        // is the render entry point and it is public, so it re-checks rather than trusting them.
        if (!settings.isExperimentalGlyphNameplatesEnabled()
                || !settings.isBannersEnabled()
                || !MysticNameTagsLicense.bannersLicensed()) {
            remove(uuid, world, store);
            return;
        }

        // The surrounding nameplate lines, with BANNER_SLOT_TOKEN marking where {tag} sat.
        String formatText = clampMultilineVisibleLength(
                nameLineText == null ? "" : nameLineText,
                settings.getExperimentalGlyphMaxLines(),
                settings.getExperimentalGlyphMaxCharsPerLine());

        // Discriminator that text nameplates can never produce, so the shared change detection
        // below correctly rebuilds when swapping between a banner and a text tag.
        String renderKey = "[[banner]]:" + banner.renderKey() + "@" + bannerScale + "|" + formatText;

        RenderState state = states.computeIfAbsent(uuid, RenderState::new);

        String previousWorldName = state.worldName;
        boolean worldChanged = previousWorldName != null && !Objects.equals(previousWorldName, world.getName());

        boolean needsRebuild =
                worldChanged
                        || !Objects.equals(state.lastText, renderKey)
                        || !hasLiveRender(state);

        if (needsRebuild) {
            boolean rebuilt = rebuildBanner(world, store, playerRef, state, banner, bannerScale, formatText, settings);
            if (!rebuilt) {
                state.lastText = null;
                state.lastGlyphFont = null;
                state.worldName = world.getName();
                return;
            }

            state.lastText = renderKey;
            state.lastGlyphFont = settings.getExperimentalGlyphFont();
        }

        state.worldName = world.getName();
        follow(uuid, world, store, playerRef, state);
    }

    private boolean rebuildBanner(@Nonnull World world,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull Ref<EntityStore> playerRef,
                                  @Nonnull RenderState state,
                                  @Nonnull BannerInfo banner,
                                  double bannerScale,
                                  @Nonnull String formatText,
                                  @Nonnull Settings settings) {

        despawnAll(store, state);
        state.lines.clear();
        state.packetGeneration++;

        TransformComponent playerTx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTx == null) {
            return false;
        }

        BannerQuadModels.Rendered rendered = BannerQuadModels.resolve(
                banner,
                settings.getBannerMaxWidthBlocks(),
                settings.getBannerMaxHeightBlocks(),
                bannerScale
        );

        if (rendered == null) {
            LOGGER.at(Level.WARNING).log("[MysticNameTags] Could not build a quad for banner '"
                    + banner.name() + "'; falling back to a text nameplate.");
            return false;
        }

        String glyphFont = settings.getExperimentalGlyphFont();
        double lineSpacing = settings.getExperimentalGlyphLineSpacing();

        // Banner-only nameplate: no surrounding format to honour.
        if (!hasVisibleText(formatText)) {
            LineRenderState only = newBannerLine(banner, rendered, glyphFont, state.scale);
            only.yOffset = 0.0d;
            state.lines.add(only);
            return true;
        }

        double charAdvance = getGlyphAdvance(state.scale);
        int hardCap = settings.getExperimentalGlyphMaxEntitiesPerPlayer();
        int spawnedCount = 0;

        // Built in format order first, then offset by index, so inserting the banner cannot
        // disturb the spacing of the lines around it.
        List<LineRenderState> planned = new ArrayList<>();
        boolean bannerPlaced = false;

        for (String lineText : splitLines(formatText, settings.getExperimentalGlyphMaxLines())) {
            if (lineText == null) {
                lineText = "";
            }

            if (lineText.contains(BANNER_SLOT_TOKEN)) {
                planned.add(newBannerLine(banner, rendered, glyphFont, state.scale));
                bannerPlaced = true;

                // Anything sharing the {tag} line still deserves to render, on its own line
                // directly after the banner. A line that held only {tag} becomes just the banner.
                lineText = lineText.replace(BANNER_SLOT_TOKEN, "");
                if (!hasVisibleText(lineText)) {
                    continue;
                }
            }

            // Blank lines are kept, matching the text-only path, so they still take up space.
            LineRenderState textLine = new LineRenderState();
            textLine.text = lineText;
            textLine.glyphFont = glyphFont;

            spawnedCount += populateTextLine(
                    textLine,
                    lineText,
                    glyphFont,
                    settings,
                    charAdvance,
                    hardCap - spawnedCount
            ).glyphsAdded;

            rebuildLineRuns(textLine, state.scale);
            planned.add(textLine);

            if (spawnedCount >= hardCap) {
                break;
            }
        }

        // Format has no {tag} at all: the banner becomes the topmost line rather than vanishing.
        if (!bannerPlaced) {
            planned.add(newBannerLine(banner, rendered, glyphFont, state.scale));
        }

        for (int i = 0; i < planned.size(); i++) {
            planned.get(i).yOffset = i * lineSpacing;
        }

        state.lines.addAll(planned);

        return true;
    }

    private void follow(@Nonnull UUID uuid,
                        @Nonnull World world,
                        @Nonnull Store<EntityStore> store,
                        @Nonnull Ref<EntityStore> playerRef,
                        @Nonnull RenderState state) {

        TransformComponent playerTx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTx == null) return;

        NetworkId playerNetworkId = store.getComponent(playerRef, NetworkId.getComponentType());
        if (playerNetworkId == null) return;

        Vector3d playerPos = playerTx.getTransform().getPosition();
        Rotation3f playerRot = playerTx.getTransform().getRotation();

        float playerYawRadians = normalizeRadians(playerRot.yaw());

        Set<Integer> activeViewerIds = new HashSet<>();

        for (int lineIndex = 0; lineIndex < state.lines.size(); lineIndex++) {
            LineRenderState line = state.lines.get(lineIndex);
            if (line == null) continue;

            Map<Ref<EntityStore>, EntityViewer> viewers = new LinkedHashMap<>();

            try {
                for (PlayerRef viewerPlayer : world.getPlayerRefs()) {
                    if (viewerPlayer == null) continue;
                    Ref<EntityStore> viewerEntityRef = viewerPlayer.getReference();
                    if (viewerEntityRef == null || !viewerEntityRef.isValid()) continue;
                    viewers.putIfAbsent(viewerEntityRef, null);
                }
            } catch (Throwable ignored) {
            }

            viewers.putIfAbsent(playerRef, null);

            if (viewers.isEmpty()) {
                continue;
            }

            for (Map.Entry<Ref<EntityStore>, EntityViewer> entry : viewers.entrySet()) {
                if (PacketGlyphSender.isRuntimeDisabled()) {
                    continue;
                }

                Ref<EntityStore> viewerRef = entry.getKey();
                if (viewerRef == null || !viewerRef.isValid()) continue;

                boolean selfView = viewerRef.equals(playerRef);
                if (selfView && !TagManager.get().isOwnNameplateVisible(uuid)) {
                    continue;
                }

                float yawRadians;

                if (selfView) {
                    yawRadians = normalizeRadians(playerYawRadians + (float) Math.PI);
                } else {
                    TransformComponent viewerTx = store.getComponent(viewerRef, TransformComponent.getComponentType());
                    if (viewerTx == null) continue;

                    Vector3d viewerPos = viewerTx.getTransform().getPosition();
                    double dx = viewerPos.x() - playerPos.x();
                    double dz = viewerPos.z() - playerPos.z();

                    yawRadians = normalizeRadians((float) Math.atan2(-dx, -dz));
                }

                try {
                    PlayerRef packetViewer = selfView
                            ? findPlayerRef(world, uuid)
                            : findPlayerRef(world, viewerRef);

                    if (packetViewer == null) {
                        LOGGER.at(Level.INFO).log("[MysticNameTags] Packet glyph skipped: could not resolve PlayerRef. selfView="
                                + selfView + ", subject=" + uuid);
                        continue;
                    }

                    UUID viewerUuid = packetViewer.getUuid();
                    if (viewerUuid == null) {
                        LOGGER.at(Level.INFO).log("[MysticNameTags] Packet glyph skipped: viewer UUID was null.");
                        continue;
                    }

                    // Vanished subject: viewers below the subject's vanish level are
                    // skipped and left out of activeViewerIds, so
                    // cleanupDroppedPacketViewers() despawns any glyphs they already
                    // received. Once the subject unvanishes the glyphs respawn via
                    // the normal missing-entity path.
                    if (!selfView && !MysticVanishSupport.canSee(viewerUuid, uuid)) {
                        continue;
                    }

                    int viewerId = viewerIdentity(store, viewerRef);
                    activeViewerIds.add(viewerId);

                    long now = System.nanoTime();
                    PacketGlyphState.ViewerState packetState =
                            packetGlyphState.viewer(uuid, viewerId, viewerUuid);

                    yawRadians = wrapSignedRadians(yawRadians + GLYPH_YAW_CORRECTION_RADIANS);
                    if (selfView) {
                        yawRadians = continuousYaw(yawRadians, packetState.lastYawRadians);
                    }

                    float modelScale = GlyphInfoCompat.BASE_MODEL_SCALE * (float) state.scale * line.scaleMultiplier;
                    float lineOffsetY = (float) (ANCHOR_Y_OFFSET + line.yOffset);
                    int mountedToNetworkId = playerNetworkId.getId();
                    double anchorX = playerPos.x();
                    double anchorY = playerPos.y();
                    double anchorZ = playerPos.z();
                    float glyphYaw = yawRadians;
                    int count = line.glyphRuns.size();

                    boolean hasMissingPacketEntities = false;
                    for (int runIndex = 0; runIndex < count; runIndex++) {
                        int fakeId = PacketGlyphIdFactory.glyphId(uuid, viewerId, lineIndex, runIndex, state.packetGeneration);
                        if (!packetState.spawnedIds.contains(fakeId)) {
                            hasMissingPacketEntities = true;
                            break;
                        }
                    }

                    boolean postSpawnCorrection = packetState.postSpawnCorrectionsRemaining > 0;
                    boolean yawDirty = Float.isNaN(packetState.lastYawRadians)
                            || Math.abs(angleDeltaRadians(yawRadians, packetState.lastYawRadians)) >= BILLBOARD_YAW_DIRTY_RADIANS;
                    boolean parentYawDirty = Float.isNaN(packetState.lastParentYawRadians)
                            || Math.abs(angleDeltaRadians(playerYawRadians, packetState.lastParentYawRadians)) >= BILLBOARD_YAW_DIRTY_RADIANS;
                    long updateIntervalNs = Math.max(1L,
                            (long) Settings.get().getExperimentalGlyphRotationSyncIntervalMs()) * 1_000_000L;
                    boolean intervalReady = now >= packetState.nextUpdateAtNs;
                    boolean rotationDirty = yawDirty || (!selfView && (postSpawnCorrection || parentYawDirty));
                    boolean glyphNeedsUpdate = !packetState.spawnedIds.isEmpty()
                            && rotationDirty
                            && intervalReady;

                    if (count <= 0 || (!hasMissingPacketEntities && !glyphNeedsUpdate)) {
                        continue;
                    }

                    List<EntityUpdate> spawnUpdates = new ArrayList<>();
                    List<PacketGlyphSender.GlyphMove> moveUpdates = new ArrayList<>();

                    for (int runIndex = 0; runIndex < count; runIndex++) {
                        GlyphRunState run = line.glyphRuns.get(runIndex);
                        if (run == null || run.packetModel == null) {
                            continue;
                        }

                        int fakeId = PacketGlyphIdFactory.glyphId(
                                uuid,
                                viewerId,
                                lineIndex,
                                runIndex,
                                state.packetGeneration
                        );

                        if (!packetState.spawnedIds.contains(fakeId)) {
                            spawnUpdates.add(PacketGlyphSender.glyphSpawnUpdate(
                                    fakeId,
                                    mountedToNetworkId,
                                    run.packetModel,
                                    anchorX,
                                    anchorY,
                                    anchorZ,
                                    0.0f,
                                    lineOffsetY,
                                    0.0f,
                                    glyphYaw,
                                    modelScale,
                                    run.tintEffectIndex
                            ));
                        } else if (glyphNeedsUpdate) {
                            moveUpdates.add(new PacketGlyphSender.GlyphMove(
                                    fakeId,
                                    mountedToNetworkId,
                                    anchorX,
                                    anchorY,
                                    anchorZ,
                                    0.0f,
                                    lineOffsetY,
                                    0.0f,
                                    glyphYaw
                            ));
                        }
                    }

                    if (!moveUpdates.isEmpty()) {
                        PacketGlyphSender.updateGlyphs(packetViewer, moveUpdates);
                    }

                    if (!spawnUpdates.isEmpty()) {
                        LOGGER.at(Level.FINE).log("[MysticNameTags] Sending packet glyph spawn count="
                                + spawnUpdates.size()
                                + ", subject=" + uuid
                                + ", viewer=" + viewerUuid
                                + ", selfView=" + selfView);

                        if (PacketGlyphSender.isRuntimeDisabled()) {
                            continue;
                        }

                        boolean sent = PacketGlyphSender.spawnMany(packetViewer, spawnUpdates);

                        if (sent) {
                            logPacketSpawnOnce(uuid, viewerUuid, selfView, line.glyphChars.size(), mountedToNetworkId,
                                    line.glyphTexturePaths.isEmpty() ? "none" : line.glyphTexturePaths.get(0));

                            Map<Integer, Integer> tintUpdates = new LinkedHashMap<>();
                            for (int runIndex = 0; runIndex < count; runIndex++) {
                                GlyphRunState run = line.glyphRuns.get(runIndex);
                                if (run == null) {
                                    continue;
                                }

                                int fakeId = PacketGlyphIdFactory.glyphId(uuid, viewerId, lineIndex, runIndex, state.packetGeneration);
                                packetState.spawnedIds.add(fakeId);

                                Integer tintEffectIndex = run.tintEffectIndex;
                                if (tintEffectIndex != null && tintEffectIndex >= 0) {
                                    tintUpdates.put(fakeId, tintEffectIndex);
                                }
                            }

                            if (!tintUpdates.isEmpty()) {
                                PacketGlyphSender.updateGlyphTints(packetViewer, tintUpdates);
                            }

                            packetState.postSpawnCorrectionsRemaining = selfView
                                    ? 0
                                    : Math.max(
                                            packetState.postSpawnCorrectionsRemaining,
                                            POST_SPAWN_CORRECTION_UPDATES
                                    );
                            packetState.nextUpdateAtNs = 0L;
                        }
                    }

                    if (lineIndex + 1 >= state.lines.size()) {
                        packetState.lastYawRadians = yawRadians;
                        packetState.lastParentYawRadians = playerYawRadians;
                        packetState.lastBaseX = playerPos.x();
                        packetState.lastBaseY = playerPos.y();
                        packetState.lastBaseZ = playerPos.z();
                        if (glyphNeedsUpdate && postSpawnCorrection) {
                            packetState.postSpawnCorrectionsRemaining--;
                            packetState.nextUpdateAtNs = now + Math.min(updateIntervalNs, 1_000_000L);
                        } else if (glyphNeedsUpdate && rotationDirty && intervalReady) {
                            packetState.nextUpdateAtNs = now + updateIntervalNs;
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.at(Level.INFO).withCause(t)
                            .log("[MysticNameTags] Failed to update packet glyph billboard for viewer.");
                }
            }
        }

        cleanupDroppedPacketViewers(world, uuid, activeViewerIds);
    }

    private void logPacketSpawnOnce(@Nonnull UUID subjectUuid,
                                    @Nonnull UUID viewerUuid,
                                    boolean selfView,
                                    int glyphCount,
                                    int mountedToNetworkId,
                                    @Nonnull String firstAssetId) {
        String key = subjectUuid + ":" + viewerUuid + ":" + selfView;
        if (!loggedPacketSpawns.add(key)) {
            return;
        }

        LOGGER.at(Level.INFO).log("[MysticNameTags] Packet glyph spawn sent subject="
                + subjectUuid
                + ", viewer=" + viewerUuid
                + ", selfView=" + selfView
                + ", glyphCount=" + glyphCount
                + ", mountedToNetworkId=" + mountedToNetworkId
                + ", firstAsset=" + firstAssetId);
    }

    private static void rebuildLineRuns(@Nonnull LineRenderState line, double scale) {
        line.glyphRuns.clear();

        // A banner is a single entity carrying the image quad. No tint: the art brings its own
        // colors and the HtTint_* effect would wash them out.
        if (line.bannerModel != null) {
            line.glyphRuns.add(new GlyphRunState(0, 1, null, line.bannerModel));
            return;
        }

        int count = Math.min(
                Math.min(Math.min(line.glyphChars.size(), line.glyphOffsets.size()), line.glyphTintEffectIndexes.size()),
                line.glyphTexturePaths.size()
        );

        // One entity per glyph. Batching a run into a single carrier entity with N
        // ModelAttachment entries does not render on Update 6: the carrier spawns, mounts,
        // positions and even accepts its tint effect, but the attachments are never drawn.
        // A banner is the one model shape proven to render, and it is just a model plus a
        // texture - so each glyph is now sent that same way. The per-glyph x offset still
        // rides in the GlyphSlot_* model geometry, so layout is unchanged.
        for (int i = 0; i < count; i++) {
            addGlyphEntity(line, i, line.glyphTintEffectIndexes.get(i), scale);
        }
    }

    private static void addGlyphEntity(@Nonnull LineRenderState line,
                                       int index,
                                       @Nullable Integer tintEffectIndex,
                                       double scale) {
        com.hypixel.hytale.protocol.Model model = buildGlyphPacketModel(line, index, scale);
        if (model == null) {
            return;
        }

        line.glyphRuns.add(new GlyphRunState(index, index + 1, tintEffectIndex, model));
    }

    @Nullable
    private static com.hypixel.hytale.protocol.Model buildGlyphPacketModel(@Nonnull LineRenderState line,
                                                                           int index,
                                                                           double scale) {
        if (index < 0 || index >= line.glyphOffsets.size() || index >= line.glyphTexturePaths.size()) {
            return null;
        }

        double safeScale = Math.max(0.0001d, scale);
        double offset = line.glyphOffsets.get(index);
        int offsetPx = (int) Math.round((-offset / safeScale) * GLYPH_RUN_SLOT_UNITS_PER_BLOCK);

        // The slot model carries this glyph's horizontal offset in its own geometry, so the
        // entity can sit on the line anchor and still land in the right column. The 16px
        // default family and the 32px families each have their own slot set, sized to the
        // ink cell their textures carry.
        String slotModel = GlyphAssets.slotModelPath(offsetPx, GlyphAssets.cellSize(line.glyphFont));

        String texture = line.glyphTexturePaths.get(index);


        // Shaped exactly like a banner: path + texture + scale, no attachments and no
        // ModelAsset lookup. protocol.Model's no-arg constructor initialises only `phobia`,
        // so `scale` must be set explicitly or the model draws at zero size.
        //
        // assetId must be unique per model+texture pair. Banners get one per quad size
        // (BannerQuad_256x64) and render correctly; a shared constant here made every glyph
        // in a line share one cache entry, so they all drew blank - correctly positioned and
        // correctly tinted, but with the first entry's texture rather than their own.
        com.hypixel.hytale.protocol.Model model = new com.hypixel.hytale.protocol.Model();
        model.assetId = GlyphAssets.slotAssetId(slotModel, texture);
        model.path = slotModel;
        model.texture = texture;
        model.scale = 1.0f;

        return model;
    }

    @Nullable
    private Integer resolveTintEffectIndex(@Nonnull Color color) {
        int rgb = quantizeTintRgb(GlyphAssets.rgb(color));

        return tintEffectIndexCache.computeIfAbsent(rgb, key -> {
            String shortEffectId = "HtTint_" + String.format("%06X", (key & 0xFFFFFF));
            String namespacedEffectId = GlyphAssets.tintEffectId(key);

            try {
                String directEffectId = findTintEffectAssetId(shortEffectId, namespacedEffectId);
                if (directEffectId != null) {
                    return EntityEffect.getAssetMap().getIndex(directEffectId);
                }

                String nearestEffectId = findNearestTintEffectAssetId(key);
                if (nearestEffectId != null) {
                    return EntityEffect.getAssetMap().getIndex(nearestEffectId);
                }

                if (loggedMissingTintEffects.add(key)) {
                    LOGGER.at(Level.INFO).log("[MysticNameTags] Packet glyph tint effect not found: "
                            + shortEffectId + " or " + namespacedEffectId);
                }
                return -1;
            } catch (Throwable t) {
                if (loggedMissingTintEffects.add(key)) {
                    LOGGER.at(Level.INFO).withCause(t)
                            .log("[MysticNameTags] Packet glyph tint effect lookup failed: "
                                    + shortEffectId + " or " + namespacedEffectId);
                }
                return -1;
            }
        });
    }

    @Nullable
    private static String findTintEffectAssetId(@Nonnull String... candidates) {
        try {
            for (String effectId : candidates) {
                if (EntityEffect.getAssetMap().getAsset(effectId) != null) {
                    return effectId;
                }
            }

            Map<String, ?> effectMap = EntityEffect.getAssetMap().getAssetMap();
            for (String effectId : candidates) {
                String lowerSuffix = effectId.toLowerCase(Locale.ROOT);
                for (String key : effectMap.keySet()) {
                    if (key != null && key.toLowerCase(Locale.ROOT).endsWith(lowerSuffix)) {
                        return key;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Nullable
    private static String findNearestTintEffectAssetId(int desiredRgb) {
        try {
            Map<String, ?> effectMap = EntityEffect.getAssetMap().getAssetMap();

            String bestId = null;
            long bestDistance = Long.MAX_VALUE;

            int desiredR = (desiredRgb >> 16) & 0xFF;
            int desiredG = (desiredRgb >> 8) & 0xFF;
            int desiredB = desiredRgb & 0xFF;

            for (String key : effectMap.keySet()) {
                if (key == null) {
                    continue;
                }

                int marker = key.lastIndexOf("HtTint_");
                if (marker < 0 || marker + 13 > key.length()) {
                    continue;
                }

                String hex = key.substring(marker + "HtTint_".length(), marker + 13);
                if (!hex.matches("[0-9A-Fa-f]{6}")) {
                    continue;
                }

                int rgb = Integer.parseInt(hex, 16) & 0xFFFFFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                long dr = desiredR - r;
                long dg = desiredG - g;
                long db = desiredB - b;
                long distance = dr * dr + dg * dg + db * db;

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestId = key;
                    if (distance == 0L) {
                        break;
                    }
                }
            }

            return bestId;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int quantizeTintRgb(int rgb) {
        return (quantizeTintChannel((rgb >> 16) & 0xFF) << 16)
                | (quantizeTintChannel((rgb >> 8) & 0xFF) << 8)
                | quantizeTintChannel(rgb & 0xFF);
    }

    private static int quantizeTintChannel(int value) {
        int[] palette = {0x00, 0x20, 0x33, 0x40, 0x60, 0x66, 0x80, 0x99, 0xA0, 0xC0, 0xCC, 0xE0, 0xFF};
        int best = palette[0];
        int bestDistance = Math.abs(value - best);

        for (int candidate : palette) {
            int distance = Math.abs(value - candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }

        return best;
    }

    private void cleanupDroppedPacketViewers(@Nonnull World world,
                                             @Nonnull UUID subjectUuid,
                                             @Nonnull Set<Integer> activeViewerIds) {
        Map<Integer, PacketGlyphState.ViewerState> snapshot = packetGlyphState.snapshotViewers(subjectUuid);
        if (snapshot.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, PacketGlyphState.ViewerState> entry : snapshot.entrySet()) {
            int viewerId = entry.getKey();
            PacketGlyphState.ViewerState viewerState = entry.getValue();

            if (activeViewerIds.contains(viewerId)) {
                continue;
            }

            PlayerRef playerRef = findPlayerRef(world, viewerState.viewerUuid);

            if (playerRef != null && !viewerState.spawnedIds.isEmpty()) {
                PacketGlyphSender.removeGlyphs(playerRef, viewerState.spawnedIds);
            }

            packetGlyphState.removeViewer(subjectUuid, viewerId);
        }
    }

    private void removePacketGlyphsForViewer(@Nonnull UUID subjectUuid,
                                             @Nonnull World world,
                                             @Nonnull UUID viewerUuid) {
        Map<Integer, PacketGlyphState.ViewerState> snapshot = packetGlyphState.snapshotViewers(subjectUuid);
        if (snapshot.isEmpty()) {
            return;
        }

        PlayerRef viewer = findPlayerRef(world, viewerUuid);
        for (Map.Entry<Integer, PacketGlyphState.ViewerState> entry : snapshot.entrySet()) {
            PacketGlyphState.ViewerState viewerState = entry.getValue();
            if (viewerState == null || !viewerUuid.equals(viewerState.viewerUuid)) {
                continue;
            }

            if (viewer != null && !viewerState.spawnedIds.isEmpty()) {
                PacketGlyphSender.removeGlyphs(viewer, viewerState.spawnedIds);
            }
            packetGlyphState.removeViewer(subjectUuid, entry.getKey());
        }
    }

    private void despawnAll(@Nonnull Store<EntityStore> store,
                            @Nonnull RenderState state) {

        try {
            Universe universe = Universe.get();

            if (universe != null) {
                Map<Integer, PacketGlyphState.ViewerState> snapshot =
                        packetGlyphState.snapshotViewers(state.subjectUuid);

                for (World world : universe.getWorlds().values()) {
                    if (world == null || !world.isAlive()) {
                        continue;
                    }

                    for (PacketGlyphState.ViewerState viewerState : snapshot.values()) {
                        PlayerRef viewer = findPlayerRef(world, viewerState.viewerUuid);
                        if (viewer != null && !viewerState.spawnedIds.isEmpty()) {
                            PacketGlyphSender.removeGlyphs(viewer, viewerState.spawnedIds);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        packetGlyphState.clearSubject(state.subjectUuid);

        for (LineRenderState line : state.lines) {
            if (line == null) continue;

            if (line.anchorRef != null && line.anchorRef.isValid()) {
                removeEntity(store, line.anchorRef);
                line.anchorRef = null;
            }

            line.glyphChars.clear();
            line.glyphTexturePaths.clear();
            line.glyphOffsets.clear();
            line.glyphTintEffectIndexes.clear();
        }

        state.lines.clear();
    }

    @Nullable
    private static PlayerRef findPlayerRef(@Nonnull World world,
                                           @Nonnull Ref<EntityStore> entityRef) {
        try {
            for (PlayerRef player : world.getPlayerRefs()) {
                if (player == null) {
                    continue;
                }

                Ref<EntityStore> ref = player.getReference();
                if (ref != null && ref.equals(entityRef)) {
                    return player;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Nullable
    private static PlayerRef findPlayerRef(@Nonnull World world, @Nonnull UUID uuid) {
        try {
            for (PlayerRef player : world.getPlayerRefs()) {
                if (player == null) {
                    continue;
                }

                UUID playerUuid = player.getUuid();
                if (uuid.equals(playerUuid)) {
                    return player;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static final class RenderState {
        final UUID subjectUuid;
        final List<LineRenderState> lines = new ArrayList<>();

        String lastText = null;
        String lastGlyphFont = null;
        double scale = 1.0d;
        String worldName = null;
        int packetGeneration = 0;

        RenderState(@Nonnull UUID subjectUuid) {
            this.subjectUuid = subjectUuid;
        }
    }

    private static final class LineRenderState {
        final List<Character> glyphChars = new ArrayList<>();
        final List<String> glyphTexturePaths = new ArrayList<>();
        final List<Double> glyphOffsets = new ArrayList<>();
        final List<Integer> glyphTintEffectIndexes = new ArrayList<>();
        final List<GlyphRunState> glyphRuns = new ArrayList<>();

        String text = "";
        String glyphFont = GlyphAssets.DEFAULT_FONT;
        double yOffset = 0.0d;
        Ref<EntityStore> anchorRef = null;

        /**
         * Set when this line is a banner image rather than text. The whole line is then one
         * entity carrying this model, so the per-character machinery above stays empty.
         */
        com.hypixel.hytale.protocol.Model bannerModel = null;

        /**
         * Per-line entity scale multiplier. Banners size themselves this way so one bundled quad
         * per aspect ratio covers every banner, without resizing a name line rendered beside it.
         */
        float scaleMultiplier = 1.0f;
    }

    private static final class LineBuildResult {
        int glyphsAdded = 0;
        boolean attemptedVisibleGlyph = false;
    }

    private static final class GlyphRunState {
        final int startInclusive;
        final int endExclusive;
        final Integer tintEffectIndex;
        final com.hypixel.hytale.protocol.Model packetModel;

        GlyphRunState(int startInclusive,
                      int endExclusive,
                      @Nullable Integer tintEffectIndex,
                      @Nonnull com.hypixel.hytale.protocol.Model packetModel) {
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            this.tintEffectIndex = tintEffectIndex;
            this.packetModel = packetModel;
        }
    }

    private static final class ColoredChar {
        final char ch;
        final Color color;

        ColoredChar(char ch, Color color) {
            this.ch = ch;
            this.color = color;
        }
    }

    private static final class SimpleColorParser {
        private static final Map<Character, Color> LEGACY_COLORS = new HashMap<>();
        private static final Map<String, Color> MINI_MESSAGE_COLORS = new HashMap<>();

        static {
            LEGACY_COLORS.put('0', Color.BLACK);
            LEGACY_COLORS.put('1', new Color(0x00, 0x00, 0xA0));
            LEGACY_COLORS.put('2', new Color(0x00, 0xA0, 0x00));
            LEGACY_COLORS.put('3', new Color(0x00, 0xA0, 0xA0));
            LEGACY_COLORS.put('4', new Color(0xA0, 0x00, 0x00));
            LEGACY_COLORS.put('5', new Color(0xA0, 0x00, 0xA0));
            LEGACY_COLORS.put('6', new Color(0xFF, 0xA0, 0x00));
            LEGACY_COLORS.put('7', new Color(0xA0, 0xA0, 0xA0));
            LEGACY_COLORS.put('8', new Color(0x60, 0x60, 0x60));
            LEGACY_COLORS.put('9', new Color(0x60, 0x60, 0xFF));
            LEGACY_COLORS.put('a', new Color(0x60, 0xFF, 0x60));
            LEGACY_COLORS.put('b', new Color(0x60, 0xFF, 0xFF));
            LEGACY_COLORS.put('c', new Color(0xFF, 0x60, 0x60));
            LEGACY_COLORS.put('d', new Color(0xFF, 0x60, 0xFF));
            LEGACY_COLORS.put('e', new Color(0xFF, 0xFF, 0x60));
            LEGACY_COLORS.put('f', Color.WHITE);

            MINI_MESSAGE_COLORS.put("black", Color.BLACK);
            MINI_MESSAGE_COLORS.put("dark_blue", new Color(0x00, 0x00, 0xA0));
            MINI_MESSAGE_COLORS.put("dark_green", new Color(0x00, 0xA0, 0x00));
            MINI_MESSAGE_COLORS.put("dark_aqua", new Color(0x00, 0xA0, 0xA0));
            MINI_MESSAGE_COLORS.put("dark_red", new Color(0xA0, 0x00, 0x00));
            MINI_MESSAGE_COLORS.put("dark_purple", new Color(0xA0, 0x00, 0xA0));
            MINI_MESSAGE_COLORS.put("gold", new Color(0xFF, 0xA0, 0x00));
            MINI_MESSAGE_COLORS.put("gray", new Color(0xA0, 0xA0, 0xA0));
            MINI_MESSAGE_COLORS.put("grey", new Color(0xA0, 0xA0, 0xA0));
            MINI_MESSAGE_COLORS.put("dark_gray", new Color(0x60, 0x60, 0x60));
            MINI_MESSAGE_COLORS.put("dark_grey", new Color(0x60, 0x60, 0x60));
            MINI_MESSAGE_COLORS.put("blue", new Color(0x60, 0x60, 0xFF));
            MINI_MESSAGE_COLORS.put("green", new Color(0x60, 0xFF, 0x60));
            MINI_MESSAGE_COLORS.put("aqua", new Color(0x60, 0xFF, 0xFF));
            MINI_MESSAGE_COLORS.put("red", new Color(0xFF, 0x60, 0x60));
            MINI_MESSAGE_COLORS.put("light_purple", new Color(0xFF, 0x60, 0xFF));
            MINI_MESSAGE_COLORS.put("purple", new Color(0xFF, 0x60, 0xFF));
            MINI_MESSAGE_COLORS.put("magenta", new Color(0xFF, 0x60, 0xFF));
            MINI_MESSAGE_COLORS.put("pink", new Color(0xFF, 0x60, 0xFF));
            MINI_MESSAGE_COLORS.put("yellow", new Color(0xFF, 0xFF, 0x60));
            MINI_MESSAGE_COLORS.put("white", Color.WHITE);
        }

        static List<ColoredChar> parse(String text) {
            List<ColoredChar> out = new ArrayList<>();
            if (text == null || text.isEmpty()) return out;

            text = ColorFormatter.colorizeForGlyphNameplate(text);
            Color current = Color.WHITE;

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);

                if ((c == '&' || c == '§') && i + 7 < text.length() && text.charAt(i + 1) == '#') {
                    Color parsed = GlyphAssets.tryParseHex6(text.substring(i + 2, i + 8));
                    if (parsed != null) current = parsed;
                    i += 7;
                    continue;
                }

                if ((c == '&' || c == '§') && i + 13 < text.length() && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X')) {
                    StringBuilder hex = new StringBuilder(6);
                    boolean valid = true;
                    for (int j = i + 2; j <= i + 12; j += 2) {
                        if (j + 1 >= text.length()) {
                            valid = false;
                            break;
                        }
                        char marker = text.charAt(j);
                        char digit = text.charAt(j + 1);
                        if ((marker != '&' && marker != '§') || !isHexDigit(digit)) {
                            valid = false;
                            break;
                        }
                        hex.append(digit);
                    }
                    if (valid) {
                        Color parsed = GlyphAssets.tryParseHex6(hex.toString());
                        if (parsed != null) current = parsed;
                        i += 13;
                        continue;
                    }
                }

                if ((c == '&' || c == '§') && i + 1 < text.length()) {
                    char code = Character.toLowerCase(text.charAt(i + 1));
                    if (LEGACY_COLORS.containsKey(code)) {
                        current = LEGACY_COLORS.get(code);
                        i += 1;
                        continue;
                    }
                    if (code == 'r') {
                        current = Color.WHITE;
                        i += 1;
                        continue;
                    }
                    if ("klmno".indexOf(code) >= 0) {
                        i += 1;
                        continue;
                    }
                }

                if (c == '<') {
                    int end = text.indexOf('>', i);
                    if (end > i) {
                        String tag = text.substring(i + 1, end).trim().toLowerCase(Locale.ROOT);
                        if (tag.startsWith("/") || tag.equals("reset")) {
                            current = Color.WHITE;
                            i = end;
                            continue;
                        }

                        if (tag.equals("bold") || tag.equals("b")
                                || tag.equals("strong")
                                || tag.equals("italic") || tag.equals("i")
                                || tag.equals("em")
                                || tag.equals("underlined") || tag.equals("underline")
                                || tag.equals("u")
                                || tag.equals("strikethrough") || tag.equals("strike")
                                || tag.equals("st")
                                || tag.equals("obfuscated") || tag.equals("obfuscate")
                                || tag.equals("obf")
                                || tag.startsWith("gradient:")) {
                            i = end;
                            continue;
                        }

                        String hexTag = normalizeMiniMessageHexTag(tag);
                        if (hexTag != null) {
                            Color parsed = GlyphAssets.tryParseHex6(hexTag);
                            if (parsed != null) current = parsed;
                            i = end;
                            continue;
                        }

                        Color named = MINI_MESSAGE_COLORS.get(tag);
                        if (named != null) {
                            current = named;
                            i = end;
                            continue;
                        }
                    }
                }

                out.add(new ColoredChar(c, current));
            }

            return out;
        }

        private static boolean isHexDigit(char c) {
            return (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
        }

        @Nullable
        private static String normalizeMiniMessageHexTag(@Nonnull String tag) {
            String value = tag;
            int colon = tag.indexOf(':');
            if (colon >= 0) {
                String prefix = tag.substring(0, colon);
                if ("color".equals(prefix) || "colour".equals(prefix) || "c".equals(prefix)) {
                    value = tag.substring(colon + 1).trim();
                }
            }

            if (!value.startsWith("#")) {
                return null;
            }

            String hex = value.substring(1);
            if (hex.matches("[0-9a-fA-F]{3}")) {
                return "" + hex.charAt(0) + hex.charAt(0)
                        + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            }

            return hex.matches("[0-9a-fA-F]{6}") ? hex : null;
        }
    }

    /**
     * Removes a glyph/anchor entity from the ECS.
     *
     * <p>Update 6 settled {@code Store#removeEntity(Ref, RemoveReason)} as public API, so the
     * old reflective probe over five candidate signatures is gone. A failure here is logged
     * rather than swallowed: silently leaking anchor entities is what the probe used to do
     * once every candidate missed.
     */
    private static void removeEntity(@Nonnull Store<EntityStore> store,
                                     @Nonnull Ref<EntityStore> ref) {
        try {
            store.removeEntity(ref, RemoveReason.REMOVE);
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t)
                    .log("[MysticNameTags] Failed to remove glyph entity");
        }
    }

    private static Color scaleColor(@Nonnull Color color, double factor) {
        factor = Math.max(0.0d, Math.min(1.0d, factor));

        int r = (int) Math.round(color.getRed() * factor);
        int g = (int) Math.round(color.getGreen() * factor);
        int b = (int) Math.round(color.getBlue() * factor);

        return new Color(
                Math.max(0, Math.min(255, r)),
                Math.max(0, Math.min(255, g)),
                Math.max(0, Math.min(255, b))
        );
    }
}
