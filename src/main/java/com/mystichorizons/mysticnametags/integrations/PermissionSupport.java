package com.mystichorizons.mysticnametags.integrations;

import javax.annotation.Nullable;
import java.util.UUID;

public interface PermissionSupport {

    boolean isAvailable();

    boolean hasPermission(UUID uuid, String permissionNode);

    /**
     * Try to grant a permission to a user in a persistent way.
     * Returns true if it appears to succeed.
     */
    boolean grantPermission(UUID uuid, String permissionNode);

    String getBackendName();
}
