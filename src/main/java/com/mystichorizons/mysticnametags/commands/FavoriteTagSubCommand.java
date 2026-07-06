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
import java.util.Set;
import java.util.UUID;

public class FavoriteTagSubCommand extends AbstractPlayerCommand {

    @Nonnull
    private final OptionalArg<String> actionArg =
            this.withOptionalArg("action", "add/remove/toggle/list", ArgTypes.STRING);

    @Nonnull
    private final OptionalArg<String> tagArg =
            this.withOptionalArg("tagId", "Tag id", ArgTypes.STRING);

    public FavoriteTagSubCommand() {
        super("favorite", "Manage favorite tags");
        this.addAliases("fav", "favorites");
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
            Set<String> favorites = manager.getFavoriteTags(uuid);
            if (favorites.isEmpty()) {
                context.sendMessage(colored(lang.tr("cmd.favorite.empty")));
            } else {
                context.sendMessage(colored(lang.tr("cmd.favorite.list", Map.of(
                        "tags", String.join(", ", favorites)
                ))));
            }
            return;
        }

        String tagId = tagArg.get(context);
        if (tagId == null || tagId.isBlank()) {
            context.sendMessage(colored(lang.tr("cmd.favorite.usage")));
            return;
        }

        TagManager.FavoriteResult result;
        switch (mode) {
            case "add", "set" -> result = manager.setFavorite(uuid, tagId, true, playerRef.getUsername());
            case "remove", "delete", "del" -> result = manager.setFavorite(uuid, tagId, false, playerRef.getUsername());
            case "toggle" -> result = manager.toggleFavorite(uuid, tagId, playerRef.getUsername());
            default -> {
                context.sendMessage(colored(lang.tr("cmd.favorite.usage")));
                return;
            }
        }

        switch (result) {
            case ADDED -> context.sendMessage(colored(lang.tr("cmd.favorite.added", Map.of("tagId", tagId))));
            case REMOVED -> context.sendMessage(colored(lang.tr("cmd.favorite.removed", Map.of("tagId", tagId))));
            case NOT_OWNED -> context.sendMessage(colored(lang.tr("cmd.favorite.not_owned", Map.of("tagId", tagId))));
            case NOT_FOUND -> context.sendMessage(colored(lang.tr("cmd.favorite.not_found", Map.of("tagId", tagId))));
        }
    }
}
