package com.mystichorizons.mysticnametags.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.mystichorizons.mysticnametags.MysticNameTagsPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

public final class ConsoleCommandRunner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ConsoleCommandRunner() {}

    /**
     * Best-effort console dispatch. Returns true if it *appears* to have dispatched.
     * If it returns false, we couldn't locate a known dispatcher method.
     */
    public static boolean dispatchConsole(String command) {
        if (command == null || command.isBlank()) return false;

        // Normalize leading slash (optional)
        String cmd = command.startsWith("/") ? command.substring(1) : command;

        try {
            Object plugin = MysticNameTagsPlugin.getInstance();

            // Many platforms expose some kind of server accessor on the plugin instance.
            Object server = tryCall(plugin, "getServer");
            if (server == null) server = tryCall(plugin, "server");
            if (server == null) server = plugin; // last-resort probe

            // Common manager accessors
            Object cmdMgr = firstNonNull(
                    tryCall(server, "getCommandManager"),
                    tryCall(server, "getCommandSystem"),
                    tryCall(server, "getCommands"),
                    tryCall(server, "getCommandDispatcher")
            );

            // Some APIs dispatch directly on server
            if (tryDispatchOn(server, cmd)) return true;

            // Some APIs dispatch via a manager/system object
            if (cmdMgr != null && tryDispatchOn(cmdMgr, cmd)) return true;

            LOGGER.at(Level.WARNING).log("[MysticNameTags] Could not locate command dispatcher for: " + cmd);
            return false;

        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to dispatch console command: " + command);
            return false;
        }
    }

    // ---------------- Internals ----------------

    private static boolean tryDispatchOn(Object target, String cmd) {
        if (target == null) return false;

        // Probe a few common method names/signatures
        // 1) dispatchConsole(String)
        if (tryInvokeBooleanish(target, "dispatchConsole", new Class[]{String.class}, new Object[]{cmd})) return true;
        if (tryInvokeBooleanish(target, "executeConsole",  new Class[]{String.class}, new Object[]{cmd})) return true;
        if (tryInvokeBooleanish(target, "runConsoleCommand", new Class[]{String.class}, new Object[]{cmd})) return true;

        // 2) dispatch(String) / execute(String)
        if (tryInvokeBooleanish(target, "dispatch", new Class[]{String.class}, new Object[]{cmd})) return true;
        if (tryInvokeBooleanish(target, "execute",  new Class[]{String.class}, new Object[]{cmd})) return true;

        // 3) dispatch(CommandSender, String) / execute(CommandSender, String)
        // We try to resolve a console sender if present
        Object consoleSender = resolveConsoleSender(target);
        if (consoleSender != null) {
            if (tryInvokeBooleanish(target, "dispatch",
                    new Class[]{consoleSender.getClass(), String.class},
                    new Object[]{consoleSender, cmd})) return true;

            if (tryInvokeBooleanish(target, "execute",
                    new Class[]{consoleSender.getClass(), String.class},
                    new Object[]{consoleSender, cmd})) return true;

            // If method expects an interface/supertype, above may miss; try a generic scan:
            if (tryInvokeTwoArgAnySender(target, "dispatch", consoleSender, cmd)) return true;
            if (tryInvokeTwoArgAnySender(target, "execute",  consoleSender, cmd)) return true;
        }

        return false;
    }

    private static Object resolveConsoleSender(Object context) {
        // Try static CommandSender.console() / getConsole() patterns without importing unknown types.
        try {
            // If server/manager has getConsoleSender()
            Object cs = tryCall(context, "getConsoleSender");
            if (cs != null) return cs;
            cs = tryCall(context, "console");
            if (cs != null) return cs;

            // Try loading CommandSender class and calling a static console() / getConsole()
            Class<?> senderClz = Class.forName("com.hypixel.hytale.server.core.command.system.CommandSender");
            Object s = tryCallStatic(senderClz, "console");
            if (s != null) return s;
            s = tryCallStatic(senderClz, "getConsole");
            if (s != null) return s;
        } catch (Throwable ignored) {}

        return null;
    }

    private static Object tryCall(Object target, String methodName, Object... args) {
        try {
            Class<?>[] sig = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) sig[i] = args[i].getClass();
            Method m = findMethodLoosely(target.getClass(), methodName, args.length);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object tryCallStatic(Class<?> clz, String methodName, Object... args) {
        try {
            Method m = findMethodLoosely(clz, methodName, args.length);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean tryInvokeBooleanish(Object target, String methodName, Class<?>[] sig, Object[] args) {
        try {
            Method m = target.getClass().getMethod(methodName, sig);
            m.setAccessible(true);
            Object r = m.invoke(target, args);
            // Some APIs return boolean, some return void; treat non-exception as success.
            if (r == null) return true;
            if (r instanceof Boolean b) return b;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryInvokeTwoArgAnySender(Object target, String methodName, Object sender, String cmd) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2) continue;
                if (!p[1].isAssignableFrom(String.class) && p[1] != String.class) continue;

                // first arg must accept the sender instance
                if (!p[0].isInstance(sender)) continue;

                m.setAccessible(true);
                Object r = m.invoke(target, sender, cmd);
                if (r == null) return true;
                if (r instanceof Boolean b) return b;
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Method findMethodLoosely(Class<?> clz, String name, int paramCount) {
        for (Method m : clz.getMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterCount() != paramCount) continue;
            return m;
        }
        return null;
    }

    private static Object firstNonNull(Object... arr) {
        if (arr == null) return null;
        for (Object o : arr) if (o != null) return o;
        return null;
    }
}
