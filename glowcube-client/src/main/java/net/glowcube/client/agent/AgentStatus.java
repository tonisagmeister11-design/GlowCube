package net.glowcube.client.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Was ein Agent gerade tut - fuer die Agenten-Uebersicht im HUD. Der Server
 * (eigene Welt oder Plugin) meldet alle halbe Sekunde den Stand aller Agenten;
 * die Anzeige liest {@link #aktuell()}.
 *
 * @param besitzer wem er gehoert (in der eigenen Welt; vom Plugin kommen nur die eigenen, dann null)
 * @param zustand  kurz: "arbeitet", "zur Kiste", "kommt zurueck" ...
 * @param beute    wie viele Items er dabei hat (Guardian: besiegte Gegner)
 * @param welt     Dimension, z. B. "overworld"
 */
public record AgentStatus(UUID besitzer, String auftrag, int nummer, String titel, String zustand, int beute,
                          double x, double y, double z, String welt) {
    private static volatile List<AgentStatus> stand = List.of();
    private static volatile long zeit;

    public static void setzen(List<AgentStatus> neu) {
        stand = List.copyOf(neu);
        zeit = System.currentTimeMillis();
    }

    /** Der letzte Stand - aelter als fuenf Sekunden gilt als weg. */
    public static List<AgentStatus> aktuell() {
        return System.currentTimeMillis() - zeit > 5000 ? List.of() : stand;
    }

    public static void leeren() {
        stand = List.of();
    }

    private static String sauber(String text) {
        return text.replace(',', ' ').replace('|', ' ').replace(';', ' ');
    }

    /** Fuers Plugin-Protokoll: "status;eintrag|eintrag", Felder mit Komma. */
    public static String alsText(List<AgentStatus> liste) {
        StringBuilder b = new StringBuilder("status;");
        for (int i = 0; i < liste.size(); i++) {
            AgentStatus s = liste.get(i);
            if (i > 0) {
                b.append('|');
            }
            b.append(s.auftrag).append(',').append(s.nummer).append(',').append(sauber(s.titel)).append(',')
                    .append(sauber(s.zustand)).append(',').append(s.beute).append(',')
                    .append(Math.round(s.x)).append(',').append(Math.round(s.y)).append(',').append(Math.round(s.z))
                    .append(',').append(sauber(s.welt));
        }
        return b.toString();
    }

    /** Gegenstueck zu {@link #alsText}: der Teil nach "status;". */
    public static List<AgentStatus> lesen(String text) {
        List<AgentStatus> liste = new ArrayList<>();
        if (text.isEmpty()) {
            return liste;
        }
        for (String eintrag : text.split("\\|")) {
            String[] f = eintrag.split(",", -1);
            if (f.length < 9) {
                continue;
            }
            try {
                liste.add(new AgentStatus(null, f[0], Integer.parseInt(f[1]), f[2], f[3], Integer.parseInt(f[4]),
                        Double.parseDouble(f[5]), Double.parseDouble(f[6]), Double.parseDouble(f[7]), f[8]));
            } catch (NumberFormatException kaputt) {
                // Eintrag ueberspringen
            }
        }
        return liste;
    }
}
