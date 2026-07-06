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
import com.mystichorizons.mysticnametags.tags.TagManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TagLoadoutSubCommand extends AbstractPlayerCommand {

    @Nonnull
    private final OptionalArg<String> actionArg =
            this.withOptionalArg("action", "save/equip/delete/list", ArgTypes.STRING);

    @Nonnull
    private final OptionalArg<String> nameArg =
            this.withOptionalArg("name", "Loadout name", ArgTypes.STRING);

    public TagLoadoutSubCommand() {
        super("loadout", "Save and equip tag loadouts");
        this.addAliases("tagloadout", "loadouts");
        this.setPermissionGroups();
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    private Message colored(String text) {
        return ColorFormatter.toMessage(text);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        LanguageManager lang = LanguageManager.get();
        TagManager manager = TagManager.get();
        UUID uuid = playerRef.getUuid();

        if (uuid == null) {
            context.sendMessage(colored(lang.tr("cmd.tags.no_account_id")));
            return;
        }

        String action = actionArg.get(context);
        String mode = action == null || action.isBlank()
                ? "list"
                : action.trim().toLowerCase(Locale.ROOT);

        if ("list".equals(mode)) {
            Map<String, String> loadouts = manager.getLoadouts(uuid);
            if (loadouts.isEmpty()) {
                context.sendMessage(colored(lang.tr("cmd.loadout.empty")));
            } else {
                context.sendMessage(colored(lang.tr("cmd.loadout.list", Map.of(
                        "loadouts", loadouts.toString()
                ))));
            }
            return;
        }

        String name = nameArg.get(context);
        if (name == null || name.isBlank()) {
            context.sendMessage(colored(lang.tr("cmd.loadout.usage")));
            return;
        }

        switch (mode) {
            case "save", "set" -> {
                TagManager.LoadoutResult result = manager.saveLoadout(uuid, name, playerRef.getUsername());
                sendLoadoutResult(context, lang, result, name);
            }
            case "equip", "use", "load" -> {
                TagManager.LoadoutEquipResult result = manager.equipLoadout(playerRef, uuid, name);
                if (result.getLoadoutResult() != TagManager.LoadoutResult.EQUIPPED) {
                    sendLoadoutResult(context, lang, result.getLoadoutResult(), name);
                } else {
                    context.sendMessage(colored(lang.tr("cmd.loadout.equipped", Map.of(
                            "name", name,
                            "tagId", result.getTagId() == null ? "unknown" : result.getTagId(),
                            "result", result.getTagResult().name()
                    ))));
                }
            }
            case "delete", "remove", "del" -> {
                TagManager.LoadoutResult result = manager.deleteLoadout(uuid, name, playerRef.getUsername());
                sendLoadoutResult(context, lang, result, name);
            }
            default -> context.sendMessage(colored(lang.tr("cmd.loadout.usage")));
        }
    }

    private void sendLoadoutResult(@Nonnull CommandContext context,
                                   @Nonnull LanguageManager lang,
                                   @Nonnull TagManager.LoadoutResult result,
                                   @Nonnull String name) {
        String key = switch (result) {
            case SAVED -> "cmd.loadout.saved";
            case DELETED -> "cmd.loadout.deleted";
            case NOT_FOUND -> "cmd.loadout.not_found";
            case INVALID_NAME -> "cmd.loadout.invalid_name";
            case NO_EQUIPPED_TAG -> "cmd.loadout.no_equipped";
            case EQUIPPED -> "cmd.loadout.equipped";
        };

        context.sendMessage(colored(lang.tr(key, Map.of("name", name))));
    }
}
