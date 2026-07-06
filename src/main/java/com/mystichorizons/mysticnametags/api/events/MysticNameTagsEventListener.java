package com.mystichorizons.mysticnametags.api.events;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface MysticNameTagsEventListener {
    void onMysticNameTagsEvent(@Nonnull MysticNameTagsEvent event);
}
