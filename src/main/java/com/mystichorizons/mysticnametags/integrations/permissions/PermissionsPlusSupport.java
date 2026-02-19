package com.mystichorizons.mysticnametags.integrations.permissions;

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

    /**
     * Returns true if the permission set contains:
     *  - the exact node
     *  - a global wildcard "*"
     *  - a hierarchical wildcard like "mysticnametags.*" or "mysticnametags.tag.*"
     */
    private boolean hasMatchingNode(Set<String> perms, String target) {
        if (perms == null || perms.isEmpty() || target == null || target.isEmpty()) {
            return false;
        }

        for (String raw : perms) {
            if (raw == null || raw.isEmpty()) continue;

            String perm = raw.toLowerCase();

            // Exact match
            if (perm.equals(target)) {
                return true;
            }

            // Global wildcard
            if (perm.equals("*")) {
                return true;
            }

            // Hierarchical wildcard: "mysticnametags.*", "mysticnametags.tag.*", etc.
            if (perm.endsWith(".*")) {
                String prefix = perm.substring(0, perm.length() - 2);
                if (!prefix.isEmpty() && target.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean hasPermission(UUID uuid, String node) {
        if (uuid == null || node == null || node.isEmpty()) return false;

        final String target = node.toLowerCase();

        try {
            PermissionsModule pm = PermissionsModule.get();

            for (PermissionProvider provider : pm.getProviders()) {

                Set<String> userPerms = provider.getUserPermissions(uuid);
                if (hasMatchingNode(userPerms, target)) {
                    return true;
                }

                for (String group : provider.getGroupsForUser(uuid)) {
                    Set<String> groupPerms = provider.getGroupPermissions(group);
                    if (hasMatchingNode(groupPerms, target)) {
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
    public boolean revokePermission(UUID uuid, String node) {
        if (uuid == null || node == null || node.isEmpty()) return false;

        try {
            PermissionsModule pm = PermissionsModule.get();
            pm.removeUserPermission(uuid, Collections.singleton(node));
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
