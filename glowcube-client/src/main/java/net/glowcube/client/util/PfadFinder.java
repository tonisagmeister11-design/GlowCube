package net.glowcube.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Ein schlanker A*-Wegfinder nach dem Vorbild von Baritone (LGPL-3.0) -
 * dessen Prinzip, nicht sein Code: Baritones eigentliche Umsetzung ist
 * obfuskiert und fuer eine alte Spielfassung gebaut, also nicht uebernehmbar.
 * Nachgezogen ist die Idee: eine A*-Suche ueber echte Spielbewegungen mit
 * Kosten je Bewegung.
 *
 * <p>Der Suchraum sind begehbare Standflaechen (ein Feld, in dem der Spieler
 * mit Kopffreiheit auf festem Boden steht). Von jedem Feld gehen aus:
 * <ul>
 *   <li>gehen auf gleicher Hoehe zu den vier Nachbarn,</li>
 *   <li>eine Stufe hoch (Y+1),</li>
 *   <li>fallen (bis {@value #MAX_FALL} tief, also ohne Fallschaden),</li>
 *   <li>ueber eine Ein-Feld-Luecke springen.</li>
 * </ul>
 *
 * <p>Wasser und Lava sind keine Standflaechen - der Weg fuehrt von selbst
 * darum herum. Die Suche ist gedeckelt ({@value #KNOTEN_BUDGET} Knoten,
 * {@value #MAX_RADIUS} Felder Umkreis), damit sie nie das Spiel einfriert;
 * wird das Ziel nicht erreicht, kommt der Weg bis zum naechstgelegenen Feld
 * zurueck, damit der Bot wenigstens in die richtige Richtung laeuft.
 */
public final class PfadFinder {
    private static final int KNOTEN_BUDGET = 6000;
    private static final int MAX_RADIUS = 64;
    private static final int MAX_HOEHE = 48;
    private static final int MAX_FALL = 3;

    private static final int[][] RICHTUNGEN = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private PfadFinder() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private record Knoten(long pos, double g, double f) {
    }

    /**
     * Sucht einen Weg vom Startfeld zu einem Feld nahe dem Ziel. Gibt die
     * Wegpunkte in Reihenfolge zurueck (ohne das Startfeld) oder eine leere
     * Liste, wenn gar nichts gefunden wurde.
     */
    public static List<BlockPos> finde(BlockPos start, BlockPos ziel) {
        try {
            return suche(start, ziel);
        } catch (RuntimeException fehler) {
            // Ein Wegfinder, der stolpert, darf nie den Spiel-Tick mitreissen.
            return List.of();
        }
    }

    private static List<BlockPos> suche(BlockPos start, BlockPos ziel) {
        if (mc().level == null) {
            return List.of();
        }
        BlockPos zielSteh = standflaecheNahe(ziel);
        long startKey = start.asLong();
        long zielKey = zielSteh.asLong();

        Map<Long, Double> beste = new HashMap<>();
        Map<Long, Long> herkunft = new HashMap<>();
        PriorityQueue<Knoten> offen = new PriorityQueue<>(Comparator.comparingDouble(Knoten::f));

        beste.put(startKey, 0.0);
        offen.add(new Knoten(startKey, 0.0, heuristik(start, zielSteh)));

        long naehester = startKey;
        double naehesteHeur = heuristik(start, zielSteh);
        int ausgebaut = 0;

        while (!offen.isEmpty() && ausgebaut < KNOTEN_BUDGET) {
            Knoten k = offen.poll();
            if (k.g() > beste.getOrDefault(k.pos(), Double.MAX_VALUE)) {
                continue;   // veralteter Eintrag
            }
            if (k.pos() == zielKey) {
                return zusammenbauen(herkunft, k.pos());
            }
            ausgebaut++;
            BlockPos p = BlockPos.of(k.pos());

            for (long nachbar : nachbarn(p, start)) {
                double neu = k.g() + kosten(p, BlockPos.of(nachbar));
                if (neu < beste.getOrDefault(nachbar, Double.MAX_VALUE)) {
                    beste.put(nachbar, neu);
                    herkunft.put(nachbar, k.pos());
                    double h = heuristik(BlockPos.of(nachbar), zielSteh);
                    offen.add(new Knoten(nachbar, neu, neu + h));
                    if (h < naehesteHeur) {
                        naehesteHeur = h;
                        naehester = nachbar;
                    }
                }
            }
        }

        // Kein voller Weg: wenigstens bis zum naechstgelegenen erreichten Feld.
        if (naehester != startKey) {
            return zusammenbauen(herkunft, naehester);
        }
        return List.of();
    }

    private static List<BlockPos> zusammenbauen(Map<Long, Long> herkunft, long ende) {
        List<BlockPos> weg = new ArrayList<>();
        long jetzt = ende;
        while (herkunft.containsKey(jetzt)) {
            weg.add(BlockPos.of(jetzt));
            jetzt = herkunft.get(jetzt);
        }
        java.util.Collections.reverse(weg);
        return weg;
    }

    private static double heuristik(BlockPos p, BlockPos ziel) {
        double dx = p.getX() - ziel.getX();
        double dy = p.getY() - ziel.getY();
        double dz = p.getZ() - ziel.getZ();
        // Leicht gewichtet: etwas gieriger, dafuer schneller fertig.
        return Math.sqrt(dx * dx + dy * dy + dz * dz) * 1.2;
    }

    private static double kosten(BlockPos von, BlockPos nach) {
        int dy = nach.getY() - von.getY();
        int waagrecht = Math.abs(nach.getX() - von.getX()) + Math.abs(nach.getZ() - von.getZ());
        double kosten = waagrecht;              // 1 je Feld, 2 fuer den Luecken-Sprung
        if (dy > 0) {
            kosten += 1.0;                      // Stufe hoch kostet extra
        } else if (dy < 0) {
            kosten += 0.3 * (-dy);              // Fallen ist billig, aber nicht gratis
        }
        return kosten;
    }

    private static List<Long> nachbarn(BlockPos p, BlockPos start) {
        List<Long> aus = new ArrayList<>(8);
        boolean kopfFrei = passierbar(p.above().above());

        for (int[] r : RICHTUNGEN) {
            int nx = p.getX() + r[0];
            int nz = p.getZ() + r[1];

            BlockPos eben = new BlockPos(nx, p.getY(), nz);
            if (standbar(eben)) {
                hinzu(aus, eben, start);
                continue;
            }
            // Stufe hoch.
            BlockPos hoch = new BlockPos(nx, p.getY() + 1, nz);
            if (kopfFrei && standbar(hoch)) {
                hinzu(aus, hoch, start);
                continue;
            }
            // Seitlich frei? Dann fallen oder Luecke pruefen.
            if (!passierbar(eben)) {
                continue;   // Wand, kein Durchkommen
            }
            // Fallen (bis MAX_FALL tief).
            boolean gefallen = false;
            for (int dy = 1; dy <= MAX_FALL; dy++) {
                BlockPos land = new BlockPos(nx, p.getY() - dy, nz);
                if (standbar(land)) {
                    hinzu(aus, land, start);
                    gefallen = true;
                    break;
                }
                if (fest(land)) {
                    break;  // fester Block, aber keine Standflaeche darauf
                }
            }
            if (gefallen) {
                continue;
            }
            // Ein-Feld-Luecke ueberspringen: zwei Felder weit, gleiche Hoehe.
            BlockPos weit = new BlockPos(p.getX() + 2 * r[0], p.getY(), p.getZ() + 2 * r[1]);
            if (passierbar(eben.above()) && !fest(eben.below()) && standbar(weit)) {
                hinzu(aus, weit, start);
            }
        }
        return aus;
    }

    private static void hinzu(List<Long> aus, BlockPos pos, BlockPos start) {
        if (Math.abs(pos.getX() - start.getX()) > MAX_RADIUS
                || Math.abs(pos.getZ() - start.getZ()) > MAX_RADIUS
                || Math.abs(pos.getY() - start.getY()) > MAX_HOEHE) {
            return;
        }
        aus.add(pos.asLong());
    }

    /** Ein Feld, auf dem man mit Kopffreiheit auf festem Boden stehen kann. */
    private static boolean standbar(BlockPos fuss) {
        return fest(fuss.below())
                && passierbar(fuss)
                && passierbar(fuss.above())
                && !fluessig(fuss)
                && !fluessig(fuss.below());
    }

    private static boolean passierbar(BlockPos pos) {
        return mc().level.getBlockState(pos).getCollisionShape(mc().level, pos).isEmpty();
    }

    private static boolean fest(BlockPos pos) {
        return !mc().level.getBlockState(pos).getCollisionShape(mc().level, pos).isEmpty();
    }

    private static boolean fluessig(BlockPos pos) {
        return !mc().level.getBlockState(pos).getFluidState().isEmpty();
    }

    /**
     * Das Ziel selbst ist meist ein fester Block (ein Erz), auf dem man nicht
     * stehen kann. Gesucht wird das naechste begehbare Feld daneben oder
     * darueber - dort will der Bot hin, um das Ziel in Reichweite zu haben.
     */
    private static BlockPos standflaecheNahe(BlockPos ziel) {
        if (standbar(ziel)) {
            return ziel;
        }
        BlockPos oben = ziel.above();
        if (standbar(oben)) {
            return oben;
        }
        for (int[] r : RICHTUNGEN) {
            BlockPos n = new BlockPos(ziel.getX() + r[0], ziel.getY(), ziel.getZ() + r[1]);
            if (standbar(n)) {
                return n;
            }
        }
        return oben;   // beste Naeherung; die Suche kommt so nah wie moeglich heran
    }
}
