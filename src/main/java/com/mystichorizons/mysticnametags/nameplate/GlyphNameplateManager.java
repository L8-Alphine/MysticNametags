package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.EntityUpdate;
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

    private static final float BILLBOARD_YAW_DIRTY_DEGREES = 2.0f;
    private static final double BILLBOARD_POS_DIRTY_SQ = 0.0004d;
    private static final long BILLBOARD_MIN_UPDATE_INTERVAL_MS = 25L;
    private static final long BILLBOARD_FORCE_UPDATE_INTERVAL_MS = 250L;

    private static final float GLYPH_YAW_CORRECTION_DEGREES = 0f;

    private static final double GLYPH_EXTRA_SPACING_PX = 4.0d;
    private static final double GLYPH_SOURCE_WIDTH_PX = 16.0d;

    private final Map<UUID, RenderState> states = new ConcurrentHashMap<>();
    private final PacketGlyphState packetGlyphState = new PacketGlyphState();

    private GlyphNameplateManager() {
    }

    public static GlyphNameplateManager get() {
        return INSTANCE;
    }

    private static boolean hasLiveRender(@Nullable RenderState state) {
        if (state == null) return false;

        for (LineRenderState line : state.lines) {
            if (line == null) continue;
            if (line.anchorRef != null && line.anchorRef.isValid()) {
                return true;
            }
        }

        return false;
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

    private static double billboardRightX(float yaw, boolean looksDegrees) {
        double radians = looksDegrees ? Math.toRadians(yaw) : yaw;
        return Math.cos(radians);
    }

    private static double billboardRightZ(float yaw, boolean looksDegrees) {
        double radians = looksDegrees ? Math.toRadians(yaw) : yaw;
        return -Math.sin(radians);
    }

    private static double distSq(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
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
     * <p>The method clears tracking state immediately (so the follow-task
     * stops touching this player) and then enqueues a lightweight anchor-
     * entity removal on the world thread.  Unlike {@link #remove(UUID, World)},
     * it skips the cross-world viewer iteration and packet writes — the
     * disconnecting player's connection is already being torn down so those
     * packets would either fail or be pointless.</p>
     *
     * <p>The outer lambda is wrapped in {@code catch(Throwable)} so that an
     * unexpected {@code Error} cannot kill the world's ticking thread and
     * stall the subsequent {@code Player.remove()} task (which caused the
     * 5-second timeout seen in production).</p>
     */
    public void disconnectCleanup(@Nonnull UUID uuid, @Nonnull World world) {
        // 1. Pull state atomically — follow task will no longer see this player
        RenderState state = states.remove(uuid);

        // 2. Clear packet-glyph bookkeeping (safe from any thread)
        packetGlyphState.clearSubject(uuid);

        if (state == null || state.lines.isEmpty()) {
            return;
        }

        // 3. Enqueue anchor entity removal on the world thread.
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

        TransformComponent playerTx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTx == null) {
            return false;
        }

        Vector3d playerPos = playerTx.getTransform().getPosition();
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

            double lineYOffset = lineIndex * lineSpacing;

            Vector3d anchorPos = new Vector3d(
                    playerPos.getX(),
                    playerPos.getY() + ANCHOR_Y_OFFSET + lineYOffset,
                    playerPos.getZ()
            );

            Vector3f anchorRot = new Vector3f(0f, 0f, 0f);

            Holder anchorHolder = EntityStore.REGISTRY.newHolder();
            anchorHolder.putComponent(TransformComponent.getComponentType(), new TransformComponent(anchorPos, anchorRot));
            anchorHolder.putComponent(NetworkId.getComponentType(), new NetworkId(world.getEntityStore().takeNextNetworkId()));
            anchorHolder.putComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
            anchorHolder.putComponent(Intangible.getComponentType(), Intangible.INSTANCE);

            try {
                anchorHolder.ensureComponent(EntityModule.get().getVisibleComponentType());
            } catch (Throwable ignored) {
            }

            if (MountCompat.isSupported()) {
                MountCompat.mount(anchorHolder, playerRef, new Vector3f(0f, (float) (ANCHOR_Y_OFFSET + lineYOffset), 0f));
            }

            Ref<EntityStore> anchorRef = store.addEntity(anchorHolder, AddReason.SPAWN);
            if (anchorRef == null || !anchorRef.isValid()) {
                continue;
            }

            LineRenderState lineState = new LineRenderState();
            lineState.anchorRef = anchorRef;
            lineState.text = lineText;
            lineState.yOffset = lineYOffset;

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
                    continue;
                }

                lineState.glyphChars.add(ch);
                lineState.glyphAssetIds.add(assetId);
                lineState.glyphOffsets.add(offset);

                spawnedCount++;
                spawnedAnyGlyph = true;
            }

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

        Vector3d playerPos = playerTx.getTransform().getPosition();
        Vector3f playerRot = playerTx.getTransform().getRotation();

        boolean looksDegrees = state.yawNativeLooksLikeDegrees != null
                ? state.yawNativeLooksLikeDegrees
                : RotationCompat.looksLikeDegrees(playerRot.getY());

        if (state.yawNativeLooksLikeDegrees == null) {
            state.yawNativeLooksLikeDegrees = looksDegrees;
        }

        Set<Integer> activeViewerIds = new HashSet<>();

        for (int lineIndex = 0; lineIndex < state.lines.size(); lineIndex++) {
            LineRenderState line = state.lines.get(lineIndex);
            if (line == null || line.anchorRef == null || !line.anchorRef.isValid()) continue;

            Visible visible = store.getComponent(line.anchorRef, EntityModule.get().getVisibleComponentType());

            Map<Ref<EntityStore>, EntityViewer> viewers = new LinkedHashMap<>();

            if (visible != null && visible.visibleTo != null) {
                viewers.putAll(visible.visibleTo);
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

                    long now = System.currentTimeMillis();
                    float yawDegrees = toDegreesForCompare(yaw, looksDegrees);

                    double baseX = playerPos.getX();
                    double baseY = playerPos.getY() + ANCHOR_Y_OFFSET + line.yOffset;
                    double baseZ = playerPos.getZ();

                    PacketGlyphState.ViewerState packetState =
                            packetGlyphState.viewer(uuid, viewerId, viewerUuid);

                    boolean yawDirty = Float.isNaN(packetState.lastYawDegrees)
                            || Math.abs(angleDeltaDegrees(yawDegrees, packetState.lastYawDegrees)) >= BILLBOARD_YAW_DIRTY_DEGREES;

                    boolean posDirty = Double.isNaN(packetState.lastBaseX)
                            || distSq(baseX, baseY, baseZ, packetState.lastBaseX, packetState.lastBaseY, packetState.lastBaseZ) >= BILLBOARD_POS_DIRTY_SQ;

                    boolean intervalReady = now >= packetState.nextUpdateAtMs;
                    boolean forceReady = now >= packetState.forceUpdateAtMs;

                    if (!yawDirty && !posDirty && !forceReady) {
                        continue;
                    }

                    if (!intervalReady && !forceReady) {
                        continue;
                    }

                    packetState.lastYawDegrees = yawDegrees;
                    packetState.lastBaseX = baseX;
                    packetState.lastBaseY = baseY;
                    packetState.lastBaseZ = baseZ;
                    packetState.nextUpdateAtMs = now + BILLBOARD_MIN_UPDATE_INTERVAL_MS;
                    packetState.forceUpdateAtMs = now + BILLBOARD_FORCE_UPDATE_INTERVAL_MS;

                    double rx = billboardRightX(yaw, looksDegrees);
                    double rz = billboardRightZ(yaw, looksDegrees);

                    float modelScale = GlyphInfoCompat.BASE_MODEL_SCALE * (float) state.scale;

                    List<EntityUpdate> spawnUpdates = new ArrayList<>();

                    int count = Math.min(
                            Math.min(line.glyphChars.size(), line.glyphAssetIds.size()),
                            line.glyphOffsets.size()
                    );

                    for (int glyphIndex = 0; glyphIndex < count; glyphIndex++) {
                        String assetId = line.glyphAssetIds.get(glyphIndex);
                        double offset = line.glyphOffsets.get(glyphIndex);

                        int fakeId = PacketGlyphIdFactory.glyphId(uuid, viewerId, lineIndex, glyphIndex);

                        double glyphX = baseX + (rx * offset);
                        double glyphY = baseY;
                        double glyphZ = baseZ + (rz * offset);

                        if (!packetState.spawnedIds.contains(fakeId)) {
                            spawnUpdates.add(PacketGlyphSender.glyphSpawnUpdate(
                                    fakeId,
                                    assetId,
                                    glyphX,
                                    glyphY,
                                    glyphZ,
                                    yaw,
                                    modelScale
                            ));
                        } else {
                            PacketGlyphSender.moveGlyph(
                                    packetViewer,
                                    fakeId,
                                    glyphX,
                                    glyphY,
                                    glyphZ,
                                    yaw
                            );
                        }
                    }

                    if (!spawnUpdates.isEmpty()) {
                        LOGGER.at(Level.INFO).log("[MysticNameTags] Sending packet glyph spawn count="
                                + spawnUpdates.size()
                                + ", subject=" + uuid
                                + ", viewer=" + viewerUuid
                                + ", selfView=" + selfView);

                        if (PacketGlyphSender.isRuntimeDisabled()) {
                            continue;
                        }

                        boolean sent = PacketGlyphSender.spawnMany(packetViewer, spawnUpdates);

                        if (sent) {
                            for (int glyphIndex = 0; glyphIndex < count; glyphIndex++) {
                                int fakeId = PacketGlyphIdFactory.glyphId(uuid, viewerId, lineIndex, glyphIndex);
                                packetState.spawnedIds.add(fakeId);
                            }
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
            line.glyphOffsets.clear();
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

        RenderState(@Nonnull UUID subjectUuid) {
            this.subjectUuid = subjectUuid;
        }
    }

    private static final class LineRenderState {
        final List<Character> glyphChars = new ArrayList<>();
        final List<String> glyphAssetIds = new ArrayList<>();
        final List<Double> glyphOffsets = new ArrayList<>();

        String text = "";
        double yOffset = 0.0d;
        Ref<EntityStore> anchorRef = null;
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