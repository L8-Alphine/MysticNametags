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
import com.mystichorizons.mysticnametags.nameplate.glyph.TintPaletteCompat;

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

    public static GlyphNameplateManager get() {
        return INSTANCE;
    }

    private final Map<UUID, RenderState> states = new ConcurrentHashMap<>();

    // Position above head
    private static final double ANCHOR_Y_OFFSET = 2.25;

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

        if (!Settings.get().isExperimentalGlyphNameplatesEnabled()) {
            remove(uuid, world, store);
            return;
        }

        String clamped = clampVisibleLength(formattedText, Settings.get().getExperimentalGlyphMaxChars());

        RenderState state = states.computeIfAbsent(uuid, u -> new RenderState());

        // Rebuild only if text changed
        if (!Objects.equals(state.lastText, clamped)) {
            rebuild(world, store, playerRef, state, clamped);
            state.lastText = clamped;
        }

        // Always follow on apply
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

    /**
     * Remove state cache when player fully leaves (no store access).
     */
    public void forget(@Nonnull UUID uuid) {
        states.remove(uuid);
    }

    /**
     * Follow/update transforms only (cheap).
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

    // ---------------------------------------------------------------------
    // Build & follow
    // ---------------------------------------------------------------------

    private void rebuild(@Nonnull World world,
                         @Nonnull Store<EntityStore> store,
                         @Nonnull Ref<EntityStore> playerRef,
                         @Nonnull RenderState state,
                         @Nonnull String text) {

        despawnAll(store, world.getEntityStore(), state);
        state.glyphRefs.clear();
        state.glyphOffsets.clear();

        TransformComponent playerTx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTx == null) return;

        Vector3f playerRot = playerTx.getTransform().getRotation();
        float yaw = playerRot.getY();

        // Parse into per-character colors
        List<ColoredChar> chars = SimpleColorParser.parse(text);

        // Determine spacing/scale
        double charWidth = GlyphInfoCompat.CHAR_WIDTH * state.scale;
        float modelScale = GlyphInfoCompat.BASE_MODEL_SCALE * (float) state.scale;

        // Center align based on visible count (including spaces)
        int count = 0;
        for (ColoredChar cc : chars) {
            if (cc.ch == '\n' || cc.ch == '\r') continue;
            count++;
        }

        double totalWidth = count * charWidth;
        double start = -totalWidth / 2.0;

        int idx = 0;
        for (ColoredChar cc : chars) {
            char ch = cc.ch;
            if (ch == '\n' || ch == '\r') continue;

            double offset = start + ((idx + 0.5) * charWidth);
            idx++;

            if (ch == ' ') continue;

            int rgbQuant = TintPaletteCompat.quantizeRgb(cc.color);
            String tintEffectId = GlyphAssets.tintEffectId(rgbQuant);

            Ref<EntityStore> glyphRef = spawnGlyph(
                    store,
                    world.getEntityStore(),
                    ch,
                    tintEffectId,
                    new Vector3d(0, 0, 0),
                    new Vector3f(0, yaw, 0),
                    modelScale
            );

            if (glyphRef != null) {
                state.glyphRefs.add(glyphRef);
                state.glyphOffsets.add(offset);
            }
        }
    }

    private void follow(@Nonnull Store<EntityStore> store,
                        @Nonnull Ref<EntityStore> playerRef,
                        @Nonnull RenderState state) {

        TransformComponent playerTx = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTx == null) return;

        Vector3d p = playerTx.getTransform().getPosition();
        Vector3f r = playerTx.getTransform().getRotation();

        double yawRad = Math.toRadians(r.getY());

        // RIGHT vector for horizontal spacing
        double rightX = Math.sin(yawRad);
        double rightZ = Math.cos(yawRad);

        Vector3d anchor = new Vector3d(p.getX(), p.getY() + ANCHOR_Y_OFFSET, p.getZ());

        // face the camera-ish (common for flat glyph planes)
        float faceYaw = r.getY() + 180f;

        for (int i = 0; i < state.glyphRefs.size(); i++) {
            Ref<EntityStore> glyphRef = state.glyphRefs.get(i);
            if (glyphRef == null || !glyphRef.isValid()) continue;

            double along = state.glyphOffsets.get(i);

            Vector3d pos = new Vector3d(
                    anchor.getX() + rightX * along,
                    anchor.getY(),
                    anchor.getZ() + rightZ * along
            );

            TransformComponent tx = store.getComponent(glyphRef, TransformComponent.getComponentType());
            if (tx == null) continue;

            TransformComponentCompat.apply(tx, pos, new Vector3f(0f, faceYaw, 0f));
        }
    }

    private void despawnAll(@Nonnull Store<EntityStore> store,
                            @Nonnull EntityStore entityStore,
                            @Nonnull RenderState state) {

        for (Ref<EntityStore> ref : state.glyphRefs) {
            if (ref == null || !ref.isValid()) continue;
            try {
                EntityRemoveCompat.remove(store, entityStore, ref);
            } catch (Throwable ignored) {}
        }
    }

    public void clearAllInWorld(@Nonnull World world) {
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            store.assertThread();

            List<UUID> uuids = new ArrayList<>(states.keySet());
            for (UUID uuid : uuids) {
                try {
                    remove(uuid, world, store);
                } catch (Throwable ignored) {}
            }
        });
    }

    // ---------------------------------------------------------------------
    // Spawning (Option B: lowercase-first + engine-case fallback)
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

            if (asset == null) {
                LOGGER.at(Level.WARNING).log("[MysticNameTags] Glyph model not found for '" + ch + "': " + Arrays.toString(candidates));
                return null;
            }

            Model model = Model.createScaledModel(asset, scale);

            holder.putComponent(TransformComponent.getComponentType(), new TransformComponent(pos, rot));
            holder.putComponent(ModelComponent.getComponentType(), new ModelComponent(model));
            holder.putComponent(PersistentModel.getComponentType(), new PersistentModel(model.toReference()));
            holder.putComponent(NetworkId.getComponentType(), new NetworkId(entityStore.takeNextNetworkId()));

            UUIDComponent uuidComp = UUIDComponent.randomUUID();
            holder.putComponent(UUIDComponent.getComponentType(), uuidComp);

            holder.putComponent(Intangible.getComponentType(), Intangible.INSTANCE);

            // Attach effects controller
            holder.putComponent(EffectControllerComponent.getComponentType(), new EffectControllerComponent());

            Ref<EntityStore> spawned = store.addEntity(holder, AddReason.SPAWN);

            // Fetch the actual component from the spawned entity (some builds clone components)
            EffectControllerComponent spawnedEffects =
                    store.getComponent(spawned, EffectControllerComponent.getComponentType());

            EntityEffect tint = (EntityEffect) EntityEffect.getAssetMap().getAsset(effectAssetId);
            if (tint != null) {
                if (spawnedEffects != null) {
                    spawnedEffects.addEffect(spawned, tint, (float) Integer.MAX_VALUE, OverlapBehavior.OVERWRITE, store);
                } else {
                    LOGGER.at(Level.WARNING).log("[MysticNameTags] EffectControllerComponent missing on spawned glyph: " + usedModelId);
                }
            } else {
                LOGGER.at(Level.WARNING).log("[MysticNameTags] Tint effect not found: " + effectAssetId);
            }

            return spawned;
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t).log("[MysticNameTags] spawnGlyph failed");
            return null;
        }
    }

    public void remove(@Nonnull UUID uuid, @Nonnull World world) {
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            remove(uuid, world, store);
        });
    }

    // ---------------------------------------------------------------------
    // Formatting parsing + clamping
    // ---------------------------------------------------------------------

    private static String clampVisibleLength(String text, int maxVisible) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder out = new StringBuilder(text.length());
        int visible = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // &#RRGGBB
            if (c == '&' && i + 7 < text.length() && text.charAt(i + 1) == '#') {
                out.append(text, i, i + 8);
                i += 7;
                continue;
            }

            // <#RRGGBB>
            if (c == '<' && i + 8 < text.length() && text.charAt(i + 1) == '#') {
                int end = text.indexOf('>', i);
                if (end > i) {
                    out.append(text, i, end + 1);
                    i = end;
                    continue;
                }
            }

            // </> (reset)
            if (c == '<') {
                int end = text.indexOf('>', i);
                if (end > i) {
                    String tag = text.substring(i, end + 1).toLowerCase(Locale.ROOT);
                    if (tag.equals("</>")) {
                        out.append(text, i, end + 1);
                        i = end;
                        continue;
                    }
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

    private static final class RenderState {
        String lastText = null;
        double scale = 1.0;

        final List<Ref<EntityStore>> glyphRefs = new ArrayList<>();
        final List<Double> glyphOffsets = new ArrayList<>();
    }

    private static final class ColoredChar {
        final char ch;
        final Color color;
        ColoredChar(char ch, Color color) { this.ch = ch; this.color = color; }
    }

    private static final class SimpleColorParser {
        static List<ColoredChar> parse(String text) {
            List<ColoredChar> out = new ArrayList<>();
            if (text == null || text.isEmpty()) return out;

            Color current = Color.WHITE;

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);

                if (c == '&' && i + 7 < text.length() && text.charAt(i + 1) == '#') {
                    Color parsed = GlyphAssets.tryParseHex6(text.substring(i + 2, i + 8));
                    if (parsed != null) current = parsed;
                    i += 7;
                    continue;
                }

                if (c == '<' && i + 8 < text.length() && text.charAt(i + 1) == '#') {
                    int end = text.indexOf('>', i);
                    if (end > i) {
                        String hex = text.substring(i + 2, Math.min(i + 8, end));
                        Color parsed = GlyphAssets.tryParseHex6(hex);
                        if (parsed != null) current = parsed;
                        i = end;
                        continue;
                    }
                }

                if (c == '<') {
                    int end = text.indexOf('>', i);
                    if (end > i) {
                        String tag = text.substring(i, end + 1).toLowerCase(Locale.ROOT);
                        if (tag.equals("</>")) {
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
    }

    // ---------------------------------------------------------------------
    // COMPAT HELPERS
    // ---------------------------------------------------------------------

    private static final class TransformComponentCompat {
        private static volatile boolean cached = false;

        private static Method mSetPosition;
        private static Method mSetRotation;
        private static Method mSetTransformPR;   // setTransform(Vector3d, Vector3f)
        private static Method mSetTransformObj;  // setTransform(Transform)
        private static Method mGetTransform;     // getTransform()

        private static Constructor<?> cTransform; // new Transform(Vector3d, Vector3f) (best guess)

        static void apply(@Nonnull TransformComponent tx,
                          @Nonnull Vector3d pos,
                          @Nonnull Vector3f rot) {

            tryInit(tx);

            if (mSetTransformPR != null) {
                try {
                    mSetTransformPR.invoke(tx, pos, rot);
                    return;
                } catch (Throwable ignored) {}
            }

            boolean didPos = false;
            if (mSetPosition != null) {
                try { mSetPosition.invoke(tx, pos); didPos = true; } catch (Throwable ignored) {}
            }
            boolean didRot = false;
            if (mSetRotation != null) {
                try { mSetRotation.invoke(tx, rot); didRot = true; } catch (Throwable ignored) {}
            }
            if (didPos || didRot) return;

            if (mSetTransformObj != null) {
                Object transformObj = null;

                if (cTransform != null) {
                    try {
                        transformObj = cTransform.newInstance(pos, rot);
                    } catch (Throwable ignored) {}
                }

                if (transformObj == null && mGetTransform != null) {
                    try {
                        transformObj = mGetTransform.invoke(tx);
                    } catch (Throwable ignored) {}
                }

                if (transformObj != null) {
                    try {
                        mSetTransformObj.invoke(tx, transformObj);
                    } catch (Throwable ignored) {}
                }
            }
        }

        private static void tryInit(@Nonnull TransformComponent tx) {
            if (cached) return;

            synchronized (TransformComponentCompat.class) {
                if (cached) return;

                Class<?> cls = tx.getClass();

                mSetTransformPR = findMethod(cls, "setTransform", Vector3d.class, Vector3f.class);
                mSetPosition    = findMethod(cls, "setPosition", Vector3d.class);
                mSetRotation    = findMethod(cls, "setRotation", Vector3f.class);

                for (Method m : cls.getMethods()) {
                    if (!m.getName().equals("setTransform")) continue;
                    if (m.getParameterCount() == 1) {
                        mSetTransformObj = m;
                        break;
                    }
                }

                mGetTransform = findMethod(cls, "getTransform");

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

        private static Method findMethod(Class<?> cls, String name, Class<?>... params) {
            try {
                return cls.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    private static final class EntityRemoveCompat {
        static void remove(@Nonnull Store<EntityStore> store,
                           @Nonnull EntityStore entityStore,
                           @Nonnull Ref<EntityStore> ref) {

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
}