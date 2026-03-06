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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.Settings;
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphAssets;
import com.mystichorizons.mysticnametags.nameplate.glyph.GlyphInfoCompat;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class GlyphNameplateManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final GlyphNameplateManager INSTANCE = new GlyphNameplateManager();

    private static final double ANCHOR_Y_OFFSET = 2.25d;
    private static final double POS_EPSILON = 0.0005d;
    private static final float YAW_EPSILON = 0.05f;

    public static GlyphNameplateManager get() {
        return INSTANCE;
    }

    private final Map<UUID, RenderState> states = new ConcurrentHashMap<>();

    private GlyphNameplateManager() {}

    /**
     * Apply or update a glyph nameplate for a player.
     * MUST be called on the world thread.
     */
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

        String clamped = clampVisibleLength(formattedText, settings.getExperimentalGlyphMaxChars());

        RenderState state = states.computeIfAbsent(uuid, ignored -> new RenderState());
        state.worldName = world.getName();

        boolean needsRebuild = !Objects.equals(state.lastText, clamped);

        if (needsRebuild) {
            rebuild(world, store, playerRef, state, clamped, settings);
            state.lastText = clamped;
        }

        follow(store, playerRef, state);
    }

    /**
     * Remove glyph entities for this player.
     * MUST be called on the world thread.
     */
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
        if (state == null) {
            return;
        }

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            store.assertThread();
            despawnAll(store, world.getEntityStore(), state);
        });
    }

    /**
     * Remove cached state when player leaves.
     * No store/world access here.
     */
    public void forget(@Nonnull UUID uuid) {
        states.remove(uuid);
    }

    /**
     * Cheap transform-only follow update.
     * MUST be called on the world thread.
     */
    public void followOnly(@Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> playerRef,
                           @Nonnull UUID uuid) {
        store.assertThread();
        RenderState state = states.get(uuid);
        if (state == null) return;
        follow(store, playerRef, state);
    }

    public boolean hasState(@Nonnull UUID uuid) {
        return states.containsKey(uuid);
    }

    public void clearAllInWorld(@Nonnull World world) {
        final String worldName = world.getName();

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            store.assertThread();

            List<UUID> uuids = new ArrayList<>(states.keySet());
            for (UUID uuid : uuids) {
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

    // ---------------------------------------------------------------------
    // Build & follow
    // ---------------------------------------------------------------------

    private void rebuild(@Nonnull World world,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull Ref<EntityStore> playerRef,
                         @Nonnull RenderState state,
                         @Nonnull String text,
                         @Nonnull Settings settings) {

        despawnAll(store, world.getEntityStore(), state);
        state.glyphRefs.clear();
        state.glyphOffsets.clear();

        state.lastAnchorX = Double.NaN;
        state.lastAnchorY = Double.NaN;
        state.lastAnchorZ = Double.NaN;
        state.lastFaceYaw = Float.NaN;

        TransformComponent playerTx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTx == null) return;

        Vector3d playerPos = playerTx.getTransform().getPosition();
        Vector3f playerRot = playerTx.getTransform().getRotation();

        state.yawNativeLooksLikeDegrees = RotationCompat.looksLikeDegrees(playerRot.getY());

        double yawRadTrig = RotationCompat.toTrigYawRad(playerRot.getY(), state.yawNativeLooksLikeDegrees);
        double rightX = Math.sin(yawRadTrig);
        double rightZ = Math.cos(yawRadTrig);

        Vector3d anchor = new Vector3d(
                playerPos.getX(),
                playerPos.getY() + ANCHOR_Y_OFFSET,
                playerPos.getZ()
        );

        float faceYawNative = RotationCompat.addYawNative(playerRot.getY(), 180f, state.yawNativeLooksLikeDegrees);
        Vector3f faceRot = new Vector3f(0f, faceYawNative, 0f);

        List<ColoredChar> chars = SimpleColorParser.parse(text);

        int hardCap = settings.getExperimentalGlyphMaxEntitiesPerPlayer();
        int spawnedCount = 0;

        double charWidth = GlyphInfoCompat.CHAR_WIDTH * state.scale;
        float modelScale = GlyphInfoCompat.BASE_MODEL_SCALE * (float) state.scale;

        int visibleCount = 0;
        for (ColoredChar cc : chars) {
            if (cc.ch == '\n' || cc.ch == '\r') continue;
            visibleCount++;
        }

        double totalWidth = visibleCount * charWidth;
        double start = -totalWidth / 2.0d;

        int logicalIndex = 0;
        for (ColoredChar cc : chars) {
            char ch = cc.ch;
            if (ch == '\n' || ch == '\r') continue;

            double offset = start + ((logicalIndex + 0.5d) * charWidth);
            logicalIndex++;

            if (ch == ' ') continue;
            if (spawnedCount >= hardCap) break;
            if (!GlyphInfoCompat.isSupported(ch)) continue;

            int rgbQuant = TintPaletteCompat.quantizeRgb(cc.color);
            String tintEffectId = GlyphAssets.tintEffectId(rgbQuant);

            Vector3d pos = new Vector3d(
                    anchor.getX() + rightX * offset,
                    anchor.getY(),
                    anchor.getZ() + rightZ * offset
            );

            Ref<EntityStore> glyphRef = spawnGlyph(
                    store,
                    world.getEntityStore(),
                    ch,
                    tintEffectId,
                    pos,
                    faceRot,
                    modelScale
            );

            if (glyphRef != null) {
                state.glyphRefs.add(glyphRef);
                state.glyphOffsets.add(offset);
                spawnedCount++;
            }
        }
    }

    private void follow(@Nonnull Store<EntityStore> store,
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

        double yawRadTrig = RotationCompat.toTrigYawRad(playerRot.getY(), looksDegrees);
        double rightX = Math.sin(yawRadTrig);
        double rightZ = Math.cos(yawRadTrig);

        Vector3d anchor = new Vector3d(
                playerPos.getX(),
                playerPos.getY() + ANCHOR_Y_OFFSET,
                playerPos.getZ()
        );

        float faceYawNative = RotationCompat.addYawNative(playerRot.getY(), 180f, looksDegrees);
        Vector3f faceRot = new Vector3f(0f, faceYawNative, 0f);

        if (nearlyEqual(state.lastAnchorX, anchor.getX())
                && nearlyEqual(state.lastAnchorY, anchor.getY())
                && nearlyEqual(state.lastAnchorZ, anchor.getZ())
                && nearlyEqual(state.lastFaceYaw, faceYawNative)) {
            return;
        }

        state.lastAnchorX = anchor.getX();
        state.lastAnchorY = anchor.getY();
        state.lastAnchorZ = anchor.getZ();
        state.lastFaceYaw = faceYawNative;

        List<Ref<EntityStore>> refs = state.glyphRefs;
        List<Double> offsets = state.glyphOffsets;

        for (int i = 0; i < refs.size(); i++) {
            Ref<EntityStore> glyphRef = refs.get(i);
            if (glyphRef == null || !glyphRef.isValid()) continue;

            double along = offsets.get(i);

            Vector3d pos = new Vector3d(
                    anchor.getX() + rightX * along,
                    anchor.getY(),
                    anchor.getZ() + rightZ * along
            );

            StoreTransformCompat.set(store, glyphRef, new TransformComponent(pos, faceRot));
        }
    }

    private void despawnAll(@Nonnull Store<EntityStore> store,
                            @Nonnull EntityStore entityStore,
                            @Nonnull RenderState state) {

        List<Ref<EntityStore>> refs = state.glyphRefs;
        for (int i = 0; i < refs.size(); i++) {
            Ref<EntityStore> ref = refs.get(i);
            if (ref == null || !ref.isValid()) continue;

            try {
                EntityRemoveCompat.remove(store, entityStore, ref);
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------------------------------------------------------------
    // Spawning
    // ---------------------------------------------------------------------

    @Nullable
    private Ref<EntityStore> spawnGlyph(@Nonnull Store<EntityStore> store,
                                        @Nonnull EntityStore entityStore,
                                        char ch,
                                        @Nonnull String effectAssetId,
                                        @Nonnull Vector3d pos,
                                        @Nonnull Vector3f rot,
                                        float scale) {

        try {
            Holder holder = EntityStore.REGISTRY.newHolder();

            String[] candidates = GlyphInfoCompat.getModelAssetIdCandidates(ch);
            if (candidates == null || candidates.length == 0) {
                return null;
            }

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
                if (colon >= 0) {
                    shortName = shortName.substring(colon + 1);
                }
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

            if (asset == null || usedModelId == null) {
                LOGGER.at(Level.WARNING).log(
                        "[MysticNameTags] Glyph model not found for '" + ch + "': " + Arrays.toString(candidates)
                );
                return null;
            }

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

            Ref<EntityStore> spawned = store.addEntity(holder, AddReason.SPAWN);

            try {
                EffectControllerComponent spawnedEffects =
                        store.getComponent(spawned, EffectControllerComponent.getComponentType());

                EntityEffect tint = (EntityEffect) EntityEffect.getAssetMap().getAsset(effectAssetId);
                if (tint != null && spawnedEffects != null) {
                    spawnedEffects.addEffect(
                            spawned,
                            tint,
                            (float) Integer.MAX_VALUE,
                            OverlapBehavior.OVERWRITE,
                            store
                    );
                }
            } catch (Throwable t) {
                LOGGER.at(Level.FINE).withCause(t)
                        .log("[MysticNameTags] Failed to apply tint effect to glyph " + usedModelId);
            }

            return spawned;
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] spawnGlyph failed for character " + ch);
            return null;
        }
    }

    // ---------------------------------------------------------------------
    // Formatting parsing + clamping
    // ---------------------------------------------------------------------

    private static String clampVisibleLength(String text, int maxVisible) {
        if (text == null || text.isEmpty()) return "";

        text = ColorFormatter.miniToLegacy(text);

        StringBuilder out = new StringBuilder(text.length());
        int visible = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // &#RRGGBB / §#RRGGBB
            if ((c == '&' || c == '§')
                    && i + 7 < text.length()
                    && text.charAt(i + 1) == '#') {
                out.append(text, i, i + 8);
                i += 7;
                continue;
            }

            // &x&F&F&0&0&0&0 / §x§F§F§0§0§0§0
            if ((c == '&' || c == '§')
                    && i + 13 < text.length()
                    && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X')) {
                out.append(text, i, i + 14);
                i += 13;
                continue;
            }

            // legacy codes
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if ("0123456789abcdefABCDEFklmnorKLMNORxX".indexOf(code) >= 0) {
                    out.append(c).append(code);
                    i += 1;
                    continue;
                }
            }

            // angle tags
            if (c == '<') {
                int end = text.indexOf('>', i);
                if (end > i) {
                    out.append(text, i, end + 1);
                    i = end;
                    continue;
                }
            }

            out.append(c);
            if (c != '\n' && c != '\r') {
                visible++;
                if (visible >= maxVisible) break;
            }
        }

        return out.toString();
    }

    private static boolean nearlyEqual(double a, double b) {
        return Math.abs(a - b) < POS_EPSILON;
    }

    private static boolean nearlyEqual(float a, float b) {
        return Math.abs(a - b) < YAW_EPSILON;
    }

    private static final class RenderState {
        String lastText = null;
        double scale = 1.0d;

        String worldName = null;
        Boolean yawNativeLooksLikeDegrees = null;

        double lastAnchorX = Double.NaN;
        double lastAnchorY = Double.NaN;
        double lastAnchorZ = Double.NaN;
        float lastFaceYaw = Float.NaN;

        final List<Ref<EntityStore>> glyphRefs = new ArrayList<>();
        final List<Double> glyphOffsets = new ArrayList<>();
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
            LEGACY_COLORS.put('1', new Color(0x0000AA));
            LEGACY_COLORS.put('2', new Color(0x00AA00));
            LEGACY_COLORS.put('3', new Color(0x00AAAA));
            LEGACY_COLORS.put('4', new Color(0xAA0000));
            LEGACY_COLORS.put('5', new Color(0xAA00AA));
            LEGACY_COLORS.put('6', new Color(0xFFAA00));
            LEGACY_COLORS.put('7', new Color(0xAAAAAA));
            LEGACY_COLORS.put('8', new Color(0x555555));
            LEGACY_COLORS.put('9', new Color(0x5555FF));
            LEGACY_COLORS.put('a', new Color(0x55FF55));
            LEGACY_COLORS.put('b', new Color(0x55FFFF));
            LEGACY_COLORS.put('c', new Color(0xFF5555));
            LEGACY_COLORS.put('d', new Color(0xFF55FF));
            LEGACY_COLORS.put('e', new Color(0xFFFF55));
            LEGACY_COLORS.put('f', Color.WHITE);
        }

        static List<ColoredChar> parse(String text) {
            List<ColoredChar> out = new ArrayList<>();
            if (text == null || text.isEmpty()) return out;

            text = ColorFormatter.miniToLegacy(text);

            Color current = Color.WHITE;

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);

                // &#RRGGBB / §#RRGGBB
                if ((c == '&' || c == '§')
                        && i + 7 < text.length()
                        && text.charAt(i + 1) == '#') {
                    Color parsed = GlyphAssets.tryParseHex6(text.substring(i + 2, i + 8));
                    if (parsed != null) current = parsed;
                    i += 7;
                    continue;
                }

                // &x&F&F&0&0&0&0 / §x§F§F§0§0§0§0
                if ((c == '&' || c == '§')
                        && i + 13 < text.length()
                        && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X')) {

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

                // legacy single-char color/style
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

                // <#RRGGBB>, </>, <reset>
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

    // ---------------------------------------------------------------------
    // Compat helpers
    // ---------------------------------------------------------------------

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

    private static final class TransformComponentCompat {
        private static volatile boolean cached = false;

        private static Method mSetPosition;
        private static Method mSetRotation;
        private static Method mSetTransformPR;
        private static Method mSetTransformObj;
        private static Method mGetTransform;
        private static Method mMarkChunkDirty;

        private static Constructor<?> cTransform;

        static void apply(@Nonnull Store<EntityStore> store,
                          @Nonnull TransformComponent tx,
                          @Nonnull Vector3d pos,
                          @Nonnull Vector3f rot) {

            tryInit(tx);

            if (mSetTransformPR != null) {
                try {
                    mSetTransformPR.invoke(tx, pos, rot);
                    markDirty(store, tx);
                    return;
                } catch (Throwable ignored) {
                }
            }

            boolean didPos = false;
            if (mSetPosition != null) {
                try {
                    mSetPosition.invoke(tx, pos);
                    didPos = true;
                } catch (Throwable ignored) {
                }
            }

            boolean didRot = false;
            if (mSetRotation != null) {
                try {
                    mSetRotation.invoke(tx, rot);
                    didRot = true;
                } catch (Throwable ignored) {
                }
            }

            if (didPos || didRot) {
                markDirty(store, tx);
                return;
            }

            if (mSetTransformObj != null) {
                Object transformObj = null;

                if (cTransform != null) {
                    try {
                        transformObj = cTransform.newInstance(pos, rot);
                    } catch (Throwable ignored) {
                    }
                }

                if (transformObj == null && mGetTransform != null) {
                    try {
                        transformObj = mGetTransform.invoke(tx);
                    } catch (Throwable ignored) {
                    }
                }

                if (transformObj != null) {
                    try {
                        mSetTransformObj.invoke(tx, transformObj);
                        markDirty(store, tx);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        private static void markDirty(Store<EntityStore> store, TransformComponent tx) {
            if (mMarkChunkDirty != null) {
                try {
                    mMarkChunkDirty.invoke(tx, store);
                } catch (Throwable ignored) {
                }
            }
        }

        private static void tryInit(@Nonnull TransformComponent tx) {
            if (cached) return;

            synchronized (TransformComponentCompat.class) {
                if (cached) return;

                Class<?> cls = tx.getClass();

                mSetTransformPR = findMethodExact(cls, "setTransform", Vector3d.class, Vector3f.class);
                mSetPosition = findMethodExact(cls, "setPosition", Vector3d.class);
                mSetRotation = findMethodExact(cls, "setRotation", Vector3f.class);
                mGetTransform = findMethodExact(cls, "getTransform");
                mMarkChunkDirty = findMethodExact(cls, "markChunkDirty", Store.class);

                for (Method m : cls.getMethods()) {
                    if (!m.getName().equals("setTransform")) continue;
                    if (m.getParameterCount() == 1) {
                        mSetTransformObj = m;
                        break;
                    }
                }

                if (mSetTransformObj != null) {
                    Class<?> transformType = mSetTransformObj.getParameterTypes()[0];
                    try {
                        cTransform = transformType.getConstructor(Vector3d.class, Vector3f.class);
                    } catch (Throwable ignored) {
                        cTransform = null;
                    }
                }

                cached = true;
            }
        }

        private static Method findMethodExact(Class<?> cls, String name, Class<?>... params) {
            try {
                return cls.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    private static final class RotationCompat {

        static boolean looksLikeDegrees(float yaw) {
            return Math.abs(yaw) > 6.4f;
        }

        static double toTrigYawRad(float yawNative, boolean looksDegrees) {
            return looksDegrees ? Math.toRadians(yawNative) : yawNative;
        }

        static float addYawNative(float yawNative, float addDegrees, boolean looksDegrees) {
            if (looksDegrees) return yawNative + addDegrees;
            return (float) (yawNative + Math.toRadians(addDegrees));
        }
    }

    private static final class StoreTransformCompat {
        private static volatile boolean cached = false;
        private static Method mPutComponent3;
        private static Method mSetComponent3;
        private static Method mUpdateComponent3;

        static void set(Store<EntityStore> store, Ref<EntityStore> ref, TransformComponent tx) {
            tryInit(store);
            Object type = TransformComponent.getComponentType();

            if (mPutComponent3 != null && tryInvoke(mPutComponent3, store, ref, type, tx)) return;
            if (mSetComponent3 != null && tryInvoke(mSetComponent3, store, ref, type, tx)) return;
            if (mUpdateComponent3 != null && tryInvoke(mUpdateComponent3, store, ref, type, tx)) return;

            TransformComponent existing = store.getComponent(ref, TransformComponent.getComponentType());
            if (existing != null) {
                TransformComponentCompat.apply(
                        store,
                        existing,
                        tx.getTransform().getPosition(),
                        tx.getTransform().getRotation()
                );
            }
        }

        private static boolean tryInvoke(Method m, Object target, Object... args) {
            try {
                m.invoke(target, args);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static void tryInit(Store<EntityStore> store) {
            if (cached) return;

            synchronized (StoreTransformCompat.class) {
                if (cached) return;

                Class<?> cls = store.getClass();
                mPutComponent3 = findMethodByNameAndParamCount(cls, "putComponent", 3);
                mSetComponent3 = findMethodByNameAndParamCount(cls, "setComponent", 3);
                mUpdateComponent3 = findMethodByNameAndParamCount(cls, "updateComponent", 3);
                cached = true;
            }
        }

        private static Method findMethodByNameAndParamCount(Class<?> cls, String name, int count) {
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == count) {
                    return m;
                }
            }
            return null;
        }
    }

    private static final class TintPaletteCompat {

        private static final int[] LUT = new int[256];

        static {
            for (int i = 0; i < 256; i++) {
                int step = (i * 8 + 127) / 255;
                LUT[i] = (step * 255 + 4) / 8;
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