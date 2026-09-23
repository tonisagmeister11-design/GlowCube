package net.glowcube.client.agent;

/**
 * Die Einstellungen eines Agenten, wie sie im Menue stehen.
 *
 * @param tempo        Laufgeschwindigkeit (1 = wie ein Spieler, bis 4)
 * @param abbauTempo   wie viel schneller er abbaut (1 = Diamantwerkzeug, bis 20)
 * @param xray         X-Ray: sieht Zielbloecke durch Stein, weit ueber den Nahbereich hinaus
 * @param xrayChunks   wie weit X-Ray reicht, in Chunks um ihn herum
 */
public record AgentWerte(double tempo, double abbauTempo, boolean xray, int xrayChunks) {
    public static final AgentWerte STANDARD = new AgentWerte(1, 1, true, 3);

    /** Als Text fuer das Server-Plugin: "tempo;abbau;xray;chunks". */
    public String alsText() {
        return tempo + ";" + abbauTempo + ";" + xray + ";" + xrayChunks;
    }
}
