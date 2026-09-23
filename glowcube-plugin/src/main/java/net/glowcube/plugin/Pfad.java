package net.glowcube.plugin;

import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * A*-Wegsuche des Agenten - wie im Client (AgentPfad): feste Bloecke kosten
 * ihre Abbauzeit, so graebt er Tunnel und Treppen, wo es kuerzer ist; mit
 * Bausteinen baut er Bruecken und Tuerme. Lava und Unzerstoerbares sind tabu.
 */
final class Pfad {
    private static final int[][] RICHTUNGEN = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final World welt;
    private final boolean bausteine;
    private final HashMap<Long, Double> zellKosten = new HashMap<>();
    private final HashMap<Long, Boolean> traegtCache = new HashMap<>();

    Pfad(World welt, boolean bausteine) {
        this.welt = welt;
        this.bausteine = bausteine;
    }

    private record Knoten(Pos pos, double g, double f, Knoten vorher) {
    }

    private record Zug(Pos nach, double kosten) {
    }

    List<Pos> suchen(Pos start, Predicate<Pos> ziel, Pos richtwert, int maxKnoten, int maxAbstand) {
        PriorityQueue<Knoten> offen = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        HashMap<Long, Double> besteG = new HashMap<>();
        offen.add(new Knoten(start, 0, start.manhattan(richtwert), null));
        besteG.put(start.schluessel(), 0.0);
        int besucht = 0;
        while (!offen.isEmpty() && besucht < maxKnoten) {
            Knoten k = offen.poll();
            Double bekannt = besteG.get(k.pos.schluessel());
            if (bekannt != null && bekannt < k.g) {
                continue;
            }
            besucht++;
            if (ziel.test(k.pos)) {
                List<Pos> weg = new ArrayList<>();
                for (Knoten n = k; n.vorher != null; n = n.vorher) {
                    weg.add(n.pos);
                }
                Collections.reverse(weg);
                return weg;
            }
            for (Zug zug : zuege(k.pos)) {
                if (zug.kosten == Bloecke.NIE || zug.nach.manhattan(start) > maxAbstand) {
                    continue;
                }
                double g = k.g + zug.kosten;
                long s = zug.nach.schluessel();
                Double alt = besteG.get(s);
                if (alt == null || g < alt) {
                    besteG.put(s, g);
                    offen.add(new Knoten(zug.nach, g, g + zug.nach.manhattan(richtwert), k));
                }
            }
        }
        return null;
    }

    private List<Zug> zuege(Pos p) {
        List<Zug> liste = new ArrayList<>(10);
        for (int[] r : RICHTUNGEN) {
            Pos seit = p.plus(r[0], 0, r[1]);
            liste.add(new Zug(seit, 1 + zelle(seit) + zelle(seit.hoch()) + halt(seit)));
            Pos hoch = seit.hoch();
            liste.add(new Zug(hoch, 1.5 + zelle(p.hoch(2)) + zelle(hoch) + zelle(hoch.hoch()) + halt(hoch)));
            Pos runter = seit.runter();
            liste.add(new Zug(runter, 1.5 + zelle(seit.hoch()) + zelle(seit) + zelle(runter) + halt(runter)));
        }
        Pos unten = p.runter();
        liste.add(new Zug(unten, 2 + zelle(unten) + halt(unten)));
        if (bausteine) {
            Pos oben = p.hoch();
            liste.add(new Zug(oben, 4 + zelle(oben.hoch())));
        }
        return liste;
    }

    double zelle(Pos p) {
        long s = p.schluessel();
        Double c = zellKosten.get(s);
        if (c != null) {
            return c;
        }
        double kosten;
        if (!Bloecke.geladen(welt, p)) {
            kosten = Bloecke.NIE;
        } else {
            Block b = p.block(welt);
            if (Bloecke.frei(b)) {
                kosten = b.isLiquid() ? 2 : 0;
            } else if (Bloecke.abbaubar(b)) {
                kosten = 1 + Bloecke.abbauTicks(b) / 4.0;
            } else {
                kosten = Bloecke.NIE;
            }
        }
        zellKosten.put(s, kosten);
        return kosten;
    }

    private double halt(Pos fuesse) {
        Pos unten = fuesse.runter();
        long s = unten.schluessel();
        Boolean traegt = traegtCache.get(s);
        if (traegt == null) {
            traegt = Bloecke.geladen(welt, unten) && Bloecke.traegt(unten.block(welt));
            traegtCache.put(s, traegt);
        }
        if (traegt) {
            return 0;
        }
        return bausteine ? 3 : Bloecke.NIE;
    }
}
