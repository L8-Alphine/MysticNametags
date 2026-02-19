package com.mystichorizons.mysticnametags.commands;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.mystichorizons.mysticnametags.config.LanguageManager;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;

/**
 * /tags help - Show available commands
 */
public class HelpSubCommand extends CommandBase {

    public HelpSubCommand() {
        super("help", "Show available commands");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    private void sendColored(CommandContext context, String text) {
        context.sendMessage(ColorFormatter.toMessage(text));
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        LanguageManager lang = LanguageManager.get();

        sendColored(context, "");
        sendColored(context, lang.tr("cmd.help.header"));
        sendColored(context, lang.tr("cmd.help.line.help"));
        sendColored(context, lang.tr("cmd.help.line.info"));
        sendColored(context, lang.tr("cmd.help.line.reload"));
        sendColored(context, lang.tr("cmd.help.line.ui"));
        sendColored(context, lang.tr("cmd.help.line.tags"));
        sendColored(context, lang.tr("cmd.help.footer"));
    }
}
