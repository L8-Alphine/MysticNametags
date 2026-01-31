package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.PermissionProvider;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class NativePermissionsSupport implements PermissionSupport {

    @Override
    public boolean isAvailable() {
        return true; // Hytale PermissionsModule is always present
    }

    @Override
    public boolean hasPermission(UUID uuid, String node) {
        if (uuid == null || node == null || node.isEmpty()) return false;

        try {
            PermissionsModule pm = PermissionsModule.get();
            for (PermissionProvider provider : pm.getProviders()) {
                Set<String> userPerms = provider.getUserPermissions(uuid);
                if (userPerms.contains(node)) {
                    return true;
                }
                for (String group : provider.getGroupsForUser(uuid)) {
                    if (provider.getGroupPermissions(group).contains(node)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public boolean grantPermission(UUID uuid, String node) {
        if (uuid == null || node == null || node.isEmpty()) return false;
        try {
            PermissionsModule pm = PermissionsModule.get();
            pm.addUserPermission(uuid, Collections.singleton(node));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public String getBackendName() {
        return "NativePermissions";
    }
}
