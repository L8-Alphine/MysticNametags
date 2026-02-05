package com.mystichorizons.mysticnametags.nameplate;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NameplateManager {

    private final Map<UUID, String> original = new ConcurrentHashMap<>();

    private static final NameplateManager INSTANCE = new NameplateManager();

    public static NameplateManager get() {
        return INSTANCE;
    }

    /**
     * Apply a custom nameplate and remember the original text.
     * MUST be called on the world's thread.
     */
    public void apply(@Nonnull UUID uuid,
                      @Nonnull Store<EntityStore> store,
                      @Nonnull Ref<EntityStore> entityRef,
                      @Nonnull String newText) {

        store.assertThread(); // defensive – will throw clearly if misused


        Nameplate nameplate = store.getComponent(entityRef, Nameplate.getComponentType());
        if (nameplate == null) {
            nameplate = new Nameplate(newText);
            store.addComponent(entityRef, Nameplate.getComponentType(), nameplate);
            // No original in this case – treat empty as default.
        } else {
            original.putIfAbsent(uuid, nameplate.getText());
            nameplate.setText(newText);
        }
    }

    /**
     * Restore the original nameplate text if we cached one.
     * MUST be called on the world's thread.
     */
    public void restore(@Nonnull UUID uuid,
                        @Nonnull Store<EntityStore> store,
                        @Nonnull Ref<EntityStore> entityRef,
                        @Nonnull String fallbackName) {

        store.assertThread(); // defensive

        Nameplate nameplate = store.getComponent(entityRef, Nameplate.getComponentType());
        String originalText = original.remove(uuid);

        String text = (originalText != null) ? originalText : fallbackName;

        if (nameplate == null) {
            nameplate = new Nameplate(text);
            store.addComponent(entityRef, Nameplate.getComponentType(), nameplate);
        } else {
            nameplate.setText(text);
        }
    }

    /**
     * Called when the player fully leaves the server. No store access here.
     */
    public void forget(@Nonnull UUID uuid) {
        original.remove(uuid);
    }

    public void clearAll() {
        original.clear();
    }
}
