package com.mystichorizons.mysticnametags.nameplate.packet;

import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.receiver.IPacketReceiver;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketGlyphSender {

    private static volatile boolean packetGlyphsRuntimeDisabled = false;
    private static final boolean PACKET_CANARY_ONLY = true;

    // cache the receiver per player so we don't do the reflective graph walk every packet
    private static final Map<UUID, IPacketReceiver> receiverCache = new ConcurrentHashMap<>();

    private PacketGlyphSender() {
    }

    public static boolean isRuntimeDisabled() {
        return packetGlyphsRuntimeDisabled;
    }

    /** Drop cached receiver for a player. Call on disconnect. */
    public static void evictReceiverCache(@Nonnull UUID uuid) {
        receiverCache.remove(uuid);
    }

    @Nonnull
    public static EntityUpdate glyphSpawnUpdate(int networkId,
                                                @Nonnull String assetId,
                                                double x,
                                                double y,
                                                double z,
                                                float yaw,
                                                float scale) {

        com.hypixel.hytale.protocol.Model model = new com.hypixel.hytale.protocol.Model();
        model.assetId = assetId;
        model.path = assetId;
        model.scale = scale;

        ModelTransform transform = new ModelTransform();
        setPosition(transform, x, y, z);
        transform.bodyOrientation = new Direction(yaw, 0.0f, 0.0f);
        transform.lookOrientation = new Direction(yaw, 0.0f, 0.0f);

        return new EntityUpdate(
                networkId,
                null,
                new ComponentUpdate[]{

                        // MUST be first
                        new NewSpawnUpdate(),

                        // REQUIRED baseline safety components
                        new IntangibleUpdate(),
                        new InteractableUpdate(),
                        new HitboxCollisionUpdate(0),

                        // Optional but often required for model-based entities
                        new PropUpdate(),

                        // Then your actual data
                        new TransformUpdate(transform),
                        new ModelUpdate(model, scale)
                }
        );
    }

    public static void moveGlyph(@Nonnull PlayerRef viewer,
                                 int networkId,
                                 double x,
                                 double y,
                                 double z,
                                 float yaw) {
        if (packetGlyphsRuntimeDisabled) {
            return;
        }

        try {
            EntityUpdate update = new EntityUpdate(
                    networkId,
                    new ComponentUpdateType[0],
                    new ComponentUpdate[]{
                            transform(x, y, z, yaw)
                    }
            );

            safeWrite(viewer, new EntityUpdates(null, new EntityUpdate[]{update}));
        } catch (Throwable t) {
            disableRuntime("moveGlyph packet build failed", t);
        }
    }

    public static boolean spawnMany(@Nonnull PlayerRef viewer,
                                    @Nonnull List<EntityUpdate> updates) {
        if (updates.isEmpty() || packetGlyphsRuntimeDisabled) {
            return false;
        }

        EntityUpdate[] outgoing = PACKET_CANARY_ONLY
                ? new EntityUpdate[]{updates.get(0)}
                : updates.toArray(new EntityUpdate[0]);

        EntityUpdates packet;

        try {
            packet = new EntityUpdates(null, outgoing);
        } catch (Throwable t) {
            disableRuntime("spawnMany packet build failed", t);
            return false;
        }

        return safeWrite(viewer, packet);
    }

    private static boolean safeWrite(@Nonnull PlayerRef viewer, @Nonnull Object packet) {
        if (packetGlyphsRuntimeDisabled) {
            return false;
        }

        if (!(packet instanceof ToClientPacket toClientPacket)) {
            disableRuntime("packet is not ToClientPacket: " + packet.getClass().getName(), null);
            return false;
        }

        try {
            UUID viewerUuid = viewer.getUuid();
            IPacketReceiver receiver = viewerUuid != null ? receiverCache.get(viewerUuid) : null;
            if (receiver == null) {
                // reflective discovery is expensive, only do it once per player
                receiver = findPacketReceiver(viewer, 0, new IdentityHashMap<>());
                if (receiver == null) {
                    disableRuntime("no IPacketReceiver found", null);
                    return false;
                }
                if (viewerUuid != null) {
                    receiverCache.put(viewerUuid, receiver);
                }
            }

            receiver.writeNoCache(toClientPacket);
            return true;
        } catch (Throwable t) {
            // stale cache entry maybe, clear it and let next call rediscover
            try { if (viewer.getUuid() != null) receiverCache.remove(viewer.getUuid()); } catch (Exception ignored) {}
            disableRuntime("packet write failed", t);
            return false;
        }
    }

    public static void removeGlyphs(@Nonnull PlayerRef viewer,
                                    @Nonnull Collection<Integer> ids) {
        if (ids.isEmpty() || packetGlyphsRuntimeDisabled) {
            return;
        }

        EntityUpdates packet;

        try {
            int[] removed = new int[ids.size()];
            int i = 0;
            for (Integer id : ids) {
                removed[i++] = id == null ? 0 : id;
            }

            packet = new EntityUpdates(removed, null);
        } catch (Throwable t) {
            disableRuntime("removeGlyphs packet build failed", t);
            return;
        }

        safeWrite(viewer, packet);
    }

    @Nonnull
    private static TransformUpdate transform(double x, double y, double z, float yaw) {
        ModelTransform transform = new ModelTransform();
        setPosition(transform, x, y, z);
        transform.bodyOrientation = new Direction(yaw, 0.0f, 0.0f);
        transform.lookOrientation = new Direction(yaw, 0.0f, 0.0f);
        return new TransformUpdate(transform);
    }

    private static void setPosition(@Nonnull ModelTransform transform, double x, double y, double z) {
        try {
            Object position;

            try {
                Constructor<Position> ctor = Position.class.getConstructor(double.class, double.class, double.class);
                position = ctor.newInstance(x, y, z);
            } catch (Throwable ignored) {
                position = Position.class.getConstructor().newInstance();
                setNumberField(position, "x", x);
                setNumberField(position, "y", y);
                setNumberField(position, "z", z);
            }

            Field field = ModelTransform.class.getField("position");
            field.set(transform, position);
        } catch (Throwable ignored) {
        }
    }

    private static void setNumberField(@Nonnull Object target, @Nonnull String fieldName, double value) throws Exception {
        Field field = target.getClass().getField(fieldName);
        Class<?> type = field.getType();

        if (type == double.class || type == Double.class) {
            field.set(target, value);
        } else if (type == float.class || type == Float.class) {
            field.set(target, (float) value);
        } else if (type == int.class || type == Integer.class) {
            field.set(target, (int) Math.round(value));
        }
    }

    public static void disableRuntime(String reason, Throwable t) {
        packetGlyphsRuntimeDisabled = true;
        System.out.println("[MysticNameTags] Packet glyphs runtime-disabled: " + reason);
        if (t != null) {
            t.printStackTrace();
        }
    }

    @Nullable
    private static IPacketReceiver findPacketReceiver(Object root,
                                                      int depth,
                                                      Map<Object, Boolean> seen) {
        if (root == null || depth > 3) {
            return null;
        }

        if (seen.containsKey(root)) {
            return null;
        }

        seen.put(root, Boolean.TRUE);

        if (root instanceof IPacketReceiver receiver) {
            return receiver;
        }

        Class<?> type = root.getClass();

        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0) {
                continue;
            }

            String name = method.getName();

            if (name.equals("getClass")
                    || name.equals("hashCode")
                    || name.equals("toString")
                    || name.equals("getUuid")
                    || name.equals("getReference")) {
                continue;
            }

            try {
                Object value = method.invoke(root);
                IPacketReceiver found = findPacketReceiver(value, depth + 1, seen);
                if (found != null) {
                    System.out.println("[MysticNameTags] Found IPacketReceiver through method: " +
                            type.getName() + "#" + name);
                    return found;
                }
            } catch (Throwable ignored) {
            }
        }

        for (Field field : type.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(root);

                IPacketReceiver found = findPacketReceiver(value, depth + 1, seen);
                if (found != null) {
                    System.out.println("[MysticNameTags] Found IPacketReceiver through field: " +
                            type.getName() + "#" + field.getName());
                    return found;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }
}