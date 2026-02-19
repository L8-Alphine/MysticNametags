package com.mystichorizons.mysticnametags.integrations.permissions;

import java.util.UUID;

public interface PermissionSupport {

    boolean isAvailable();

    boolean hasPermission(UUID uuid, String permissionNode);

    /**
     * Try to grant a permission to a user in a persistent way.
     * Returns true if it appears to succeed.
     */
    boolean grantPermission(UUID uuid, String permissionNode);

    default boolean revokePermission(UUID uuid, String node) {
        return false;
    }

    String getBackendName();
}
