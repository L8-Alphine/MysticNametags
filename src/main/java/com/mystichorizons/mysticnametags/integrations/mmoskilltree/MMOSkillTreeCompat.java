package com.mystichorizons.mysticnametags.integrations.mmoskilltree;

/**
 * Optional MMOSkillTree availability checks.
 */
public final class MMOSkillTreeCompat {

    private static volatile Boolean available;

    private MMOSkillTreeCompat() {
    }

    public static boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }

        try {
            Class.forName("com.ziggfreed.mmoskilltree.api.MMOSkillTreeAPI");
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }

        return available;
    }
}
