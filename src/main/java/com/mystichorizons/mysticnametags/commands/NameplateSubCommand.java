package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.nameplate.GlyphNameplateManager;
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

/**
 * /mnametags nameplate [on|off|toggle|status]
 *
 * Toggles the player's own packet glyph nameplate view for third-person camera comfort.
 * Other players still see their nameplate normally.
 */
public class NameplateSubCommand extends AbstractPlayerCommand {

    @Nonnull
    private final OptionalArg<String> modeArg =
            this.withOptionalArg("mode", "on/off/toggle/status", ArgTypes.STRING);

    public NameplateSubCommand() {
        super("nameplate", "Toggle your own packet nameplate visibility");
        this.addAliases(new String[]{"selfview", "nametag"});
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    private Message colored(String text) {
        return ColorFormatter.toMessage(text);
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        LanguageManager lang = LanguageManager.get();
        TagManager manager = TagManager.get();

        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            context.sendMessage(colored(lang.tr("cmd.tags.no_account_id")));
            return;
        }

        String rawMode = modeArg.get(context);
        String mode = rawMode == null ? "toggle" : rawMode.trim().toLowerCase(Locale.ROOT);

        Boolean desired;
        switch (mode) {
            case "", "toggle" -> desired = null;
            case "on", "show", "visible", "enable", "enabled", "true" -> desired = Boolean.TRUE;
            case "off", "hide", "hidden", "disable", "disabled", "false" -> desired = Boolean.FALSE;
            case "status" -> desired = manager.isOwnNameplateVisible(uuid);
            default -> {
                context.sendMessage(colored(lang.tr("cmd.nameplate.usage")));
                return;
            }
        }

        if ("status".equals(mode)) {
            context.sendMessage(colored(lang.tr(Boolean.TRUE.equals(desired)
                    ? "cmd.nameplate.status_visible"
                    : "cmd.nameplate.status_hidden")));
            return;
        }

        boolean visible = desired == null
                ? manager.toggleOwnNameplateVisible(uuid)
                : manager.setOwnNameplateVisible(uuid, desired);

        if (visible) {
            manager.refreshNameplate(playerRef, world);
            context.sendMessage(colored(lang.tr("cmd.nameplate.visible")));
        } else {
            GlyphNameplateManager.get().removeSelfView(uuid, world);
            context.sendMessage(colored(lang.tr("cmd.nameplate.hidden")));
        }
    }
}
