package com.mystichorizons.mysticnametags.nameplate.packet;

import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.entities.EntityUpdates;
import com.hypixel.hytale.server.core.receiver.IPacketReceiver;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PacketGlyphSender {

    private static volatile boolean packetGlyphsRuntimeDisabled = false;
    private static final boolean PACKET_CANARY_ONLY = false;

    // Cache the direct packet handler per player. Never recursively reflect through PlayerRef
    // from the world thread; some server accessors acquire locks and can stall shutdown/join.
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
                                                int mountedToNetworkId,
                                                @Nonnull com.hypixel.hytale.protocol.Model model,
                                                double x,
                                                double y,
                                                double z,
                                                float offsetX,
                                                float offsetY,
                                                float offsetZ,
                                                float yaw,
                                                float scale) {

        ModelTransform transform = new ModelTransform();
        setPosition(transform, x, y, z);
        transform.bodyOrientation = new Direction(yaw, 0.0f, 0.0f);
        transform.lookOrientation = new Direction(yaw, 0.0f, 0.0f);

        return new EntityUpdate(
                networkId,
                null,
                spawnComponents(
                        mountedToNetworkId,
                        offsetX,
                        offsetY,
                        offsetZ,
                        transform,
                        model,
                        scale
                )
        );
    }

    @Nonnull
    private static ComponentUpdate[] spawnComponents(int mountedToNetworkId,
                                                     float offsetX,
                                                     float offsetY,
                                                     float offsetZ,
                                                     @Nonnull ModelTransform transform,
                                                     @Nonnull com.hypixel.hytale.protocol.Model model,
                                                     float scale) {
        List<ComponentUpdate> updates = new ArrayList<>();

        // MUST be first
        updates.add(new NewSpawnUpdate());

        // REQUIRED baseline safety components
        updates.add(new IntangibleUpdate());
        updates.add(new InteractableUpdate());
        updates.add(new HitboxCollisionUpdate(0));

        // Optional but often required for model-based entities
        updates.add(new PropUpdate());

        // Then your actual data
        updates.add(new TransformUpdate(transform));

        if (mountedToNetworkId > 0) {
            updates.add(new MountedUpdate(
                    mountedToNetworkId,
                    new Vector3f(offsetX, offsetY, offsetZ),
                    MountController.Minecart,
                    null
            ));
        }

        updates.add(new ModelUpdate(model, scale));

        return updates.toArray(new ComponentUpdate[0]);
    }

    public static void updateMountedGlyph(@Nonnull PlayerRef viewer,
                                          int networkId,
                                          int mountedToNetworkId,
                                          double x,
                                          double y,
                                          double z,
                                          float offsetX,
                                          float offsetY,
                                          float offsetZ,
                                          float yaw) {
        if (packetGlyphsRuntimeDisabled) {
            return;
        }

        try {
            ModelTransform transform = new ModelTransform();
            setPosition(transform, x, y, z);
            transform.bodyOrientation = new Direction(yaw, 0.0f, 0.0f);
            transform.lookOrientation = new Direction(yaw, 0.0f, 0.0f);

            EntityUpdate update = new EntityUpdate(
                    networkId,
                    new ComponentUpdateType[0],
                    moveComponents(mountedToNetworkId, offsetX, offsetY, offsetZ, transform)
            );

            safeWrite(viewer, new EntityUpdates(null, new EntityUpdate[]{update}));
        } catch (Throwable t) {
            disableRuntime("updateMountedGlyph packet build failed", t);
        }
    }

    @Nonnull
    private static ComponentUpdate[] moveComponents(int mountedToNetworkId,
                                                    float offsetX,
                                                    float offsetY,
                                                    float offsetZ,
                                                    @Nonnull ModelTransform transform) {
        if (mountedToNetworkId <= 0) {
            return new ComponentUpdate[]{new TransformUpdate(transform)};
        }

        return new ComponentUpdate[]{
                new TransformUpdate(transform),
                new MountedUpdate(
                        mountedToNetworkId,
                        new Vector3f(offsetX, offsetY, offsetZ),
                        MountController.Minecart,
                        null
                )
        };
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
                receiver = viewer.getPacketHandler();
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

}
