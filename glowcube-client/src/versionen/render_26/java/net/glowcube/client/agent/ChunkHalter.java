package net.glowcube.client.agent;

import net.glowcube.client.GlowCubeClient;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Haelt die Chunks um einen Punkt geladen; gibt nur wieder frei, was er selbst erzwungen hat. */
final class ChunkHalter {
    private final Set<Long> erzwungen = new HashSet<>();
    private ServerLevel welt;

    void halten(ServerLevel neueWelt, BlockPos um, int radius) {
        if (welt != null && welt != neueWelt) {
            freigeben();
        }
        welt = neueWelt;
        ChunkPos mitte = ChunkPos.containing(um);
        Set<Long> gewuenscht = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                gewuenscht.add(ChunkPos.pack(mitte.x() + dx, mitte.z() + dz));
            }
        }
        for (long c : new ArrayList<>(erzwungen)) {
            if (!gewuenscht.contains(c)) {
                welt.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
                erzwungen.remove(c);
            }
        }
        for (long c : gewuenscht) {
            if (!erzwungen.contains(c) && !welt.getForceLoadedChunks().contains((long) c)) {
                welt.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), true);
                erzwungen.add(c);
            }
        }
    }

    void freigeben() {
        if (welt == null) {
            return;
        }
        for (long c : erzwungen) {
            try {
                welt.setChunkForced(ChunkPos.getX(c), ChunkPos.getZ(c), false);
            } catch (RuntimeException fehler) {
                GlowCubeClient.LOGGER.warn("GlowCube: Chunk nicht freigegeben", fehler);
            }
        }
        erzwungen.clear();
    }
}
