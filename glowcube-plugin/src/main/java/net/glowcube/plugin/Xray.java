package net.glowcube.plugin;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * X-Ray fuer den Agenten: alle Chunks um ihn herum, ganze Hoehe. Auf dem
 * Server-Thread werden nur Momentaufnahmen der Chunks gezogen; das
 * Durchsuchen laeuft nebenher auf einem anderen Thread, damit der Server
 * nicht ruckelt. Leere Abschnitte werden uebersprungen.
 */
final class Xray {
    private Xray() {
    }

    /** Ergebnis: Zielbloecke, naechste zuerst (hoechstens 96). */
    static CompletableFuture<List<Pos>> suchen(World welt, Pos mitte, int chunks, Auftrag auftrag, String art) {
        List<ChunkSnapshot> bilder = new ArrayList<>();
        int cx0 = mitte.x() >> 4;
        int cz0 = mitte.z() >> 4;
        for (int dx = -chunks; dx <= chunks; dx++) {
            for (int dz = -chunks; dz <= chunks; dz++) {
                bilder.add(welt.getChunkAt(cx0 + dx, cz0 + dz).getChunkSnapshot(false, false, false));
            }
        }
        int minY = welt.getMinHeight();
        int maxY = welt.getMaxHeight();
        return CompletableFuture.supplyAsync(() -> {
            List<Pos> treffer = new ArrayList<>();
            for (ChunkSnapshot bild : bilder) {
                int baseX = bild.getX() << 4;
                int baseZ = bild.getZ() << 4;
                for (int abschnitt = minY >> 4; abschnitt < maxY >> 4; abschnitt++) {
                    if (bild.isSectionEmpty(abschnitt - (minY >> 4))) {
                        continue;
                    }
                    for (int y = abschnitt << 4; y < (abschnitt << 4) + 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                Material m = bild.getBlockType(x, y, z);
                                if (Bloecke.istZiel(m, auftrag, art)) {
                                    treffer.add(new Pos(baseX + x, y, baseZ + z));
                                }
                            }
                        }
                    }
                }
            }
            treffer.sort((a, b) -> Double.compare(a.abstandQ(mitte), b.abstandQ(mitte)));
            return treffer.size() > 96 ? new ArrayList<>(treffer.subList(0, 96)) : treffer;
        });
    }
}
