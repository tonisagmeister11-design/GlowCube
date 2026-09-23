package net.glowcube.client.agent;

/**
 * Die Einstellungen eines einzelnen Agenten, wie sie im Menue stehen.
 *
 * @param tempo        Laufgeschwindigkeit (1 = wie ein Spieler, bis 4)
 * @param abbauTempo   Abbau-Tempo (1 = Diamantwerkzeug, bis 20) bzw. beim Builder Bloecke je Sekunde
 * @param xray         X-Ray: sieht Zielbloecke durch Stein, weit ueber den Nahbereich hinaus
 * @param xrayChunks   wie weit X-Ray reicht, in Chunks um ihn herum
 * @param leuchten     der Agent leuchtet (Umriss durch Waende sichtbar)
 * @param zahl         je nach Agent: Farm-Radius, Tunnel-Laenge, Tiere uebrig lassen
 */
public record AgentWerte(double tempo, double abbauTempo, boolean xray, int xrayChunks, boolean leuchten, int zahl) {
    public static final AgentWerte STANDARD = new AgentWerte(1, 1, true, 3, false, 0);

    /** Als Text fuer das Server-Plugin: "tempo;abbau;xray;chunks;leuchten;zahl". */
    public String alsText() {
        return tempo + ";" + abbauTempo + ";" + xray + ";" + xrayChunks + ";" + leuchten + ";" + zahl;
    }
}
