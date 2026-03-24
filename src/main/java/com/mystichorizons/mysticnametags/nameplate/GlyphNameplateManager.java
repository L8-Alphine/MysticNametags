package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.TransformUpdate;
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
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
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

    // Set to 180f only if all authored glyph assets are globally backwards.
    private static final float GLYPH_YAW_CORRECTION_DEGREES = 0f;

    private static final double GLYPH_EXTRA_SPACING_PX = 4.0d;
    private static final double GLYPH_SOURCE_WIDTH_PX = 16.0d;

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

            try {
                // Ensure tracker knows about the anchor so clients can get the TransformUpdate
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

                Color dimmed = scaleColor(cc.color, settings.getExperimentalGlyphTintStrength());
                int rgbQuant = TintPaletteCompat.quantizeRgb(dimmed);

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

        for (int i = 0; i < state.lines.size(); i++) {
            LineRenderState line = state.lines.get(i);
            if (line == null || line.anchorRef == null || !line.anchorRef.isValid()) continue;

            Visible visible = store.getComponent(line.anchorRef, EntityModule.get().getVisibleComponentType());
            if (visible == null || visible.visibleTo.isEmpty()) continue;

            for (Map.Entry<Ref<EntityStore>, EntityViewer> entry : visible.visibleTo.entrySet()) {
                Ref<EntityStore> viewerRef = entry.getKey();
                EntityViewer viewer = entry.getValue();

                if (!viewerRef.isValid()) continue;

                float yaw;
                
                // If the viewer is the player owning the tag, fallback to their own rotation + 180 degrees
                // This ensures it faces them nicely in 3rd person and rotates with them
                if (viewerRef.equals(playerRef)) {
                    yaw = RotationCompat.addYawNative(playerRot.getY(), 180f, looksDegrees);
                    if (looksDegrees) {
                        yaw = normalizeDegrees(yaw);
                    } else {
                        yaw = normalizeRadians(yaw);
                    }
                } else {
                    TransformComponent viewerTx = store.getComponent(viewerRef, TransformComponent.getComponentType());
                    if (viewerTx == null) continue;

                    Vector3d viewerPos = viewerTx.getTransform().getPosition();

                    double dx = viewerPos.getX() - playerPos.getX();
                    double dz = viewerPos.getZ() - playerPos.getZ();

                    yaw = (float) Math.atan2(-dx, -dz);

                    if (looksDegrees) {
                        yaw = normalizeDegrees((float) Math.toDegrees(yaw));
                    } else {
                        yaw = normalizeRadians(yaw);
                    }
                }

                yaw = RotationCompat.addYawNative(yaw, GLYPH_YAW_CORRECTION_DEGREES, looksDegrees);

                try {
                    // Update rotation via client TransformUpdate without overriding smooth position mounting
                    ModelTransform transform = new ModelTransform();
                    transform.bodyOrientation = new Direction(yaw, 0.0F, 0.0F);
                    transform.lookOrientation = new Direction(yaw, 0.0F, 0.0F);
                    TransformUpdate update = new TransformUpdate(transform);

                    if (!viewer.visible.contains(line.anchorRef)) {
                        viewer.visible.add(line.anchorRef);
                    }
                    
                    // Rotate the main anchor to make the text billboard/pivot as a whole to this viewer
                    viewer.queueUpdate(line.anchorRef, update);

                    for (Ref<EntityStore> glyphRef : line.glyphRefs) {
                        if (glyphRef != null && glyphRef.isValid()) {
                            if (!viewer.visible.contains(glyphRef)) {
                                viewer.visible.add(glyphRef);
                            }
                            
                            // Rotate the individual faces to make them render cleanly towards the viewer
                            viewer.queueUpdate(glyphRef, update);
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.at(Level.FINE).withCause(t)
                            .log("[MysticNameTags] Failed to queue rotation update for viewer.");
                }
            }
        }
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