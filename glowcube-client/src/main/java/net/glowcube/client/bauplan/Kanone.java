package net.glowcube.client.bauplan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Die Orbital Strike Cannon als Bauplan gelesen: woran man erkennt, dass sie
 * wirklich dasteht, und wo ihre Teile liegen - fuer die Vorfuehrung beim
 * Feuern (Signal durch die Leitungen, TNT in den Ladearmen, Portale, Abschuss
 * aus der Mitte).
 *
 * <p>Alle Stellen sind relativ zum Feuer-Hebel ({@link #HEBEL_X},
 * {@link #HEBEL_Y}, {@link #HEBEL_Z} im Plan) und lassen sich um 0/90/180/270
 * Grad drehen - so passt es auch, wenn die Kanone gedreht gebaut wurde.
 *
 * <p>Ohne Minecraft-Klassen, damit Client und Plugin dieselbe Datei nutzen.
 * Liegt wortgleich auch im Plugin - bei Aenderungen beide nachziehen.
 */
public final class Kanone {
    public static final String NAME = "Orbital-Strike-Cannon";
    public static final int HEBEL_X = 33;
    public static final int HEBEL_Y = 52;
    public static final int HEBEL_Z = 3;
    /** So viel des Plans muss in der Welt stimmen, damit es als Kanone zaehlt. */
    private static final double MINDESTENS = 0.7;

    /** Liefert die Block-ID (z. B. "minecraft:tnt") an einer Weltstelle. */
    public interface Welt {
        String block(int x, int y, int z);
    }

    private final List<int[]> probeStellen = new ArrayList<>();
    private final List<String> probeIds = new ArrayList<>();
    /** Redstone-Leitungen, vom Hebel aus nach Entfernung sortiert. */
    public final List<int[]> leitung = new ArrayList<>();
    /** Das TNT in den Ladearmen, von aussen nach innen sortiert. */
    public final List<int[]> ladung = new ArrayList<>();
    public final List<int[]> portale = new ArrayList<>();
    /** Die Mitte zwischen den Ladearmen. */
    public final int[] kern;
    /** Ueber der Kanone, senkrecht ueber dem Kern: dort verlaesst die Salve sie. */
    public final int[] muendung;

    private Kanone(Bauplan plan) {
        List<Bauplan.Block> alle = new ArrayList<>();
        int oben = 0;
        for (Bauplan.Block b : plan.bloecke) {
            String id = id(b.zustand());
            if (id.equals("minecraft:lever") || id.equals("minecraft:lodestone") || id.equals("minecraft:air")) {
                continue;
            }
            alle.add(b);
            oben = Math.max(oben, b.y());
            int[] rel = {b.x() - HEBEL_X, b.y() - HEBEL_Y, b.z() - HEBEL_Z};
            switch (id) {
                case "minecraft:redstone_wire", "minecraft:repeater", "minecraft:comparator" -> leitung.add(rel);
                case "minecraft:tnt" -> ladung.add(rel);
                case "minecraft:nether_portal" -> portale.add(rel);
                default -> {
                }
            }
        }
        // Stichprobe: gleichmaessig ueber den ganzen Plan verteilt, etwa 300 Bloecke.
        int schritt = Math.max(1, alle.size() / 300);
        for (int i = 0; i < alle.size(); i += schritt) {
            Bauplan.Block b = alle.get(i);
            probeStellen.add(new int[] {b.x() - HEBEL_X, b.y() - HEBEL_Y, b.z() - HEBEL_Z});
            probeIds.add(id(b.zustand()));
        }
        long sx = 0;
        long sy = 0;
        long sz = 0;
        for (int[] t : ladung) {
            sx += t[0];
            sy += t[1];
            sz += t[2];
        }
        int n = Math.max(1, ladung.size());
        kern = new int[] {(int) Math.round((double) sx / n), (int) Math.round((double) sy / n),
                (int) Math.round((double) sz / n)};
        muendung = new int[] {kern[0], oben + 1 - HEBEL_Y, kern[2]};
        leitung.sort(Comparator.comparingDouble(Kanone::laenge));
        ladung.sort(Comparator.comparingDouble((int[] t) -> abstand(t, kern)).reversed());
    }

    /** Aus dem Plan der Kanone (mit Hebel), oder null ohne Plan. */
    public static Kanone aus(Bauplan plan) {
        return plan == null ? null : new Kanone(plan);
    }

    /**
     * Steht rund um den Hebel bei (hx, hy, hz) wirklich die Kanone?
     *
     * @return die Drehung (0 bis 3, je 90 Grad), oder -1, wenn nicht
     */
    public int erkennen(int hx, int hy, int hz, Welt welt) {
        int beste = -1;
        int besteTreffer = 0;
        for (int drehung = 0; drehung < 4; drehung++) {
            int treffer = 0;
            for (int i = 0; i < probeStellen.size(); i++) {
                int[] w = drehen(probeStellen.get(i), drehung);
                if (gleich(probeIds.get(i), welt.block(hx + w[0], hy + w[1], hz + w[2]))) {
                    treffer++;
                }
            }
            if (treffer > besteTreffer) {
                besteTreffer = treffer;
                beste = drehung;
            }
        }
        return besteTreffer >= probeStellen.size() * MINDESTENS ? beste : -1;
    }

    /** Eine Stelle relativ zum Hebel, gedreht um drehung * 90 Grad. */
    public static int[] drehen(int[] rel, int drehung) {
        return switch (drehung & 3) {
            case 1 -> new int[] {-rel[2], rel[1], rel[0]};
            case 2 -> new int[] {-rel[0], rel[1], -rel[2]};
            case 3 -> new int[] {rel[2], rel[1], -rel[0]};
            default -> rel;
        };
    }

    private static boolean gleich(String plan, String welt) {
        if (plan.equals(welt)) {
            return true;
        }
        // Kolben bewegen sich - ausgefahren, eingefahren, gerade unterwegs.
        return istKolben(plan) && istKolben(welt);
    }

    private static boolean istKolben(String id) {
        return id.equals("minecraft:piston") || id.equals("minecraft:sticky_piston")
                || id.equals("minecraft:piston_head") || id.equals("minecraft:moving_piston")
                || id.equals("minecraft:air");
    }

    private static String id(String zustand) {
        int klammer = zustand.indexOf('[');
        String id = klammer < 0 ? zustand : zustand.substring(0, klammer);
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static double laenge(int[] t) {
        return Math.sqrt((double) t[0] * t[0] + (double) t[1] * t[1] + (double) t[2] * t[2]);
    }

    private static double abstand(int[] a, int[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
