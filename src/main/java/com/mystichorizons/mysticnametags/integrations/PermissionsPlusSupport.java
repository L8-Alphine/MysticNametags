package com.mystichorizons.mysticnametags.integrations;

import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.permissions.provider.PermissionProvider;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Permission backend that uses Hytale's PermissionsModule.
 * If PermissionsPlus is installed, it registers a provider there and this
 * will naturally talk to it.
 */
public final class PermissionsPlusSupport implements PermissionSupport {

    private final boolean detected;

    public PermissionsPlusSupport() {
        boolean found;
        try {
            // Any of these should exist if PermissionsPlus is on the classpath
            Class.forName("games.player.perms.permissions.ServerPermissionsManager");
            found = true;
        } catch (Throwable t) {
            found = false;
        }
        this.detected = found;
    }

    @Override
    public boolean isAvailable() {
        // We only advertise this backend if PermissionsPlus classes are present.
        return detected;
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
        return detected ? "PermissionsPlus" : "PermissionsModule";
    }
}
