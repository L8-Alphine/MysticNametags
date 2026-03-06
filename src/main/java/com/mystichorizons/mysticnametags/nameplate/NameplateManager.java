package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NameplateManager {

    private final Map<UUID, String> original = new ConcurrentHashMap<>();

    private static final NameplateManager INSTANCE = new NameplateManager();
    public static NameplateManager get() { return INSTANCE; }

    private NameplateManager() {}

    public void apply(@Nonnull UUID uuid,
                      @Nonnull Store<EntityStore> store,
                      @Nonnull Ref<EntityStore> entityRef,
                      @Nonnull String newText) {

        store.assertThread();

        Nameplate nameplate = store.getComponent(entityRef, Nameplate.getComponentType());
        if (nameplate == null) {
            StoreNameplateCompat.set(store, entityRef, new Nameplate(newText));
            return;
        }

        original.putIfAbsent(uuid, nameplate.getText());

        // Write via store to ensure replication
        StoreNameplateCompat.set(store, entityRef, new Nameplate(newText));
    }

    public void restore(@Nonnull UUID uuid,
                        @Nonnull Store<EntityStore> store,
                        @Nonnull Ref<EntityStore> entityRef,
                        @Nonnull String fallbackName) {

        store.assertThread();

        String originalText = original.remove(uuid);
        String text = (originalText != null) ? originalText : fallbackName;

        // Always store-write (even if component exists)
        StoreNameplateCompat.set(store, entityRef, new Nameplate(text));
    }

    public void forget(@Nonnull UUID uuid) {
        original.remove(uuid);
    }

    public void clearAll() {
        original.clear();
    }

    /**
     * Store-level set helper (replication-safe).
     */
    private static final class StoreNameplateCompat {
        private static volatile boolean cached = false;
        private static Method mPutComponent3;
        private static Method mSetComponent3;
        private static Method mUpdateComponent3;

        static void set(Store<EntityStore> store, Ref<EntityStore> ref, Nameplate np) {
            tryInit(store);
            Object type = Nameplate.getComponentType();

            if (mPutComponent3 != null && tryInvoke(mPutComponent3, store, ref, type, np)) return;
            if (mSetComponent3 != null && tryInvoke(mSetComponent3, store, ref, type, np)) return;
            if (mUpdateComponent3 != null && tryInvoke(mUpdateComponent3, store, ref, type, np)) return;

            // Fallback: mutate existing instance (may not replicate on some builds)
            Nameplate existing = store.getComponent(ref, Nameplate.getComponentType());
            if (existing != null) {
                existing.setText(np.getText());
            } else {
                store.addComponent(ref, Nameplate.getComponentType(), np);
            }
        }

        private static boolean tryInvoke(Method m, Object target, Object... args) {
            try { m.invoke(target, args); return true; }
            catch (Throwable ignored) { return false; }
        }

        private static void tryInit(Store<EntityStore> store) {
            if (cached) return;
            synchronized (StoreNameplateCompat.class) {
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
                if (m.getName().equals(name) && m.getParameterCount() == count) return m;
            }
            return null;
        }
    }
}