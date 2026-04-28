package com.mystichorizons.mysticnametags.nameplate.packet;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class PacketGlyphIdFactory {

    private static final int FAKE_ID_BASE = 1_000_000_000;
    private static final int FAKE_ID_RANGE = 1_000_000_000;

    private PacketGlyphIdFactory() {
    }

    public static int glyphId(@Nonnull UUID subjectUuid,
                              int viewerNetworkId,
                              int lineIndex,
                              int glyphIndex) {
        return fakeId(subjectUuid, viewerNetworkId, lineIndex, glyphIndex, 1);
    }

    public static int lineAnchorId(@Nonnull UUID subjectUuid,
                                   int viewerNetworkId,
                                   int lineIndex) {
        return fakeId(subjectUuid, viewerNetworkId, lineIndex, -1, 2);
    }

    private static int fakeId(@Nonnull UUID subjectUuid,
                              int viewerNetworkId,
                              int lineIndex,
                              int glyphIndex,
                              int kind) {
        int hash = 17;
        hash = 31 * hash + kind;
        hash = 31 * hash + subjectUuid.hashCode();
        hash = 31 * hash + viewerNetworkId;
        hash = 31 * hash + lineIndex;
        hash = 31 * hash + glyphIndex;

        return FAKE_ID_BASE + Math.floorMod(hash, FAKE_ID_RANGE);
    }
}
