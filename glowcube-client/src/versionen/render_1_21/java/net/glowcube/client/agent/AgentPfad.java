package net.glowcube.client.agent;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * A*-Wegsuche fuer den Agenten. Anders als ein normaler Mob darf er sich
 * durch Stein graben: ein fester Block im Weg kostet so viel, wie das
 * Abbauen dauert. So waehlt er von selbst zwischen Umweg durch die Hoehle und
 * kurzem Tunnel - und graebt Treppen, wenn es hoch oder runter geht.
 *
 * <p>Ein Knoten ist die Position der Fuesse; der Agent ist zwei Bloecke hoch.
 * Zuege: geradeaus, Stufe hoch, Stufe runter, senkrecht runter (Boden
 * abbauen) und - mit Bausteinen im Gepaeck - senkrecht hoch (Turm) sowie
 * ueber Luecken (Bruecke). Unzerstoerbares, Lava und alles, was an Lava
 * grenzt, ist tabu.
 */
final class AgentPfad {
    private static final int[][] RICHTUNGEN = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final ServerLevel welt;
    private final boolean bausteine;
    private final Long2ObjectOpenHashMap<Double> zellKosten = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Boolean> traegtCache = new Long2ObjectOpenHashMap<>();

    AgentPfad(ServerLevel welt, boolean bausteine) {
        this.welt = welt;
        this.bausteine = bausteine;
    }

    private record Knoten(BlockPos pos, double g, double f, Knoten vorher) {
    }

    /**
     * @param start       Fuesse des Agenten
     * @param ziel        wann angekommen
     * @param richtwert   wohin es ungefaehr geht (fuer die Schaetzung)
     * @param maxKnoten   Obergrenze fuer die Suche
     * @param maxAbstand  wie weit sich die Suche vom Start entfernen darf
     * @return die Zellen vom ersten Schritt bis zum Ziel, oder null
     */
    List<BlockPos> suchen(BlockPos start, Predicate<BlockPos> ziel, BlockPos richtwert, int maxKnoten, int maxAbstand) {
        PriorityQueue<Knoten> offen = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Long2ObjectOpenHashMap<Double> besteG = new Long2ObjectOpenHashMap<>();
        offen.add(new Knoten(start, 0, schaetzung(start, richtwert), null));
        besteG.put(start.asLong(), 0.0);
        int besucht = 0;
        while (!offen.isEmpty() && besucht < maxKnoten) {
            Knoten k = offen.poll();
            Double bekannt = besteG.get(k.pos.asLong());
            if (bekannt != null && bekannt < k.g) {
                continue;
            }
            besucht++;
            if (ziel.test(k.pos)) {
                return weg(k);
            }
            for (Zug zug : zuege(k.pos)) {
                if (zug.kosten == AgentBloecke.NIE || zug.nach.distManhattan(start) > maxAbstand) {
                    continue;
                }
                double g = k.g + zug.kosten;
                long schluessel = zug.nach.asLong();
                Double alt = besteG.get(schluessel);
                if (alt == null || g < alt) {
                    besteG.put(schluessel, g);
                    offen.add(new Knoten(zug.nach, g, g + schaetzung(zug.nach, richtwert), k));
                }
            }
        }
        return null;
    }

    private static double schaetzung(BlockPos a, BlockPos b) {
        return a.distManhattan(b);
    }

    private static List<BlockPos> weg(Knoten k) {
        List<BlockPos> liste = new ArrayList<>();
        for (Knoten n = k; n.vorher != null; n = n.vorher) {
            liste.add(n.pos);
        }
        Collections.reverse(liste);
        return liste;
    }

    private record Zug(BlockPos nach, double kosten) {
    }

    private List<Zug> zuege(BlockPos p) {
        List<Zug> liste = new ArrayList<>(10);
        for (int[] r : RICHTUNGEN) {
            BlockPos seit = p.offset(r[0], 0, r[1]);
            // geradeaus
            liste.add(new Zug(seit, 1 + zelle(seit) + zelle(seit.above()) + halt(seit)));
            // Stufe hoch: ueber dem eigenen Kopf muss Platz werden
            BlockPos hoch = seit.above();
            liste.add(new Zug(hoch, 1.5 + zelle(p.above(2)) + zelle(hoch) + zelle(hoch.above()) + halt(hoch)));
            // Stufe runter: die Zelle ueber dem Ziel-Kopf ist der Durchgang
            BlockPos runter = seit.below();
            liste.add(new Zug(runter, 1.5 + zelle(seit.above()) + zelle(seit) + zelle(runter) + halt(runter)));
        }
        // senkrecht runter: den eigenen Boden abbauen
        BlockPos unten = p.below();
        liste.add(new Zug(unten, 2 + zelle(unten) + halt(unten)));
        // senkrecht hoch: nur mit Bausteinen (Turm)
        if (bausteine) {
            BlockPos oben = p.above();
            liste.add(new Zug(oben, 4 + zelle(oben.above())));
        }
        return liste;
    }

    /** Was es kostet, diese Zelle begehbar zu machen: 0 frei, sonst Abbauzeit, NIE wenn tabu. */
    double zelle(BlockPos p) {
        long schluessel = p.asLong();
        Double c = zellKosten.get(schluessel);
        if (c != null) {
            return c;
        }
        double kosten;
        if (!AgentBloecke.geladen(welt, p) || welt.isOutsideBuildHeight(p)) {
            kosten = AgentBloecke.NIE;
        } else {
            BlockState s = welt.getBlockState(p);
            if (AgentBloecke.frei(welt, p, s)) {
                // Wasser geht, aber ungern.
                kosten = s.getFluidState().isEmpty() ? 0 : 2;
            } else if (AgentBloecke.abbaubar(welt, p, s)) {
                kosten = 1 + AgentBloecke.abbauTicks(welt, p, s) / 4.0;
            } else {
                kosten = AgentBloecke.NIE;
            }
        }
        zellKosten.put(schluessel, kosten);
        return kosten;
    }

    /** Kann man in dieser Zelle stehen? 0 ja, 3 mit Bruecke, NIE nein. */
    private double halt(BlockPos fuesse) {
        BlockPos unten = fuesse.below();
        long schluessel = unten.asLong();
        Boolean traegt = traegtCache.get(schluessel);
        if (traegt == null) {
            traegt = AgentBloecke.geladen(welt, unten) && AgentBloecke.traegt(welt, unten, welt.getBlockState(unten));
            traegtCache.put(schluessel, traegt);
        }
        if (traegt) {
            return 0;
        }
        return bausteine ? 3 : AgentBloecke.NIE;
    }
}
