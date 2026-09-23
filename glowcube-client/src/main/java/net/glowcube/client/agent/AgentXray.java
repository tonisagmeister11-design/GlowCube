package net.glowcube.client.agent;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * X-Ray fuer den Agenten: findet Zielbloecke in allen Chunks um ihn herum,
 * ueber die ganze Welthoehe. Jeder 16er-Abschnitt eines Chunks fuehrt eine
 * Palette seiner Blockarten - enthaelt sie nichts Passendes, wird der ganze
 * Abschnitt uebersprungen. So kostet selbst eine Suche ueber 7x7 Chunks nur
 * einen Bruchteil einer Sekunde.
 */
final class AgentXray {
    private AgentXray() {
    }

    /** Die Zielbloecke, naechste zuerst (hoechstens 64). */
    static List<BlockPos> suchen(ServerLevel welt, BlockPos mitte, int chunks, Auftrag auftrag, String art,
                                 Set<Long> gesperrt) {
        List<BlockPos> treffer = new ArrayList<>();
        int cx0 = mitte.getX() >> 4;
        int cz0 = mitte.getZ() >> 4;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                // Laedt den Chunk, falls noetig - X-Ray soll auch sehen, wo gerade niemand ist.
                LevelChunk chunk = welt.getChunk(cx0 + dx, cz0 + dz);
                LevelChunkSection[] abschnitte = chunk.getSections();
                for (int i = 0; i < abschnitte.length; i++) {
                    LevelChunkSection abschnitt = abschnitte[i];
                    if (abschnitt == null || abschnitt.hasOnlyAir()
                            || !abschnitt.getStates().maybeHas(s -> AgentBloecke.istZiel(s, auftrag, art))) {
                        continue;
                    }
                    int baseX = (cx0 + dx) << 4;
                    int baseY = welt.getSectionYFromSectionIndex(i) << 4;
                    int baseZ = (cz0 + dz) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState s = abschnitt.getBlockState(x, y, z);
                                if (AgentBloecke.istZiel(s, auftrag, art)) {
                                    BlockPos p = new BlockPos(baseX + x, baseY + y, baseZ + z);
                                    if (!gesperrt.contains(p.asLong())) {
                                        treffer.add(p);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        treffer.sort((a, b) -> Double.compare(a.distSqr(mitte), b.distSqr(mitte)));
        return treffer.size() > 64 ? new ArrayList<>(treffer.subList(0, 64)) : treffer;
    }
}
