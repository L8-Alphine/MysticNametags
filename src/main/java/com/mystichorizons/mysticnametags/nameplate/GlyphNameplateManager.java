package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.EntityUpdate;
import com.hypixel.hytale.protocol.ModelAttachment;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
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
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphAssets;
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphInfoCompat;
import com.mystichorizons.mysticnametags.nameplate.packet.PacketGlyphIdFactory;
import com.mystichorizons.mysticnametags.nameplate.packet.PacketGlyphSender;
import com.mystichorizons.mysticnametags.nameplate.packet.PacketGlyphState;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class GlyphNameplateManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final GlyphNameplateManager INSTANCE = new GlyphNameplateManager();

    private static final double ANCHOR_Y_OFFSET = 2.25d;

    private static final float BILLBOARD_YAW_DIRTY_DEGREES = 0.75f;
    private static final int POST_SPAWN_CORRECTION_UPDATES = 3;

    private static final float GLYPH_YAW_CORRECTION_DEGREES = 0f;

    private static final double GLYPH_EXTRA_SPACING_PX = 4.0d;
    private static final double GLYPH_SOURCE_WIDTH_PX = 16.0d;
    private static final double GLYPH_RUN_SLOT_UNITS_PER_BLOCK = 64.0d;

    private final Map<UUID, RenderState> states = new ConcurrentHashMap<>();
    private final PacketGlyphState packetGlyphState = new PacketGlyphState();
    private final Set<String> loggedPacketSpawns = ConcurrentHashMap.newKeySet();
    private final Set<Character> loggedMissingGlyphModels = ConcurrentHashMap.newKeySet();
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
        text = ColorFormatter.miniToLegacy(text);

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

    private static float normalizeDegrees(float yaw) {
        float out = yaw % 360f;
        if (out < 0f) out += 360f;
        return out;
    }

    private static float normalizeRadians(float yaw) {
        float twoPi = (float) (Math.PI * 2.0);
        float out = yaw % twoPi;
        if (out < 0f) out += twoPi;
        return out;
    }

    private static float angleDeltaDegrees(float a, float b) {
        return ((a - b + 540.0f) % 360.0f) - 180.0f;
    }

    private static float toDegreesForCompare(float yaw, boolean nativeLooksLikeDegrees) {
        return nativeLooksLikeDegrees ? normalizeDegrees(yaw) : normalizeDegrees((float) Math.toDegrees(yaw));
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

        RenderState state = states.computeIfAbsent(uuid, RenderState::new);

        String previousWorldName = state.worldName;
        boolean worldChanged = previousWorldName != null && !Objects.equals(previousWorldName, world.getName());

        boolean needsRebuild =
                worldChanged
                        || !Objects.equals(state.lastText, clamped)
                        || !hasLiveRender(state);

        if (needsRebuild) {
            boolean rebuilt = rebuild(world, store, playerRef, state, clamped, settings);
            if (!rebuilt) {
                state.lastText = null;
                state.worldName = world.getName();
                return;
            }

            state.lastText = clamped;
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

        despawnAll(store, world.getEntityStore(), state);
    }

    public void remove(@Nonnull UUID uuid, @Nonnull World world) {
        RenderState state = states.remove(uuid);
        if (state == null) return;

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            store.assertThread();
            despawnAll(store, world.getEntityStore(), state);
        });
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
                            try {
                                EntityRemoveCompat.remove(store, world.getEntityStore(), line.anchorRef);
                            } catch (Throwable ignored) {
                            }
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

        despawnAll(store, world.getEntityStore(), state);
        state.lines.clear();
        state.packetGeneration++;

        TransformComponent playerTx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTx == null) {
            return false;
        }

        Vector3f playerRot = playerTx.getTransform().getRotation();

        state.yawNativeLooksLikeDegrees = RotationCompat.looksLikeDegrees(playerRot.getY());

        List<String> logicalLines = splitLines(text, settings.getExperimentalGlyphMaxLines());
        if (logicalLines.isEmpty()) {
            logicalLines = Collections.singletonList("");
        }

        double lineSpacing = settings.getExperimentalGlyphLineSpacing();
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
            lineState.yOffset = lineIndex * lineSpacing;

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
                if (spawnedCount >= hardCap) break;
                if (!GlyphInfoCompat.isSupported(ch)) continue;

                spawnAttemptedForVisibleGlyph = true;

                String assetId = resolveGlyphModelId(ch);
                if (assetId == null) {
                    if (loggedMissingGlyphModels.add(ch)) {
                        LOGGER.at(Level.INFO).log("[MysticNameTags] Packet glyph model not found for char='"
                                + ch + "' candidates=" + Arrays.toString(GlyphInfoCompat.getModelAssetIdCandidates(ch)));
                    }
                    continue;
                }

                com.hypixel.hytale.protocol.Model packetModel = resolveGlyphModelPacket(assetId);
                if (packetModel == null) {
                    if (loggedMissingGlyphModels.add(ch)) {
                        LOGGER.at(Level.INFO).log("[MysticNameTags] Packet glyph model could not convert to packet for char='"
                                + ch + "', asset=" + assetId);
                    }
                    continue;
                }

                lineState.glyphChars.add(ch);
                lineState.glyphAssetIds.add(assetId);
                lineState.glyphModels.add(packetModel);
                lineState.glyphOffsets.add(offset);
                lineState.glyphTintEffectIndexes.add(resolveTintEffectIndex(scaleColor(cc.color, settings.getExperimentalGlyphTintStrength())));

                spawnedCount++;
                spawnedAnyGlyph = true;
            }

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
        Vector3f playerRot = playerTx.getTransform().getRotation();

        boolean looksDegrees = state.yawNativeLooksLikeDegrees != null
                ? state.yawNativeLooksLikeDegrees
                : RotationCompat.looksLikeDegrees(playerRot.getY());

        if (state.yawNativeLooksLikeDegrees == null) {
            state.yawNativeLooksLikeDegrees = looksDegrees;
        }

        float playerYaw = looksDegrees ? normalizeDegrees(playerRot.getY()) : normalizeRadians(playerRot.getY());
        float playerYawDegrees = toDegreesForCompare(playerYaw, looksDegrees);

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

                float yaw;

                if (selfView) {
                    yaw = RotationCompat.addYawNative(playerRot.getY(), 180f, looksDegrees);
                    yaw = looksDegrees ? normalizeDegrees(yaw) : normalizeRadians(yaw);
                } else {
                    TransformComponent viewerTx = store.getComponent(viewerRef, TransformComponent.getComponentType());
                    if (viewerTx == null) continue;

                    Vector3d viewerPos = viewerTx.getTransform().getPosition();
                    double dx = viewerPos.getX() - playerPos.getX();
                    double dz = viewerPos.getZ() - playerPos.getZ();

                    yaw = (float) Math.atan2(-dx, -dz);
                    yaw = looksDegrees ? normalizeDegrees((float) Math.toDegrees(yaw)) : normalizeRadians(yaw);
                }

                yaw = RotationCompat.addYawNative(yaw, GLYPH_YAW_CORRECTION_DEGREES, looksDegrees);

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

                    int viewerId = viewerIdentity(store, viewerRef);
                    activeViewerIds.add(viewerId);

                    long now = System.nanoTime();
                    float yawDegrees = toDegreesForCompare(yaw, looksDegrees);

                    PacketGlyphState.ViewerState packetState =
                            packetGlyphState.viewer(uuid, viewerId, viewerUuid);

                    float modelScale = GlyphInfoCompat.BASE_MODEL_SCALE * (float) state.scale;
                    float lineOffsetY = (float) (ANCHOR_Y_OFFSET + line.yOffset);
                    int mountedToNetworkId = playerNetworkId.getId();
                    double anchorX = playerPos.getX();
                    double anchorY = playerPos.getY() + lineOffsetY;
                    double anchorZ = playerPos.getZ();
                    float glyphYaw = yaw;
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
                    boolean yawDirty = Float.isNaN(packetState.lastYawDegrees)
                            || Math.abs(angleDeltaDegrees(yawDegrees, packetState.lastYawDegrees)) >= BILLBOARD_YAW_DIRTY_DEGREES;
                    boolean parentYawDirty = Float.isNaN(packetState.lastParentYawDegrees)
                            || Math.abs(angleDeltaDegrees(playerYawDegrees, packetState.lastParentYawDegrees)) >= BILLBOARD_YAW_DIRTY_DEGREES;
                    boolean positionDirty = Double.isNaN(packetState.lastBaseX)
                            || playerPos.getX() != packetState.lastBaseX
                            || playerPos.getY() != packetState.lastBaseY
                            || playerPos.getZ() != packetState.lastBaseZ;
                    long updateIntervalNs = Math.max(1L,
                            (long) Settings.get().getExperimentalGlyphRotationSyncIntervalMs()) * 1_000_000L;
                    boolean intervalReady = now >= packetState.nextUpdateAtNs;
                    boolean glyphNeedsUpdate = !packetState.spawnedIds.isEmpty()
                            && (postSpawnCorrection || yawDirty || parentYawDirty || positionDirty)
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
                                    line.glyphAssetIds.isEmpty() ? "none" : line.glyphAssetIds.get(0));

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

                            packetState.postSpawnCorrectionsRemaining = Math.max(
                                    packetState.postSpawnCorrectionsRemaining,
                                    POST_SPAWN_CORRECTION_UPDATES
                            );
                            packetState.nextUpdateAtNs = 0L;
                        }
                    }

                    if (lineIndex + 1 >= state.lines.size()) {
                        packetState.lastYawDegrees = yawDegrees;
                        packetState.lastParentYawDegrees = playerYawDegrees;
                        packetState.lastBaseX = playerPos.getX();
                        packetState.lastBaseY = playerPos.getY();
                        packetState.lastBaseZ = playerPos.getZ();
                        if (glyphNeedsUpdate && postSpawnCorrection) {
                            packetState.postSpawnCorrectionsRemaining--;
                            packetState.nextUpdateAtNs = now + Math.min(updateIntervalNs, 1_000_000L);
                        } else if (glyphNeedsUpdate) {
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

        int count = Math.min(
                Math.min(Math.min(line.glyphChars.size(), line.glyphOffsets.size()), line.glyphTintEffectIndexes.size()),
                line.glyphAssetIds.size()
        );

        int start = -1;
        Integer currentTint = null;

        for (int i = 0; i < count; i++) {
            Integer tint = line.glyphTintEffectIndexes.get(i);
            if (start < 0) {
                start = i;
                currentTint = tint;
                continue;
            }

            if (!Objects.equals(currentTint, tint)) {
                addLineRun(line, start, i, currentTint, scale);
                start = i;
                currentTint = tint;
            }
        }

        if (start >= 0) {
            addLineRun(line, start, count, currentTint, scale);
        }
    }

    private static void addLineRun(@Nonnull LineRenderState line,
                                   int startInclusive,
                                   int endExclusive,
                                   @Nullable Integer tintEffectIndex,
                                   double scale) {
        if (startInclusive >= endExclusive) {
            return;
        }

        com.hypixel.hytale.protocol.Model model = buildLineRunPacketModel(line, startInclusive, endExclusive, scale);
        if (model == null) {
            return;
        }

        line.glyphRuns.add(new GlyphRunState(startInclusive, endExclusive, tintEffectIndex, model));
    }

    @Nullable
    private static com.hypixel.hytale.protocol.Model buildLineRunPacketModel(@Nonnull LineRenderState line,
                                                                             int startInclusive,
                                                                             int endExclusive,
                                                                             double scale) {
        com.hypixel.hytale.protocol.Model model = resolveGlyphLineBasePacket();
        if (model == null) {
            model = new com.hypixel.hytale.protocol.Model();
            model.assetId = GlyphAssets.NAMESPACE + ":GlyphLineBase";
            model.path = "NPC/MysticNameTags/GlyphLineBase.blockymodel";
            model.texture = "NPC/MysticNameTags/glyph_fallback.png";
        } else {
            model = new com.hypixel.hytale.protocol.Model(model);
        }

        List<ModelAttachment> attachments = new ArrayList<>(Math.max(0, endExclusive - startInclusive));
        double safeScale = Math.max(0.0001d, scale);

        for (int i = startInclusive; i < endExclusive; i++) {
            char ch = line.glyphChars.get(i);
            String safeId = GlyphInfoCompat.getSafeIdLower(ch);
            if (safeId == null) {
                continue;
            }

            double offset = line.glyphOffsets.get(i);
            int offsetPx = (int) Math.round((-offset / safeScale) * GLYPH_RUN_SLOT_UNITS_PER_BLOCK);
            String slotModel = GlyphAssets.slotModelPath(offsetPx);
            String texture = GlyphAssets.texturePath(ch, safeId);
            attachments.add(new ModelAttachment(slotModel, texture, null, null));
        }

        if (attachments.isEmpty()) {
            return null;
        }

        model.attachments = attachments.toArray(new ModelAttachment[0]);
        return model;
    }

    @Nullable
    private static com.hypixel.hytale.protocol.Model resolveGlyphLineBasePacket() {
        try {
            ModelAsset asset = (ModelAsset) ModelAsset.getAssetMap().getAsset(GlyphAssets.NAMESPACE + ":GlyphLineBase");
            if (asset == null) {
                asset = (ModelAsset) ModelAsset.getAssetMap().getAsset("GlyphLineBase");
            }
            if (asset == null) {
                return null;
            }

            com.hypixel.hytale.server.core.asset.type.model.config.Model model =
                    com.hypixel.hytale.server.core.asset.type.model.config.Model.createUnitScaleModel(asset);
            return model == null ? null : model.toPacket();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static String resolveGlyphModelId(char ch) {
        try {
            String[] candidates = GlyphInfoCompat.getModelAssetIdCandidates(ch);
            if (candidates == null || candidates.length == 0) {
                return null;
            }

            for (String id : candidates) {
                if (id == null || id.isEmpty()) {
                    continue;
                }

                ModelAsset asset = (ModelAsset) ModelAsset.getAssetMap().getAsset(id);
                if (asset != null) {
                    return id;
                }
            }

            String shortName = candidates[0];
            int colon = shortName.lastIndexOf(':');
            if (colon >= 0) {
                shortName = shortName.substring(colon + 1);
            }

            String lowerShortName = shortName.toLowerCase(Locale.ROOT);

            for (Map.Entry<String, ?> entry : ModelAsset.getAssetMap().getAssetMap().entrySet()) {
                String key = entry.getKey();
                if (key != null && key.toLowerCase(Locale.ROOT).endsWith(lowerShortName)) {
                    return key;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Nullable
    private static com.hypixel.hytale.protocol.Model resolveGlyphModelPacket(@Nonnull String assetId) {
        try {
            ModelAsset asset = (ModelAsset) ModelAsset.getAssetMap().getAsset(assetId);
            if (asset == null) {
                return null;
            }

            com.hypixel.hytale.server.core.asset.type.model.config.Model model =
                    com.hypixel.hytale.server.core.asset.type.model.config.Model.createUnitScaleModel(asset);
            return model == null ? null : model.toPacket();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private Integer resolveTintEffectIndex(@Nonnull Color color) {
        int rgb = quantizeTintRgb(GlyphAssets.rgb(color));

        return tintEffectIndexCache.computeIfAbsent(rgb, key -> {
            String shortEffectId = "HtTint_" + String.format("%06X", (key & 0xFFFFFF));
            String namespacedEffectId = GlyphAssets.tintEffectId(key);

            try {
                for (String effectId : new String[]{shortEffectId, namespacedEffectId}) {
                    if (EntityEffect.getAssetMap().getAsset(effectId) != null) {
                        return EntityEffect.getAssetMap().getIndex(effectId);
                    }
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

    private void despawnAll(@Nonnull Store<EntityStore> store,
                            @Nonnull EntityStore entityStore,
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
                try {
                    EntityRemoveCompat.remove(store, entityStore, line.anchorRef);
                } catch (Throwable ignored) {
                }
                line.anchorRef = null;
            }

            line.glyphChars.clear();
            line.glyphAssetIds.clear();
            line.glyphModels.clear();
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
        double scale = 1.0d;
        String worldName = null;
        Boolean yawNativeLooksLikeDegrees = null;
        int packetGeneration = 0;

        RenderState(@Nonnull UUID subjectUuid) {
            this.subjectUuid = subjectUuid;
        }
    }

    private static final class LineRenderState {
        final List<Character> glyphChars = new ArrayList<>();
        final List<String> glyphAssetIds = new ArrayList<>();
        final List<com.hypixel.hytale.protocol.Model> glyphModels = new ArrayList<>();
        final List<Double> glyphOffsets = new ArrayList<>();
        final List<Integer> glyphTintEffectIndexes = new ArrayList<>();
        final List<GlyphRunState> glyphRuns = new ArrayList<>();

        String text = "";
        double yOffset = 0.0d;
        Ref<EntityStore> anchorRef = null;
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
        }

        static List<ColoredChar> parse(String text) {
            List<ColoredChar> out = new ArrayList<>();
            if (text == null || text.isEmpty()) return out;

            text = ColorFormatter.miniToLegacy(text);
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
                        if (tag.startsWith("#") && tag.length() == 7) {
                            Color parsed = GlyphAssets.tryParseHex6(tag.substring(1));
                            if (parsed != null) current = parsed;
                            i = end;
                            continue;
                        }
                        if (tag.equals("/") || tag.equals("reset")) {
                            current = Color.WHITE;
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
    }

    private static final class MountCompat {
        private static Class<?> mountedClass;
        private static Constructor<?> mountedConstructor;
        private static Object defaultController;
        private static Method getComponentTypeMethod;
        private static Method putComponentMethod;

        static {
            try {
                mountedClass = Class.forName("com.hypixel.hytale.builtin.mounts.MountedComponent");
                Class<?> controllerClass = Class.forName("com.hypixel.hytale.protocol.MountController");

                for (Object c : controllerClass.getEnumConstants()) {
                    String name = c.toString().toUpperCase(Locale.ROOT);
                    if ("NONE".equals(name)) {
                        defaultController = c;
                        break;
                    }
                }
                if (defaultController == null) defaultController = controllerClass.getEnumConstants()[0];

                mountedConstructor = mountedClass.getConstructor(Ref.class, Vector3f.class, controllerClass);
                getComponentTypeMethod = mountedClass.getMethod("getComponentType");

                for (Method m : Holder.class.getMethods()) {
                    if (m.getName().equals("putComponent") && m.getParameterCount() == 2) {
                        putComponentMethod = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        static boolean isSupported() {
            return mountedClass != null
                    && mountedConstructor != null
                    && getComponentTypeMethod != null
                    && putComponentMethod != null;
        }

        static boolean mount(Holder holder, Ref<EntityStore> target, Vector3f offset) {
            if (!isSupported()) return false;
            try {
                Object comp = mountedConstructor.newInstance(target, offset, defaultController);
                Object compType = getComponentTypeMethod.invoke(null);
                putComponentMethod.invoke(holder, compType, comp);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static final class EntityRemoveCompat {
        @SuppressWarnings({"unchecked", "rawtypes"})
        static void remove(@Nonnull Store<EntityStore> store,
                           @Nonnull EntityStore entityStore,
                           @Nonnull Ref<EntityStore> ref) {
            try {
                Class<?> rr = Class.forName("com.hypixel.hytale.component.RemoveReason");
                Object remove = Enum.valueOf((Class<? extends Enum>) rr.asSubclass(Enum.class), "REMOVE");

                if (tryInvoke(store, "removeEntity", new Class[]{Ref.class, rr}, ref, remove)) return;
                if (tryInvoke(entityStore, "removeEntity", new Class[]{Ref.class, rr}, ref, remove)) return;
            } catch (Throwable ignored) {
            }

            if (tryInvoke(store, "removeEntity", new Class[]{Ref.class, Object.class}, ref, null)) return;
            if (tryInvoke(store, "removeEntity", new Class[]{Ref.class}, ref)) return;
            if (tryInvoke(store, "deleteEntity", new Class[]{Ref.class}, ref)) return;
            if (tryInvoke(entityStore, "removeEntity", new Class[]{Ref.class, Object.class}, ref, null)) return;
            if (tryInvoke(entityStore, "removeEntity", new Class[]{Ref.class}, ref)) return;
        }

        private static boolean tryInvoke(Object target, String name, Class<?>[] sig, Object... args) {
            try {
                Method m = target.getClass().getMethod(name, sig);
                m.invoke(target, args);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    private static final class RotationCompat {
        static boolean looksLikeDegrees(float yaw) {
            return Math.abs(yaw) > 6.4f;
        }

        static float addYawNative(float yawNative, float addDegrees, boolean looksDegrees) {
            return (float) (looksDegrees ? yawNative + addDegrees : yawNative + Math.toRadians(addDegrees));
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
