package com.mystichorizons.mysticnametags.integrations.permissions;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.query.QueryOptions;

import javax.annotation.Nullable;
import java.util.UUID;

public final class LuckPermsSupport implements PermissionSupport, PrefixSupport {

    private LuckPerms api;

    public LuckPermsSupport() {
        try {
            this.api = LuckPermsProvider.get();
        } catch (Throwable t) {
            this.api = null;
        }
    }

    @Override
    public boolean isAvailable() {
        return api != null;
    }

    @Override
    public boolean hasPermission(UUID uuid, String node) {
        if (api == null || uuid == null) return false;

        User user = api.getUserManager().getUser(uuid);
        if (user == null) {
            try {
                user = api.getUserManager().loadUser(uuid).join();
            } catch (Throwable ignored) {
                return false;
            }
        }
        if (user == null) return false;

        return user.getCachedData()
                .getPermissionData()
                .checkPermission(node)
                .asBoolean();
    }

    @Override
    public boolean grantPermission(UUID uuid, String node) {
        if (api == null || uuid == null || node == null || node.isEmpty()) {
            return false;
        }

        try {
            User user = api.getUserManager().getUser(uuid);
            if (user == null) {
                user = api.getUserManager().loadUser(uuid).join();
            }
            if (user == null) return false;

            user.data().add(Node.builder(node).build());
            api.getUserManager().saveUser(user);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean revokePermission(UUID uuid, String node) {
        if (api == null || uuid == null || node == null || node.isEmpty()) {
            return false;
        }

        try {
            User user = api.getUserManager().getUser(uuid);
            if (user == null) {
                user = api.getUserManager().loadUser(uuid).join();
            }
            if (user == null) return false;

            user.data().remove(Node.builder(node).build());
            api.getUserManager().saveUser(user);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public String getBackendName() {
        return "LuckPerms";
    }

    // -------- PrefixSupport --------

    @Override
    @Nullable
    public String getPrefix(UUID uuid) {
        if (api == null || uuid == null) return null;

        User user = api.getUserManager().getUser(uuid);
        if (user == null) {
            try {
                user = api.getUserManager().loadUser(uuid).join();
            } catch (Throwable ignored) {
                return null;
            }
        }
        if (user == null) return null;

        QueryOptions options = api.getContextManager()
                .getQueryOptions(user)
                .orElseGet(() -> api.getContextManager().getStaticQueryOptions());

        String prefix = user.getCachedData().getMetaData(options).getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return prefix;
        }

        Group group = api.getGroupManager().getGroup(user.getPrimaryGroup());
        if (group != null) {
            return group.getCachedData().getMetaData(options).getPrefix();
        }

        return null;
    }
}
