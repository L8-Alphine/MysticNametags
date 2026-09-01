package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
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
            setNameplate(store, entityRef, newText);
            return;
        }

        original.putIfAbsent(uuid, nameplate.getText());

        // Write via store to ensure replication
        setNameplate(store, entityRef, newText);
    }

    public void restore(@Nonnull UUID uuid,
                        @Nonnull Store<EntityStore> store,
                        @Nonnull Ref<EntityStore> entityRef,
                        @Nonnull String fallbackName) {

        store.assertThread();

        String originalText = original.remove(uuid);
        String text = (originalText != null) ? originalText : fallbackName;

        // Always store-write (even if component exists)
        setNameplate(store, entityRef, text);
    }

    public void forget(@Nonnull UUID uuid) {
        original.remove(uuid);
    }

    public void clearAll() {
        original.clear();
    }

    /**
     * Writes the nameplate through the store so the change replicates to viewers.
     *
     * <p>{@code Store#putComponent} is stable public API on Update 6, so this replaces the
     * old reflective probe for {@code putComponent}/{@code setComponent}/{@code
     * updateComponent}. That probe ended in a fallback which mutated the component in place
     * and did not replicate, so a missed lookup silently froze every nameplate.
     */
    private static void setNameplate(@Nonnull Store<EntityStore> store,
                                     @Nonnull Ref<EntityStore> entityRef,
                                     @Nonnull String text) {
        store.putComponent(entityRef, Nameplate.getComponentType(), new Nameplate(text));
    }
}