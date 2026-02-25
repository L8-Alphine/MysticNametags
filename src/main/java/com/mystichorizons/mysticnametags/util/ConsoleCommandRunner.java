package com.mystichorizons.mysticnametags.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Console command dispatcher hooked directly into Hytale's CommandManager.
 *
 * This bypasses all the old reflection guessing and just calls
 * CommandManager.handleCommand(...) with a fake "console" sender that
 * has all permissions.
 */
public final class ConsoleCommandRunner {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Single shared "console" sender instance
    private static final CommandSender CONSOLE_SENDER = new ConsoleSender();

    private ConsoleCommandRunner() {}

    /**
     * Dispatch a command as console.
     *
     * @param command command string WITH or WITHOUT leading slash ("/")
     * @return true if the command was handed to CommandManager, false if not
     */
    public static boolean dispatchConsole(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }

        // Normalize: CommandManager expects the *name* as the first token,
        // no leading slash.
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        cmd = cmd.trim();
        if (cmd.isEmpty()) {
            return false;
        }

        CommandManager manager = CommandManager.get();
        if (manager == null) {
            LOGGER.at(Level.WARNING)
                    .log("[MysticNameTags] CommandManager.get() returned null – cannot dispatch: " + cmd);
            return false;
        }

        try {
            CompletableFuture<Void> future = manager.handleCommand(CONSOLE_SENDER, cmd);
            String finalCmd = cmd;
            future.exceptionally(t -> {
                LOGGER.at(Level.WARNING).withCause(t)
                        .log("[MysticNameTags] Console command failed: " + finalCmd);
                return null;
            });

            LOGGER.at(Level.FINE)
                    .log("[MysticNameTags] Dispatched console command via CommandManager: " + cmd);
            return true;
        } catch (Throwable t) {
            LOGGER.at(Level.WARNING).withCause(t)
                    .log("[MysticNameTags] Failed to dispatch console command: " + cmd);
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Simple console sender implementation
    // ---------------------------------------------------------------------
    private static final class ConsoleSender implements CommandSender {

        private static final UUID CONSOLE_UUID = new UUID(0L, 0L);

        @Override
        public String getDisplayName() {
            return "Console";
        }

        @Override
        public UUID getUuid() {
            return CONSOLE_UUID;
        }

        // ====== IMessageReceiver bits ======

        @Override
        public void sendMessage(@Nonnull Message message) {
            // You can route this wherever you want; for now just log it.
            // Message likely has a toString() or similar; if not, adjust.
            HytaleLogger.getLogger().at(Level.INFO)
                    .log("[ConsoleCmdOutput] " + message);
        }

        // If IMessageReceiver has other methods like sendMessages(Message...),
        // sendRaw(String), etc., implement them here. A safe default is just
        // to call sendMessage for each.

        // ====== PermissionHolder bits ======

        @Override
        public boolean hasPermission(@Nonnull String permission) {
            // Console typically has *all* permissions.
            return true;
        }

        @Override
        public boolean hasPermission(@NotNull String s, boolean b) {
            return true;
        }
    }
}