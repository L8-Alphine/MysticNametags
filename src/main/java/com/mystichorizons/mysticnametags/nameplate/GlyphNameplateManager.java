package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphAssets;
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphInfoCompat;
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
    private static final float YAW_EPSILON = 0.001f;
    private static final double POSITION_EPSILON = 0.001d;

    // Require roughly ~2 blocks improvement before switching viewers.
    private static final double VIEWER_SWITCH_BIAS_SQ = 4.0d;

    // Set to 180f only if all authored glyph assets are globally backwards.
    private static final float GLYPH_YAW_CORRECTION_DEGREES = 0f;

    private static final double GLYPH_EXTRA_SPACING_PX = 4.0d;
    private static final double GLYPH_SOURCE_WIDTH_PX = 16.0d;

    // Your current build appears to still need child yaw sync to visually match anchor rotation.
    private static final boolean FORCE_GLYPH_CHILD_ROTATION_SYNC = true;

    // Keep true if your mounted anchors do not perfectly stay aligned on all builds.
    private static final boolean FORCE_ANCHOR_POSITION_SYNC = true;

    private static final Map<Integer, String> RESOLVED_EFFECT_IDS = new ConcurrentHashMap<>();
    private final Map<UUID, RenderState> states = new ConcurrentHashMap<>();

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

            for (Ref<EntityStore> glyphRef : line.glyphRefs) {
                if (glyphRef != null && glyphRef.isValid()) {
                    return true;
                }
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

    private static double square(double d) {
        return d * d;
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

    private static boolean nearlyEqualYaw(float a, float b, boolean looksDegrees) {
        if (looksDegrees) {
            float diff = Math.abs(normalizeDegrees(a) - normalizeDegrees(b));
            diff = Math.min(diff, 360f - diff);
            return diff < YAW_EPSILON;
        } else {
            float twoPi = (float) (Math.PI * 2.0);
            float diff = Math.abs(normalizeRadians(a) - normalizeRadians(b));
            diff = Math.min(diff, twoPi - diff);
            return diff < YAW_EPSILON;
        }
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

        RenderState state = states.computeIfAbsent(uuid, ignored -> new RenderState());

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
     * Means: do we actually have a live rendered glyph anchor/glyph entity?
     */
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

        state.lastFaceYaw = Float.NaN;
        state.lastAnchorX = Double.NaN;
        state.lastAnchorY = Double.NaN;
        state.lastAnchorZ = Double.NaN;

        state.preferredViewer = null;
        state.hasNearbyViewer = false;
        state.nextViewerRefreshAtMs = 0L;
        state.nextIdleFollowAtMs = 0L;
        state.nextGlyphRotationSyncAtMs = 0L;

        TransformComponent playerTx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTx == null) {
            return false;
        }

        Vector3d playerPos = playerTx.getTransform().getPosition();
        Vector3f playerRot = playerTx.getTransform().getRotation();

        state.yawNativeLooksLikeDegrees = RotationCompat.looksLikeDegrees(playerRot.getY());

        List<String> logicalLines = splitLines(
                text,
                settings.getExperimentalGlyphMaxLines()
        );

        if (logicalLines.isEmpty()) {
            logicalLines = Collections.singletonList("");
        }

        double lineSpacing = settings.getExperimentalGlyphLineSpacing();
        int hardCap = settings.getExperimentalGlyphMaxEntitiesPerPlayer();
        int spawnedCount = 0;
        boolean spawnAttemptedForVisibleGlyph = false;
        boolean spawnedAnyGlyph = false;

        double charAdvance = getGlyphAdvance(state.scale);
        float modelScale = GlyphInfoCompat.BASE_MODEL_SCALE * (float) state.scale;

        for (int lineIndex = 0; lineIndex < logicalLines.size(); lineIndex++) {
            String lineText = logicalLines.get(lineIndex);
            if (lineText == null) {
                lineText = "";
            }

            double lineYOffset = (lineIndex * lineSpacing);

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

                int rgbQuant = TintPaletteCompat.quantizeRgb(cc.color);

                Vector3d gPos = new Vector3d(anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());
                Vector3f gRot = new Vector3f(0f, 0f, 0f);

                Ref<EntityStore> glyphRef = spawnGlyph(
                        store,
                        world.getEntityStore(),
                        ch,
                        rgbQuant,
                        gPos,
                        gRot,
                        modelScale,
                        anchorRef,
                        new Vector3f((float) offset, 0f, 0f)
                );

                if (glyphRef != null && glyphRef.isValid()) {
                    lineState.glyphRefs.add(glyphRef);
                    lineState.glyphOffsets.add(offset);
                    spawnedCount++;
                    spawnedAnyGlyph = true;
                }
            }

            state.lines.add(lineState);

            if (spawnedCount >= hardCap) {
                break;
            }
        }

        if (state.lines.isEmpty()) {
            return false;
        }

        // Success rules:
        // - blank / whitespace-only visual text can still be considered successful if at least one anchor spawned
        // - otherwise, if we attempted to spawn visible glyphs, at least one should exist
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

        Settings settings = Settings.get();

        final double viewerActivationDistanceSq =
                square(settings.getExperimentalGlyphViewerActivationDistance());
        final double viewerDropDistanceSq =
                square(Math.max(
                        settings.getExperimentalGlyphViewerDropDistance(),
                        settings.getExperimentalGlyphViewerActivationDistance()
                ));
        final long activeViewerRefreshMs = settings.getExperimentalGlyphViewerRefreshActiveMs();
        final long idleViewerRefreshMs = settings.getExperimentalGlyphViewerRefreshIdleMs();
        final long idleFollowMs = settings.getExperimentalGlyphIdleFollowIntervalMs();
        final long glyphRotationSyncMs = settings.getExperimentalGlyphRotationSyncIntervalMs();
        final double lineSpacing = settings.getExperimentalGlyphLineSpacing();

        Vector3d playerPos = playerTx.getTransform().getPosition();
        Vector3f playerRot = playerTx.getTransform().getRotation();

        boolean looksDegrees = state.yawNativeLooksLikeDegrees != null
                ? state.yawNativeLooksLikeDegrees
                : RotationCompat.looksLikeDegrees(playerRot.getY());

        if (state.yawNativeLooksLikeDegrees == null) {
            state.yawNativeLooksLikeDegrees = looksDegrees;
        }

        long now = System.currentTimeMillis();

        long viewerRefreshInterval = state.hasNearbyViewer
                ? activeViewerRefreshMs
                : idleViewerRefreshMs;

        boolean needViewerRefresh =
                state.preferredViewer == null
                        || now >= state.nextViewerRefreshAtMs
                        || !isViewerStillUsable(state.preferredViewer, playerPos, store, viewerDropDistanceSq);

        if (needViewerRefresh) {
            state.preferredViewer = resolvePreferredViewer(
                    world,
                    uuid,
                    playerPos,
                    store,
                    state,
                    viewerActivationDistanceSq,
                    viewerDropDistanceSq
            );
            state.nextViewerRefreshAtMs = now + viewerRefreshInterval;
        }

        PlayerRef viewer = state.preferredViewer;
        state.hasNearbyViewer = (viewer != null);

        if (!state.hasNearbyViewer && now < state.nextIdleFollowAtMs) {
            return;
        }

        float faceYawNative;
        if (viewer != null) {
            TransformComponent viewerTx = store.getComponent(viewer.getReference(), TransformComponent.getComponentType());
            if (viewerTx != null) {
                Vector3d viewerPos = viewerTx.getTransform().getPosition();

                double dx = viewerPos.getX() - playerPos.getX();
                double dz = viewerPos.getZ() - playerPos.getZ();

                double angleRad = Math.atan2(dx, dz) + Math.PI;

                faceYawNative = looksDegrees
                        ? normalizeDegrees((float) Math.toDegrees(angleRad))
                        : normalizeRadians((float) angleRad);
            } else {
                faceYawNative = RotationCompat.addYawNative(playerRot.getY(), 180f, looksDegrees);
            }
        } else {
            faceYawNative = RotationCompat.addYawNative(playerRot.getY(), 180f, looksDegrees);
        }

        faceYawNative = RotationCompat.addYawNative(
                faceYawNative,
                GLYPH_YAW_CORRECTION_DEGREES,
                looksDegrees
        );

        if (looksDegrees) {
            faceYawNative = normalizeDegrees(faceYawNative);
        } else {
            faceYawNative = normalizeRadians(faceYawNative);
        }

        double anchorX = playerPos.getX();
        double anchorY = playerPos.getY() + ANCHOR_Y_OFFSET;
        double anchorZ = playerPos.getZ();

        boolean moved =
                Double.isNaN(state.lastAnchorX)
                        || Math.abs(state.lastAnchorX - anchorX) > POSITION_EPSILON
                        || Math.abs(state.lastAnchorY - anchorY) > POSITION_EPSILON
                        || Math.abs(state.lastAnchorZ - anchorZ) > POSITION_EPSILON;

        boolean rotated =
                Float.isNaN(state.lastFaceYaw)
                        || !nearlyEqualYaw(state.lastFaceYaw, faceYawNative, looksDegrees);

        if (!moved && !rotated) {
            if (!state.hasNearbyViewer) {
                state.nextIdleFollowAtMs = now + idleFollowMs;
            }
            return;
        }

        state.lastAnchorX = anchorX;
        state.lastAnchorY = anchorY;
        state.lastAnchorZ = anchorZ;
        state.lastFaceYaw = faceYawNative;

        if (!state.hasNearbyViewer) {
            state.nextIdleFollowAtMs = now + idleFollowMs;
        }

        for (int i = 0; i < state.lines.size(); i++) {
            LineRenderState line = state.lines.get(i);
            if (line == null) continue;
            if (line.anchorRef == null || !line.anchorRef.isValid()) continue;

            TransformComponent anchorTx = store.getComponent(line.anchorRef, TransformComponent.getComponentType());
            if (anchorTx == null) continue;

            boolean changed = false;

            if (FORCE_ANCHOR_POSITION_SYNC && moved) {
                double lineY = playerPos.getY() + ANCHOR_Y_OFFSET + (i * lineSpacing);
                anchorTx.getPosition().setX(anchorX);
                anchorTx.getPosition().setY(lineY);
                anchorTx.getPosition().setZ(anchorZ);
                changed = true;
            }

            if (rotated) {
                anchorTx.setRotation(new Vector3f(0f, faceYawNative, 0f));
                changed = true;
            }

            if (changed) {
                StoreComponentUpdateCompat.update(store, line.anchorRef, anchorTx);
            }
        }

        if (FORCE_GLYPH_CHILD_ROTATION_SYNC
                && rotated
                && now >= state.nextGlyphRotationSyncAtMs) {

            state.nextGlyphRotationSyncAtMs = now + glyphRotationSyncMs;

            for (LineRenderState line : state.lines) {
                if (line == null) continue;

                for (Ref<EntityStore> glyphRef : line.glyphRefs) {
                    if (glyphRef == null || !glyphRef.isValid()) continue;

                    TransformComponent glyphTx = store.getComponent(glyphRef, TransformComponent.getComponentType());
                    if (glyphTx == null) continue;

                    glyphTx.setRotation(new Vector3f(0f, faceYawNative, 0f));
                    StoreComponentUpdateCompat.update(store, glyphRef, glyphTx);
                }
            }
        }
    }

    private boolean isViewerStillUsable(@Nullable PlayerRef viewer,
                                        @Nonnull Vector3d targetPos,
                                        @Nonnull Store<EntityStore> store,
                                        double viewerDropDistanceSq) {
        if (viewer == null) return false;

        Ref<EntityStore> ref = viewer.getReference();
        if (ref == null || !ref.isValid()) return false;

        try {
            TransformComponent tx = store.getComponent(ref, TransformComponent.getComponentType());
            if (tx == null) return false;

            double distSq = tx.getTransform().getPosition().distanceSquaredTo(targetPos);
            return distSq <= viewerDropDistanceSq;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    private PlayerRef resolvePreferredViewer(@Nonnull World world,
                                             @Nonnull UUID targetUuid,
                                             @Nonnull Vector3d targetPos,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull RenderState state,
                                             double viewerActivationDistanceSq,
                                             double viewerDropDistanceSq) {

        PlayerRef current = state.preferredViewer;
        double currentDistSq = Double.MAX_VALUE;

        if (current != null) {
            Ref<EntityStore> ref = current.getReference();
            if (ref != null && ref.isValid()) {
                TransformComponent tx = store.getComponent(ref, TransformComponent.getComponentType());
                if (tx != null) {
                    currentDistSq = tx.getTransform().getPosition().distanceSquaredTo(targetPos);

                    if (currentDistSq <= viewerDropDistanceSq) {
                        PlayerRef nearest = null;
                        double nearestDistSq = currentDistSq;

                        for (PlayerRef other : world.getPlayerRefs()) {
                            if (other == null || other.getUuid() == null || other.getUuid().equals(targetUuid))
                                continue;

                            Ref<EntityStore> otherRef = other.getReference();
                            if (otherRef == null || !otherRef.isValid()) continue;

                            try {
                                TransformComponent otherTx = store.getComponent(otherRef, TransformComponent.getComponentType());
                                if (otherTx == null) continue;

                                double distSq = otherTx.getTransform().getPosition().distanceSquaredTo(targetPos);
                                if (distSq > viewerActivationDistanceSq) continue;

                                if (distSq < nearestDistSq) {
                                    nearestDistSq = distSq;
                                    nearest = other;
                                }
                            } catch (Throwable ignored) {
                            }
                        }

                        if (nearest != null && nearestDistSq + VIEWER_SWITCH_BIAS_SQ < currentDistSq) {
                            state.preferredViewer = nearest;
                            return nearest;
                        }

                        return current;
                    }
                }
            }
        }

        PlayerRef nearest = findNearestViewer(world, targetUuid, targetPos, store, viewerActivationDistanceSq);
        state.preferredViewer = nearest;
        return nearest;
    }

    private void despawnAll(@Nonnull Store<EntityStore> store,
                            @Nonnull EntityStore entityStore,
                            @Nonnull RenderState state) {

        for (LineRenderState line : state.lines) {
            if (line == null) continue;

            if (line.anchorRef != null && line.anchorRef.isValid()) {
                try {
                    EntityRemoveCompat.remove(store, entityStore, line.anchorRef);
                } catch (Throwable ignored) {
                }
                line.anchorRef = null;
            }

            for (Ref<EntityStore> ref : line.glyphRefs) {
                if (ref == null || !ref.isValid()) continue;
                try {
                    EntityRemoveCompat.remove(store, entityStore, ref);
                } catch (Throwable ignored) {
                }
            }

            line.glyphRefs.clear();
            line.glyphOffsets.clear();
        }

        state.lines.clear();
    }

    private PlayerRef findNearestViewer(World world,
                                        UUID targetUuid,
                                        Vector3d targetPos,
                                        Store<EntityStore> store,
                                        double viewerActivationDistanceSq) {
        PlayerRef nearest = null;
        double minDistSq = viewerActivationDistanceSq;

        for (PlayerRef other : world.getPlayerRefs()) {
            if (other == null || other.getUuid() == null || other.getUuid().equals(targetUuid)) continue;

            Ref<EntityStore> otherRef = other.getReference();
            if (otherRef == null || !otherRef.isValid()) continue;

            try {
                TransformComponent otherTx = store.getComponent(otherRef, TransformComponent.getComponentType());
                if (otherTx == null) continue;

                double distSq = otherTx.getTransform().getPosition().distanceSquaredTo(targetPos);
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    nearest = other;
                }
            } catch (Throwable ignored) {
            }
        }

        return nearest;
    }

    @Nullable
    private Ref<EntityStore> spawnGlyph(@Nonnull Store<EntityStore> store,
                                        @Nonnull EntityStore entityStore,
                                        char ch,
                                        int rgbQuant,
                                        @Nonnull Vector3d pos,
                                        @Nonnull Vector3f rot,
                                        float scale,
                                        @Nullable Ref<EntityStore> parentRef,
                                        @Nullable Vector3f mountOffset) {

        try {
            Holder holder = EntityStore.REGISTRY.newHolder();

            String[] candidates = GlyphInfoCompat.getModelAssetIdCandidates(ch);
            if (candidates == null || candidates.length == 0) return null;

            ModelAsset asset = null;
            String usedModelId = null;

            for (String id : candidates) {
                if (id == null || id.isEmpty()) continue;
                asset = (ModelAsset) ModelAsset.getAssetMap().getAsset(id);
                if (asset != null) {
                    usedModelId = id;
                    break;
                }
            }

            if (asset == null && candidates.length > 0) {
                String shortName = candidates[0];
                int colon = shortName.lastIndexOf(':');
                if (colon >= 0) shortName = shortName.substring(colon + 1);
                shortName = shortName.toLowerCase(Locale.ROOT);

                for (Map.Entry<String, ?> entry : ModelAsset.getAssetMap().getAssetMap().entrySet()) {
                    String key = entry.getKey();
                    if (key != null && key.toLowerCase(Locale.ROOT).endsWith(shortName)) {
                        asset = (ModelAsset) entry.getValue();
                        usedModelId = key;
                        break;
                    }
                }
            }

            if (asset == null || usedModelId == null) return null;

            Model model = Model.createScaledModel(asset, scale);
            holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rot));
            holder.putComponent(ModelComponent.getComponentType(), new ModelComponent(model));

            try {
                Model.ModelReference staticRef = new Model.ModelReference(usedModelId, scale, null, true);
                holder.putComponent(PersistentModel.getComponentType(), new PersistentModel(staticRef));
            } catch (Throwable ignored) {
                holder.putComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            }

            try {
                holder.putComponent(
                        com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent.getComponentType(),
                        new com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent(scale)
                );
            } catch (Throwable ignored) {
            }

            holder.putComponent(NetworkId.getComponentType(), new NetworkId(entityStore.takeNextNetworkId()));
            holder.putComponent(UUIDComponent.getComponentType(), UUIDComponent.randomUUID());
            holder.putComponent(Intangible.getComponentType(), Intangible.INSTANCE);

            try {
                holder.ensureComponent(EntityModule.get().getVisibleComponentType());
            } catch (Throwable ignored) {
            }
            try {
                holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            } catch (Throwable ignored) {
            }

            holder.putComponent(EffectControllerComponent.getComponentType(), new EffectControllerComponent());

            if (parentRef != null && mountOffset != null) {
                MountCompat.mount(holder, parentRef, mountOffset);
            }

            Ref<EntityStore> spawned = store.addEntity(holder, AddReason.SPAWN);

            try {
                EffectControllerComponent spawnedEffects = store.getComponent(spawned, EffectControllerComponent.getComponentType());
                EntityEffect tint = null;
                String finalEffectId = RESOLVED_EFFECT_IDS.get(rgbQuant);

                if (finalEffectId != null) {
                    tint = (EntityEffect) EntityEffect.getAssetMap().getAsset(finalEffectId);
                } else {
                    String attemptId = GlyphAssets.tintEffectId(rgbQuant);
                    tint = (EntityEffect) EntityEffect.getAssetMap().getAsset(attemptId);

                    if (tint == null) {
                        attemptId = attemptId.toLowerCase(Locale.ROOT);
                        tint = (EntityEffect) EntityEffect.getAssetMap().getAsset(attemptId);
                    }

                    if (tint == null) {
                        String shortName = "httint_" + String.format("%06x", rgbQuant);
                        for (Map.Entry<String, ?> entry : EntityEffect.getAssetMap().getAssetMap().entrySet()) {
                            String key = entry.getKey();
                            if (key != null && key.toLowerCase(Locale.ROOT).contains(shortName)) {
                                attemptId = key;
                                tint = (EntityEffect) entry.getValue();
                                break;
                            }
                        }
                    }

                    if (tint != null) {
                        RESOLVED_EFFECT_IDS.put(rgbQuant, attemptId);
                    }
                }

                if (tint != null && spawnedEffects != null) {
                    spawnedEffects.addEffect(spawned, tint, (float) Integer.MAX_VALUE, OverlapBehavior.OVERWRITE, store);
                }
            } catch (Throwable ignored) {
            }

            return spawned;
        } catch (Throwable t) {
            LOGGER.at(Level.FINE).withCause(t).log("[MysticNameTags] Failed to spawn glyph '" + ch + "'");
            return null;
        }
    }

    private static final class RenderState {
        final List<LineRenderState> lines = new ArrayList<>();
        String lastText = null;
        double scale = 1.0d;
        String worldName = null;
        Boolean yawNativeLooksLikeDegrees = null;
        double lastAnchorX = Double.NaN;
        double lastAnchorY = Double.NaN;
        double lastAnchorZ = Double.NaN;
        float lastFaceYaw = Float.NaN;
        PlayerRef preferredViewer = null;
        boolean hasNearbyViewer = false;
        long nextViewerRefreshAtMs = 0L;
        long nextIdleFollowAtMs = 0L;
        long nextGlyphRotationSyncAtMs = 0L;
    }

    private static final class LineRenderState {
        final List<Ref<EntityStore>> glyphRefs = new ArrayList<>();
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

    private static final class StoreComponentUpdateCompat {
        private static volatile boolean cached = false;
        private static Method mUpdateComponent3;
        private static Method mSetComponent3;
        private static Method mPutComponent3;

        static void update(Store<EntityStore> store, Ref<EntityStore> ref, TransformComponent tx) {
            tryInit(store);
            try {
                Object type = TransformComponent.getComponentType();
                if (mUpdateComponent3 != null) mUpdateComponent3.invoke(store, ref, type, tx);
                else if (mSetComponent3 != null) mSetComponent3.invoke(store, ref, type, tx);
                else if (mPutComponent3 != null) mPutComponent3.invoke(store, ref, type, tx);
            } catch (Throwable ignored) {
            }
        }

        private static void tryInit(Store<EntityStore> store) {
            if (cached) return;
            synchronized (StoreComponentUpdateCompat.class) {
                if (cached) return;
                for (Method m : store.getClass().getMethods()) {
                    if (m.getParameterCount() == 3) {
                        if (m.getName().equals("updateComponent")) mUpdateComponent3 = m;
                        else if (m.getName().equals("setComponent")) mSetComponent3 = m;
                        else if (m.getName().equals("putComponent")) mPutComponent3 = m;
                    }
                }
                cached = true;
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

    private static final class TintPaletteCompat {
        private static final int[] ALLOWED_VALUES = {0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0, 0xE0, 0xFF};
        private static final int[] LUT = new int[256];

        static {
            for (int i = 0; i < 256; i++) {
                int closest = 0;
                int minDiff = Integer.MAX_VALUE;
                for (int val : ALLOWED_VALUES) {
                    int diff = Math.abs(i - val);
                    if (diff < minDiff) {
                        minDiff = diff;
                        closest = val;
                    }
                }
                LUT[i] = closest;
            }
        }

        static int quantizeRgb(Color c) {
            int r = LUT[c.getRed() & 0xFF];
            int g = LUT[c.getGreen() & 0xFF];
            int b = LUT[c.getBlue() & 0xFF];
            return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }
    }
}