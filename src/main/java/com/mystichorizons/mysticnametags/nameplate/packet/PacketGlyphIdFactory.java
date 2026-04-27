package com.mystichorizons.mysticnametags.nameplate.packet;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class PacketGlyphIdFactory {

    private PacketGlyphIdFactory() {
    }

    public static int glyphId(@Nonnull UUID subjectUuid,
                              int viewerNetworkId,
                              int lineIndex,
                              int glyphIndex) {
        int hash = 17;
        hash = 31 * hash + subjectUuid.hashCode();
        hash = 31 * hash + viewerNetworkId;
        hash = 31 * hash + lineIndex;
        hash = 31 * hash + glyphIndex;
        hash = Math.abs(hash);

        if (hash == 0 || hash == Integer.MIN_VALUE) {
            hash = 1;
        }

        return hash;
    }
}