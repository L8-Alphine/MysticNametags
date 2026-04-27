package com.mystichorizons.mysticnametags.nameplate.packet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public final class PacketGlyphState {

    private final Map<UUID, Map<Integer, ViewerState>> viewersBySubject = new HashMap<>();

    @Nonnull
    public ViewerState viewer(@Nonnull UUID subjectUuid, int viewerNetworkId, @Nonnull UUID viewerUuid) {
        ViewerState state = viewersBySubject
                .computeIfAbsent(subjectUuid, ignored -> new HashMap<>())
                .computeIfAbsent(viewerNetworkId, ignored -> new ViewerState(viewerNetworkId, viewerUuid));

        state.viewerUuid = viewerUuid;
        return state;
    }

    @Nonnull
    public Map<Integer, ViewerState> snapshotViewers(@Nonnull UUID subjectUuid) {
        Map<Integer, ViewerState> viewers = viewersBySubject.get(subjectUuid);
        if (viewers == null || viewers.isEmpty()) {
            return Collections.emptyMap();
        }
        return new HashMap<>(viewers);
    }

    public void removeViewer(@Nonnull UUID subjectUuid, int viewerNetworkId) {
        Map<Integer, ViewerState> viewers = viewersBySubject.get(subjectUuid);
        if (viewers == null) {
            return;
        }

        viewers.remove(viewerNetworkId);

        if (viewers.isEmpty()) {
            viewersBySubject.remove(subjectUuid);
        }
    }

    public void clearSubject(@Nonnull UUID subjectUuid) {
        viewersBySubject.remove(subjectUuid);
    }

    public static final class ViewerState {
        public final int viewerNetworkId;
        public UUID viewerUuid;
        public final Set<Integer> spawnedIds = new HashSet<>();

        public float lastYawDegrees = Float.NaN;
        public double lastBaseX = Double.NaN;
        public double lastBaseY = Double.NaN;
        public double lastBaseZ = Double.NaN;

        public long nextUpdateAtMs = 0L;
        public long forceUpdateAtMs = 0L;

        private ViewerState(int viewerNetworkId, @Nonnull UUID viewerUuid) {
            this.viewerNetworkId = viewerNetworkId;
            this.viewerUuid = viewerUuid;
        }
    }
}