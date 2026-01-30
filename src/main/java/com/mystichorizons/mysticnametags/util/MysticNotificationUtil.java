package com.mystichorizons.mysticnametags.util;

import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nullable;

/**
 * Notification helper for MysticNameTags.
 * Parses & / &#hex into a rich Message before sending.
 */
public final class MysticNotificationUtil {

    private MysticNotificationUtil() {}

    public static void send(
            PacketHandler handler,
            String title,
            @Nullable String body,
            NotificationStyle style
    ) {
        if (handler == null || title == null) {
            return;
        }

        Message titleMsg = ColorFormatter.toMessage(title);
        Message bodyMsg  = (body != null && !body.isEmpty())
                ? ColorFormatter.toMessage(body)
                : null;

        if (bodyMsg == null) {
            NotificationUtil.sendNotification(handler, titleMsg, style);
        } else {
            NotificationUtil.sendNotification(handler, titleMsg, bodyMsg, style);
        }
    }
}
