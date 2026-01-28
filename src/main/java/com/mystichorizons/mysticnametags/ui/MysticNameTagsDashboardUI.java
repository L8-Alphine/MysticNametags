package com.mystichorizons.mysticnametags.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.mystichorizons.mysticnametags.util.ColorFormatter;

import javax.annotation.Nonnull;

public class MysticNameTagsDashboardUI extends InteractiveCustomUIPage<MysticNameTagsDashboardUI.UIEventData> {

    // Codec for decoding incoming UI event data
    public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(
                    UIEventData.class,
                    UIEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING),
                    (UIEventData e, String v) -> e.action = v,
                    e -> e.action)
            .add()
            .build();

    private final PlayerRef playerRef;

    public MysticNameTagsDashboardUI(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, CODEC);
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {

        // Load the dashboard UI layout
        // Make sure this path matches your asset pack: src/main/resources/Pages/MysticNameTagsDashboard.ui
        commands.append("mysticnametags/Dashboard.ui");

        // Set initial text
        commands.set("#StatusText.Text", "Welcome to MysticNameTags!");

        // Bind buttons
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RefreshButton",
                EventData.of("Action", "refresh")
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                EventData.of("Action", "close")
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull UIEventData data) {

        if (data.action == null) {
            return;
        }

        switch (data.action) {
            case "refresh" -> {
                // Simple feedback notification
                NotificationUtil.sendNotification(
                        playerRef.getPacketHandler(),
                        Message.raw(ColorFormatter.colorize("&bMysticNameTags")),
                        Message.raw(ColorFormatter.colorize("&7Dashboard refreshed.")),
                        NotificationStyle.Success
                );

                UICommandBuilder update = new UICommandBuilder();
                update.set("#StatusText.Text", "Dashboard refreshed!");
                sendUpdate(update, null, false);
            }

            case "close" -> close();

            default -> close();
        }
    }

    // -------- Event data --------

    public static class UIEventData {
        private String action;

        public UIEventData() {
        }

        public String getAction() {
            return action;
        }
    }
}
