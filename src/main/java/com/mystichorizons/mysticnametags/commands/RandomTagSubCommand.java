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

public class RandomTagSubCommand extends AbstractPlayerCommand {

    @Nonnull
    private final OptionalArg<String> scopeArg =
            this.withOptionalArg("scope", "owned/favorites", ArgTypes.STRING);

    public RandomTagSubCommand() {
        super("randomtag", "Equip a random owned or favorite tag");
        this.requireNoPermission();
        this.addAliases("random", "tagrandom");
        this.setPermissionGroups();
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
        UUID uuid = playerRef.getUuid();
        if (uuid == null) {
            context.sendMessage(colored(lang.tr("cmd.tags.no_account_id")));
            return;
        }

        String scope = scopeArg.get(context);
        String normalized = scope == null ? "owned" : scope.trim().toLowerCase(Locale.ROOT);

        boolean favoritesOnly;
        switch (normalized) {
            case "fav", "favs", "favorite", "favorites" -> favoritesOnly = true;
            case "owned", "all", "" -> favoritesOnly = false;
            default -> {
                context.sendMessage(colored(lang.tr("cmd.random.usage")));
                return;
            }
        }

        if (favoritesOnly && TagManager.get().getFavoriteTags(uuid).isEmpty()) {
            context.sendMessage(colored(lang.tr("cmd.random.no_favorites")));
            return;
        }

        TagManager.TagPurchaseResult result = TagManager.get().equipRandomTag(playerRef, uuid, favoritesOnly);
        if (result == TagManager.TagPurchaseResult.NOT_FOUND) {
            context.sendMessage(colored(lang.tr(favoritesOnly ? "cmd.random.no_favorites" : "cmd.random.no_owned")));
            return;
        }

        context.sendMessage(colored(lang.tr("cmd.random.result", Map.of(
                "result", result.name()
        ))));
    }
}
